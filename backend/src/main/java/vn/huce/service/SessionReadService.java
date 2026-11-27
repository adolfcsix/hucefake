package vn.huce.service;

import java.sql.Date;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import static vn.huce.validation.Inputs.*;

@Service
public class SessionReadService {
    public static final String STAFF_IDS="(ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL))";
    static final String SESSION_SQL="""
      SELECT x.id,x.section_id,x.staff_id,x.room_id,x.period_from,x.period_count,x.status,x.source_session_id,
        to_char(x.start_at,'YYYY-MM-DD"T"HH24:MI:SS') AS start_at,to_char(x.end_at,'YYYY-MM-DD"T"HH24:MI:SS') AS end_at,
        c.code AS section_code,q.name AS subject_name,t.code AS room_code,sp.code AS staff_code,
        q.managing_unit_id,t.campus_id,c.status AS section_status,e.reason AS exception_reason
      FROM class_sessions x JOIN course_sections c ON c.id=x.section_id JOIN subjects q ON q.id=c.subject_id
      JOIN staff_profiles sp ON sp.id=x.staff_id LEFT JOIN teaching_rooms t ON t.id=x.room_id
      LEFT JOIN session_exceptions e ON e.session_id=x.id WHERE c.semester_id=?
      """;
    private final SqlStore db;

    public SessionReadService(SqlStore db) {
        this.db = db;
    }

    public record Query(String sql,List<Object> args){}
    public Query scheduleQuery(Accounts.Actor a,Map<String,String> q){
        String view=choice(q.getOrDefault("view","personal").isEmpty()?"personal":q.getOrDefault("view","personal"),"personal","all","room","staff","group","section");
        if(!Set.of("STAFF","MANAGER").contains(a.role())&&!Set.of("personal","section").contains(view))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        String from=q.get("from"),to=q.get("to");if(from!=null&&from.isEmpty())from=null;if(to!=null&&to.isEmpty())to=null;
        if(from!=null)from=date(from);if(to!=null)to=date(to);
        if(from!=null&&to!=null&&from.compareTo(to)>0)throw invalid("Khoảng ngày không hợp lệ.");
        Date start=from==null?null:Date.valueOf(from),end=to==null?null:Date.valueOf(to);
        var args=new ArrayList<Object>(Arrays.asList(id(q.get("semesterId")),start,start,end,end));
        String sql=SESSION_SQL+" AND (?::date IS NULL OR x.start_at>=?::date) AND (?::date IS NULL OR x.start_at<?::date+1)";
        if("STUDENT".equals(a.role())){
            sql+=" AND EXISTS(SELECT 1 FROM section_enrollments en WHERE en.section_id=c.id AND en.student_user_id=? AND en.status='ENROLLED')";args.add(a.id());
        }else if("LECTURER".equals(a.role())){sql+=" AND ?=ANY("+STAFF_IDS+")";args.add(a.staffId());}
        if(Set.of("room","staff","group","section").contains(view)){
            args.add(id(q.get("id")));
            sql+=switch(view){
                case "room" -> " AND x.room_id=?";
                case "section" -> " AND x.section_id=?";
                case "staff" -> " AND ?=ANY("+STAFF_IDS+")";
                default -> " AND EXISTS(SELECT 1 FROM section_target_groups g WHERE g.section_id=c.id AND g.group_id=?)";
            };
        }
        return new Query(sql,args);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object schedule(Accounts.Actor actor, Map<String,String> q){
        var query=scheduleQuery(actor,q);return db.paged(q,query.sql(),query.args(),"start_at,section_code,id");
    }
    public Object sections(Accounts.Actor actor, Map<String,String> q){
        var query=scheduleQuery(actor,q);
        return db.rows("SELECT DISTINCT section_id AS id,section_code AS code,subject_name AS name FROM ("+query.sql()+") scoped ORDER BY code,id",query.args().toArray());
    }
    public Object stats(Accounts.Actor actor, String semesterId){
        role(actor,"MANAGER");long sem=id(semesterId);
        var rooms=db.rows("""
          SELECT r.id,r.code,ca.code AS campus_code,count(*) AS sessions,sum(x.period_count) AS periods
          FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
          LEFT JOIN teaching_rooms r ON r.id=x.room_id LEFT JOIN campuses ca ON ca.id=r.campus_id
          WHERE c.semester_id=? AND x.status<>'CANCELLED' GROUP BY r.id,r.code,ca.code ORDER BY sessions DESC,r.code,ca.code
          """,sem);
        var lecturers=db.rows("""
          SELECT s.id,s.code,count(*) AS sessions,sum(x.period_count) AS periods
          FROM class_sessions x JOIN course_sections c ON c.id=x.section_id JOIN staff_profiles s ON s.id=ANY(
          """+STAFF_IDS+") WHERE c.semester_id=? AND x.status<>'CANCELLED' GROUP BY s.id,s.code ORDER BY sessions DESC,s.code",sem);
        return Map.of("rooms",rooms,"lecturers",lecturers);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object notifications(Accounts.Actor actor, Map<String,String> q){
        long user=actor.id();String sql="SELECT * FROM notifications WHERE user_id=?";
        if(!q.containsKey("page")&&!q.containsKey("pageSize"))return db.rows(sql+" ORDER BY id DESC LIMIT 100",user);
        return db.paged(q,sql,List.of(user),"id DESC");
    }

    public int markAll(Accounts.Actor actor) {
        return db.jdbc().update("UPDATE notifications SET read_at=now() WHERE user_id=? AND read_at IS NULL", actor.id());
    }

    public void markRead(Accounts.Actor actor, long id) {
        db.jdbc().update("UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?", id, actor.id());
    }
}
