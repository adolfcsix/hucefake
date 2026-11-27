package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import vn.huce.repository.SectionQueries;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;
import java.util.*;
import java.time.*;
import java.sql.Date;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;

@Service
public class SessionMutationService {
    private final SqlStore db;
    private final SchedulingService scheduling;
    private final ApprovalService approvals;
    SessionMutationService(SqlStore db,SchedulingService scheduling,ApprovalService approvals){this.db=db;this.scheduling=scheduling;this.approvals=approvals;}
    private void forbidden(){throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    private ApiFailure conflict(Object details){return new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",details);}
    private Map<String,Object> section(long id){return db.need(SectionQueries.SECTION_SQL+" WHERE c.id=?",id);}
    private void campus(Accounts.Actor a,Object campus){if(campus!=null)scheduling.requireCampus(a,id(campus));}
    private Map<String,Object> room(Context ctx,long id){
        var room=ctx.rooms().get(id);if(room==null)throw new ApiFailure(404,"NOT_FOUND","Không tìm thấy dữ liệu.");return room;
    }
    private boolean unchanged(Slot slot,Span time,Map<String,Object> original){
        return Objects.equals(nullableId(original.get("room_id")),slot.room())&&time.start().equals(LocalDateTime.parse(s(original,"start_at")));
    }
    private List<Map<String,Object>> alternatives(Accounts.Actor a,Context ctx,Slot slot,Map<String,Object> section,Map<String,Object> original,String action,long exclude){
        var allowed=scheduling.campuses(a);var result=new ArrayList<Map<String,Object>>();
        int first="ROOM_CHANGE".equals(action)?slot.first():1,last="ROOM_CHANGE".equals(action)?slot.first():15;
        for(int p=first;p<=last;p++)for(var r:ctx.rooms().values()){
            long campus=n(r,"campus_id"),roomId=n(r,"id");
            if(!allowed.contains(campus)||!scheduling.fits(r,section)||scheduling.holiday(ctx,campus,slot.from())||("ROOM_CHANGE".equals(action)&&Objects.equals(nullableId(original.get("room_id")),roomId)))continue;
            var proposed=slot.move(roomId,slot.day(),p);var when=scheduling.interval(ctx,proposed,slot.from());
            if(when==null||scheduling.busy(ctx,proposed,"BUSY")||unchanged(proposed,when,original))continue;
            if(scheduling.actualConflicts(ctx,proposed,when,exclude).isEmpty())result.add(Map.of("roomId",roomId,"roomCode",r.get("code"),"dayOfWeek",slot.day(),"periodFrom",p));
            if(result.size()==5)return result;
        }
        return result;
    }
    public Object change(Accounts.Actor a,long id,Map<String,Object> f,Long requestId){
        role(a,"STAFF");
        var original=db.need("SELECT x.*,r.campus_id FROM class_sessions x LEFT JOIN teaching_rooms r ON r.id=x.room_id WHERE x.id=?",id);
        var section=section(n(original,"section_id"));unit(a,section.get("managing_unit_id"));
        if(db.exists("SELECT 1 FROM session_exceptions WHERE session_id=?",id))throw new ApiFailure(409,"DUPLICATE_REQUEST","Buổi học đã có ngoại lệ.");
        if(!"PLANNED".equals(original.get("status"))||!"OPEN".equals(section.get("status")))throw state("Chỉ điều chỉnh buổi học đang lên lịch của lớp đã mở.");
        campus(a,original.get("campus_id"));
        var ctx=scheduling.context();
        String action=choice(f.get("action"),"CANCEL","RESCHEDULE","ROOM_CHANGE"),reason=text(f.get("reason"),500);
        var originalDate=LocalDateTime.parse(s(original,"start_at")).toLocalDate();Long replacement=null;
        if(!"CANCEL".equals(action)){
            var room=room(ctx,id(f.get("roomId")));long roomId=n(room,"id"),campus=n(room,"campus_id");scheduling.requireCampus(a,campus);
            boolean changeRoom="ROOM_CHANGE".equals(action);
            var day=changeRoom?originalDate:LocalDate.parse(date(f.get("date")));
            var sem=db.need("SELECT start_date,end_date FROM semesters WHERE id=?",n(section,"semester_id"));
            if(day.isBefore(SchedulingService.day(sem.get("start_date")))||day.isAfter(SchedulingService.day(sem.get("end_date"))))throw invalid("Ngày học bù phải nằm trong học kỳ.");
            if(scheduling.holiday(ctx,campus,day))throw invalid("Ngày đã chọn là ngày không học tại cơ sở.");
            int period=changeRoom?(int)n(original,"period_from"):(int)number(f.get("periodFrom"),1,15);
            if(!scheduling.fits(room,section))throw invalid("Phòng thay thế phải đủ chỗ và phù hợp học phần.");
            if(changeRoom&&Objects.equals(nullableId(original.get("room_id")),roomId))throw invalid("Cần chọn phòng khác.");
            var slot=new Slot(0,n(section,"id"),nullableId(original.get("staff_id")),roomId,day.getDayOfWeek().getValue(),period,(int)n(original,"period_count"),day,day);
            var time=scheduling.interval(ctx,slot,day);
            if(time==null||scheduling.busy(ctx,slot,"BUSY"))throw invalid("Tiết thay thế qua ca hoặc rơi vào giờ bận.");
            if(unchanged(slot,time,original))throw invalid("Giờ hoặc phòng thay thế phải khác buổi ban đầu.");
            var issues=scheduling.actualConflicts(ctx,slot,time,id);
            if(!issues.isEmpty())throw conflict(Map.of("issues",issues,"suggestions",alternatives(a,ctx,slot,section,original,action,id)));
            replacement=n(db.need("""
              INSERT INTO class_sessions(section_id,staff_id,room_id,period_from,period_count,start_at,end_at,timetable_version_id,source_session_id)
              VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
              """,slot.section(),slot.staff(),slot.room(),slot.first(),slot.count(),Timestamp.valueOf(time.start()),Timestamp.valueOf(time.end()),original.get("timetable_version_id"),id),"id");
        }
        var result=db.need("INSERT INTO session_exceptions(session_id,occurrence_date,action,replacement_id,reason,created_by) VALUES (?,?,?,?,?,?) RETURNING id",id,Date.valueOf(originalDate),action,replacement,reason,a.id());
        db.jdbc().update("UPDATE class_sessions SET status='CANCELLED',version=version+1 WHERE id=?",id);
        db.audit(a,action,"session_exceptions",n(result,"id"));
        if(requestId==null){
            var closed=db.rows("""
              UPDATE session_change_requests SET status=CASE WHEN action=? THEN 'APPROVED' ELSE 'RETURNED' END,
              decision_reason=CASE WHEN action=? THEN 'Đề nghị đã được xử lý bằng điều chỉnh trực tiếp. Xem lại thời khóa biểu.'
                ELSE 'Buổi học có điều chỉnh trực tiếp khác với đề nghị. Xem lại thời khóa biểu.' END
              WHERE session_id=? AND status='PENDING' RETURNING id,staff_id,status,decision_reason
              """,action,action,id);
            for(var request:closed){
                db.audit(a,s(request,"status"),"session_change_requests",n(request,"id"));
                db.jdbc().update("INSERT INTO notifications(user_id,message) SELECT user_id,? FROM staff_profiles WHERE id=?",request.get("decision_reason"),request.get("staff_id"));
            }
        }
        approvals.notifySection(n(section,"id"),"Lớp "+section.get("code")+" thay đổi lịch ngày "+originalDate+". "+reason);
        var out=new LinkedHashMap<String,Object>();out.put("id",result.get("id"));out.put("replacementId",replacement);return out;
    }
    public List<Map<String,Object>> availability(Accounts.Actor a,long sem){
        role(a,"LECTURER");return db.rows("SELECT * FROM staff_availability WHERE staff_id=? AND semester_id=? ORDER BY day_of_week,period_from,id",a.staffId(),sem);
    }
    public void setAvailability(Accounts.Actor a,Map<String,Object> f){
        role(a,"LECTURER");if(a.staffId()==null)forbidden();
        long sem=id(f.get("semesterId"));db.need("SELECT id FROM semesters WHERE id=?",sem);
        if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",sem))throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");
        if(!(f.get("entries") instanceof List<?> entries)||entries.size()>100)throw invalid("Tối đa 100 khoảng thời gian.");
        db.jdbc().update("DELETE FROM staff_availability WHERE staff_id=? AND semester_id=?",a.staffId(),sem);
        for(Object raw:entries){
            if(!(raw instanceof Map<?,?> e))throw invalid("Khoảng thời gian không hợp lệ.");
            long first=number(e.get("periodFrom"),1,15);Object reason=e.get("reason");
            db.jdbc().update("INSERT INTO staff_availability(staff_id,semester_id,day_of_week,period_from,period_to,kind,reason) VALUES (?,?,?,?,?,?,?)",
                a.staffId(),sem,number(e.get("dayOfWeek"),1,7),first,number(e.get("periodTo"),first,15),choice(e.get("kind"),"BUSY","AVOID"),reason==null||"".equals(reason)?null:text(reason,500));
        }
        var ctx=scheduling.context();
        for(var slot:ctx.patterns())if(scheduling.staff(ctx,slot).contains(a.staffId())&&n(ctx.sections().get(slot.section()),"semester_id")==sem&&scheduling.busy(ctx,slot,"BUSY"))
            throw conflict(Map.of("issues",List.of(scheduling.issue("BR08","Đã có lịch giảng trong khoảng giờ bận.",ctx.sections().get(slot.section())))));
        var actual=db.rows("""
          SELECT DISTINCT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
          FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
          JOIN staff_availability a ON a.staff_id=? AND a.semester_id=c.semester_id AND a.kind='BUSY'
            AND a.day_of_week=EXTRACT(ISODOW FROM x.start_at) AND a.period_from<=x.period_from+x.period_count-1 AND a.period_to>=x.period_from
          WHERE ?=ANY(
          """+SessionReadService.STAFF_IDS+") AND c.semester_id=? AND x.status='PLANNED' AND x.start_at>=CURRENT_DATE",a.staffId(),a.staffId(),sem);
        if(!actual.isEmpty())throw conflict(Map.of("issues",actual));db.audit(a,"SET_AVAILABILITY","semesters",sem);
    }
    public Object requests(Accounts.Actor a,Map<String,String> q){
        role(a,"STAFF","LECTURER");
        String sql="""
          SELECT r.*,c.code AS section_code,s.name AS subject_name,
          to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS session_time,to_char(x.end_at,'YYYY-MM-DD HH24:MI') AS end_at,
          x.period_from,x.room_id,room.code AS room_code
          FROM session_change_requests r JOIN class_sessions x ON x.id=r.session_id JOIN course_sections c ON c.id=x.section_id
          JOIN subjects s ON s.id=c.subject_id LEFT JOIN teaching_rooms room ON room.id=x.room_id WHERE c.semester_id=? AND
          """;
        var args=new ArrayList<Object>();args.add(id(q.get("semesterId")));
        if("STAFF".equals(a.role())){
            sql+=" s.managing_unit_id=? AND (room.campus_id IS NULL OR EXISTS(SELECT 1 FROM staff_campuses sc JOIN campuses ca ON ca.id=sc.campus_id WHERE sc.campus_id=room.campus_id AND sc.staff_id=? AND ca.status='ACTIVE'))";
            args.add(a.unitId());args.add(a.staffId());
        }else{sql+=" r.staff_id=?";args.add(a.staffId());}
        return db.paged(q,sql,args,"id DESC");
    }
    public Object request(Accounts.Actor a,long id,Map<String,Object> f){
        role(a,"LECTURER");
        var session=db.need("SELECT x.*,"+SessionReadService.STAFF_IDS+" AS staff_ids FROM class_sessions x WHERE x.id=?",id);
        if(a.staffId()==null||!ids(session.get("staff_ids")).contains(a.staffId()))forbidden();
        var section=section(n(session,"section_id"));
        if(!"PLANNED".equals(session.get("status"))||!"OPEN".equals(section.get("status")))throw state("Buổi học không còn ở trạng thái đã lên lịch.");
        if(db.exists("SELECT 1 FROM session_change_requests WHERE session_id=? AND status='PENDING'",id))throw new ApiFailure(409,"DUPLICATE_REQUEST","Buổi học đã có đề nghị chờ xử lý.");
        var result=db.need("INSERT INTO session_change_requests(session_id,staff_id,action,reason) VALUES (?,?,?,?) RETURNING id",id,a.staffId(),choice(f.get("action"),"CANCEL","RESCHEDULE"),text(f.get("reason"),500));
        db.audit(a,"REQUEST_CHANGE","class_sessions",id);
        db.jdbc().update("""
          INSERT INTO notifications(user_id,message)
          SELECT p.user_id,? FROM staff_profiles p JOIN users u ON u.id=p.user_id JOIN roles r ON r.role_id=u.role_id
          WHERE p.unit_id=? AND p.status='ACTIVE' AND u.status='ACTIVE' AND r.code='STAFF' AND (?::bigint IS NULL OR EXISTS(
            SELECT 1 FROM teaching_rooms t JOIN staff_campuses sc ON sc.campus_id=t.campus_id
            JOIN campuses c ON c.id=t.campus_id WHERE t.id=? AND sc.staff_id=p.id AND c.status='ACTIVE'))
          ""","Có đề nghị đổi lịch cho lớp "+section.get("code")+". Mở Đề nghị đổi lịch để xử lý.",section.get("managing_unit_id"),session.get("room_id"),session.get("room_id"));
        return result;
    }
    public void decide(Accounts.Actor a,long id,Map<String,Object> f){
        role(a,"STAFF");
        var record=db.need("""
          SELECT r.*,s.managing_unit_id,room.campus_id FROM session_change_requests r JOIN class_sessions x ON x.id=r.session_id
          JOIN course_sections c ON c.id=x.section_id JOIN subjects s ON s.id=c.subject_id LEFT JOIN teaching_rooms room ON room.id=x.room_id WHERE r.id=?
          """,id);
        unit(a,record.get("managing_unit_id"));campus(a,record.get("campus_id"));
        if(!"PENDING".equals(record.get("status")))throw state("Đề nghị đã được xử lý.");
        String reason=text(f.get("reason"),500),action=choice(f.get("action"),"RETURNED","CANCEL","RESCHEDULE");
        String status="RETURNED".equals(action)?"RETURNED":"APPROVED";
        if("APPROVED".equals(status)){
            if(!action.equals(record.get("action")))throw invalid("Loại điều chỉnh phải khớp với đề nghị.");
            change(a,n(record,"session_id"),f,id);
        }
        db.jdbc().update("UPDATE session_change_requests SET status=?,decision_reason=? WHERE id=?",status,reason,id);
        db.jdbc().update("INSERT INTO notifications(user_id,message) SELECT user_id,? FROM staff_profiles WHERE id=?",("APPROVED".equals(status)?"Đề nghị đổi lịch đã được xử lý. ":"Đề nghị đổi lịch đã được trả lại. ")+reason,record.get("staff_id"));
        db.audit(a,status,"session_change_requests",id);
    }
}
