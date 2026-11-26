package vn.huce;

import static vn.huce.Inputs.*;
import java.util.*;
import java.sql.Date;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class SectionController {
    static final String SECTION_SQL="""
      SELECT c.*,s.managing_unit_id,s.name AS subject_name,s.code AS subject_code,a.staff_id,sp.code AS staff_code,
        (SELECT count(*) FROM section_schedule_patterns p WHERE p.section_id=c.id) AS scheduled,
        ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=c.id ORDER BY g.group_id) AS group_ids,
        ARRAY(SELECT ag.code FROM section_target_groups g JOIN academic_groups ag ON ag.id=g.group_id WHERE g.section_id=c.id ORDER BY g.group_id) AS group_codes,
        ARRAY(SELECT aa.staff_id FROM section_assignments aa WHERE aa.section_id=c.id AND aa.role='ASSISTANT' AND aa.valid_to IS NULL ORDER BY aa.staff_id) AS assistant_ids
      FROM course_sections c JOIN subjects s ON s.id=c.subject_id
      LEFT JOIN section_assignments a ON a.section_id=c.id AND a.role='MAIN' AND a.valid_to IS NULL
      LEFT JOIN staff_profiles sp ON sp.id=a.staff_id
      """;
    static final String PATTERN_SQL="""
      SELECT p.*,c.code AS section_code,s.name AS subject_name,t.code AS room_code,sp.code AS staff_code,
        c.status AS section_status,c.subject_id,s.managing_unit_id,
        ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=c.id ORDER BY g.group_id) AS group_ids
      FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
      JOIN subjects s ON s.id=c.subject_id JOIN teaching_rooms t ON t.id=p.room_id JOIN staff_profiles sp ON sp.id=p.staff_id
      """;
    private final SqlStore db;
    private final MutationService writes;
    SectionController(SqlStore db,MutationService writes){this.db=db;this.writes=writes;}
    private Accounts.Actor actor(Authentication a){return (Accounts.Actor)a.getPrincipal();}
    @GetMapping("/course-sections") @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object sections(Authentication auth,@RequestParam Map<String,String> q){
        var a=actor(auth);role(a,"STAFF","MANAGER");
        String status=q.getOrDefault("status",""),subject=q.getOrDefault("subjectId","");
        Long unit="STAFF".equals(a.role())?(a.unitId()==null?-1L:a.unitId()):null;
        String search="%"+q.getOrDefault("search","")+"%";
        return db.paged(q,SECTION_SQL+"""
          WHERE c.semester_id=? AND (?::bigint IS NULL OR s.managing_unit_id=?)
          AND (?::text IS NULL OR c.status=?) AND (?::bigint IS NULL OR c.subject_id=?)
          AND (c.code ILIKE ? OR s.name ILIKE ?)
          """,Arrays.asList(id(q.get("semesterId")),unit,unit,status.isEmpty()?null:status,status.isEmpty()?null:status,subject.isEmpty()?null:id(subject),subject.isEmpty()?null:id(subject),search,search),"code,id");
    }
    @GetMapping("/schedule-patterns") @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object patterns(Authentication auth,@RequestParam Map<String,String> q){
        role(actor(auth),"STAFF","MANAGER");
        return db.paged(q,PATTERN_SQL+" WHERE c.semester_id=?",List.of(id(q.get("semesterId"))),"day_of_week,period_from,id");
    }
    void unlocked(long semester){if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",semester))throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");}
    Map<String,Object> editable(Accounts.Actor a,long id){
        var row=db.need(SECTION_SQL+" WHERE c.id=?",id);
        unit(a,row.get("managing_unit_id"));
        if(!"DRAFT".equals(row.get("status")))throw state("Chỉ chỉnh lớp nháp.");
        unlocked(n(row,"semester_id"));return row;
    }
    private record Form(long sem,long subject,String code,long capacity,long count,long length,String type,String from,String to){}
    private Form values(Map<String,Object> sem,Map<String,Object> subject,Map<String,Object> f){
        if(!"ACTIVE".equals(sem.get("status"))||!"ACTIVE".equals(subject.get("status")))throw invalid("Học kỳ và học phần phải đang hoạt động.");
        String from=date(f.get("startDate")),to=date(f.get("endDate"));
        if(from.compareTo(to)>0||from.compareTo(s(sem,"start_date"))<0||to.compareTo(s(sem,"end_date"))>0)throw invalid("Khoảng ngày phải nằm trong học kỳ.");
        return new Form(n(sem,"id"),n(subject,"id"),text(f.get("code"),40).toUpperCase(Locale.ROOT),number(f.get("capacity"),1,10000),number(f.get("sessionsPerWeek"),1,6),number(f.get("periodsPerSession"),1,6),choice(f.get("requiredRoomType"),"LECTURE","COMPUTER_LAB","LAB","STUDIO"),from,to);
    }
    private Form form(Accounts.Actor a,Map<String,Object> f){
        var sem=db.need("SELECT * FROM semesters WHERE id=?",id(f.get("semesterId")));
        var subject=db.need("SELECT * FROM subjects WHERE id=?",id(f.get("subjectId")));
        unit(a,subject.get("managing_unit_id"));return values(sem,subject,f);
    }
    private Object create(Accounts.Actor a,Form f){
        unlocked(f.sem);
        if(db.exists("SELECT 1 FROM course_sections WHERE semester_id=? AND code=?",f.sem,f.code))throw new ApiFailure(409,"DUPLICATE_REQUEST","Mã lớp đã có trong học kỳ này.",Map.of("field","code"));
        var row=db.need("""
          INSERT INTO course_sections(semester_id,subject_id,code,capacity,sessions_per_week,periods_per_session,required_room_type,start_date,end_date,created_by)
          VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
          """,f.sem,f.subject,f.code,f.capacity,f.count,f.length,f.type,Date.valueOf(f.from),Date.valueOf(f.to),a.id());
        db.audit(a,"CREATE","course_sections",n(row,"id"));return row;
    }
    @PostMapping("/course-sections") ResponseEntity<?> create(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        return ResponseEntity.status(201).body(writes.write(a,req,f,()->create(a,form(a,f))));
    }
    @PostMapping("/course-sections/import") ResponseEntity<?> imports(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        return ResponseEntity.status(201).body(writes.write(a,req,f,()->{
            var sem=db.need("SELECT * FROM semesters WHERE id=?",id(f.get("semesterId")));unlocked(n(sem,"id"));
            if(!(f.get("rows") instanceof List<?> rows)||rows.isEmpty()||rows.size()>200)throw invalid("Mỗi tệp cần có từ 1 đến 200 lớp.");
            int index=0;
            for(Object item:rows){
                index++;
                try{
                    if(!(item instanceof Map<?,?> raw))throw invalid("Dòng dữ liệu không hợp lệ.");
                    @SuppressWarnings("unchecked") var row=(Map<String,Object>)raw;
                    if(!Objects.equals(row.get("semesterCode"),sem.get("code")))throw invalid("Mã học kỳ trong tệp khác học kỳ đang chọn.");
                    String code=text(row.get("subjectCode"),40);
                    var subject=db.one("SELECT * FROM subjects WHERE managing_unit_id=? AND status='ACTIVE' AND (code=? OR code=?) ORDER BY CASE WHEN code=? THEN 0 ELSE 1 END LIMIT 1",a.unitId(),code,code.toUpperCase(Locale.ROOT),code);
                    if(subject==null)throw invalid("Mã học phần chưa có hoặc nằm ngoài phạm vi khoa.");
                    create(a,values(sem,subject,row));
                }catch(ApiFailure e){throw new ApiFailure(e.status,e.code,"Dòng dữ liệu "+index+" · "+e.getMessage(),e.details);}
            }
            return Map.of("imported",rows.size());
        }));
    }
    @PatchMapping("/course-sections/{id}") Object update(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        return writes.write(a,req,f,()->{
            var row=editable(a,id);version(f.get("version"),row);var v=form(a,f);
            if(n(row,"semester_id")!=v.sem)throw invalid("Không thể chuyển học kỳ của lớp.");
            if(n(row,"scheduled")>v.count||db.exists("""
              SELECT 1 FROM section_schedule_patterns p JOIN teaching_rooms r ON r.id=p.room_id
              WHERE p.section_id=? AND (p.period_count<>? OR p.effective_from<? OR p.effective_to>? OR r.capacity<? OR r.type<>?)
              """,id,v.length,Date.valueOf(v.from),Date.valueOf(v.to),v.capacity,v.type))throw state("Hãy gỡ lịch định kỳ bị ảnh hưởng trước khi sửa lớp.");
            db.db.update("""
              UPDATE course_sections SET subject_id=?,code=?,capacity=?,sessions_per_week=?,periods_per_session=?,required_room_type=?,start_date=?,end_date=?,version=version+1,updated_at=now() WHERE id=?
              """,v.subject,v.code,v.capacity,v.count,v.length,v.type,Date.valueOf(v.from),Date.valueOf(v.to),id);
            db.audit(a,"UPDATE","course_sections",id);return Map.of("id",id);
        });
    }
    @PostMapping("/course-sections/{id}/cancel") ResponseEntity<?> cancel(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestParam(required=false) String version){
        var a=actor(auth);role(a,"STAFF");
        writes.write(a,req,Map.of(),()->{
            var row=editable(a,id);version(version,row);
            db.db.update("DELETE FROM section_schedule_patterns WHERE section_id=?",id);
            db.db.update("UPDATE course_sections SET status='CANCELLED',version=version+1,updated_at=now() WHERE id=?",id);
            db.audit(a,"CANCEL","course_sections",id);return null;
        });return ResponseEntity.noContent().build();
    }
}
