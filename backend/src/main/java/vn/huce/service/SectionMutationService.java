package vn.huce.service;

import java.sql.Date;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import static vn.huce.repository.SectionQueries.*;
import static vn.huce.validation.Inputs.*;

@Service
public class SectionMutationService {
    private final SqlStore db;
    private final SectionService sections;
    private final SchedulingService scheduling;

    public SectionMutationService(SqlStore db, SectionService sections, SchedulingService scheduling) {
        this.db = db;
        this.sections = sections;
        this.scheduling = scheduling;
    }

    public Object importSections(Accounts.Actor a, Map<String,Object> f) {
        role(a, "STAFF");
        var sem=db.need("SELECT * FROM semesters WHERE id=?",id(f.get("semesterId")));sections.unlocked(n(sem,"id"));
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
                sections.create(a,sections.values(sem,subject,row));
            }catch(ApiFailure e){throw new ApiFailure(e.status(),e.code(),"Dòng dữ liệu "+index+" · "+e.getMessage(),e.details());}
        }
        return Map.of("imported",rows.size());
    }

    public Object update(Accounts.Actor a, long id, Map<String,Object> f) {
        role(a, "STAFF");
        var row=sections.editable(a,id);version(f.get("version"),row);var v=sections.form(a,f);
        if(n(row,"semester_id")!=v.sem())throw invalid("Không thể chuyển học kỳ của lớp.");
        if(n(row,"scheduled")>v.count()||db.exists("""
          SELECT 1 FROM section_schedule_patterns p JOIN teaching_rooms r ON r.id=p.room_id
          WHERE p.section_id=? AND (p.period_count<>? OR p.effective_from<? OR p.effective_to>? OR r.capacity<? OR r.type<>?)
          """,id,v.length(),Date.valueOf(v.from()),Date.valueOf(v.to()),v.capacity(),v.type()))throw state("Hãy gỡ lịch định kỳ bị ảnh hưởng trước khi sửa lớp.");
        db.jdbc().update("""
          UPDATE course_sections SET subject_id=?,code=?,capacity=?,sessions_per_week=?,periods_per_session=?,required_room_type=?,start_date=?,end_date=?,version=version+1,updated_at=now() WHERE id=?
          """,v.subject(),v.code(),v.capacity(),v.count(),v.length(),v.type(),Date.valueOf(v.from()),Date.valueOf(v.to()),id);
        db.audit(a,"UPDATE","course_sections",id);return Map.of("id",id);
    }

    public Object cancel(Accounts.Actor a, long id, String version) {
        role(a, "STAFF");
        var row=sections.editable(a,id);version(version,row);
        db.jdbc().update("DELETE FROM section_schedule_patterns WHERE section_id=?",id);
        db.jdbc().update("UPDATE course_sections SET status='CANCELLED',version=version+1,updated_at=now() WHERE id=?",id);
        db.audit(a,"CANCEL","course_sections",id);return null;
    }

    public Object assign(Accounts.Actor a, long id, Map<String,Object> f) {
        role(a, "STAFF");
        var section=sections.editable(a,id);if(f.get("version")!=null)version(f.get("version"),section);
        long main=id(f.get("staffId"));var assistants=idSet(f.get("assistantIds"));
        if(assistants.contains(main))throw invalid("Giảng viên chính không thể đồng thời là trợ giảng.");
        var desired=new LinkedHashMap<Long,String>();desired.put(main,"MAIN");for(long assistant:assistants)desired.put(assistant,"ASSISTANT");
        for(long staff:desired.keySet())if(!db.exists("""
          SELECT 1 FROM staff_profiles p JOIN users u ON u.id=p.user_id JOIN roles r ON r.role_id=u.role_id
          WHERE p.id=? AND p.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER'
          """,staff))throw invalid("Tài khoản giảng viên phải đang hoạt động.");
        for(var row:db.rows("SELECT * FROM section_assignments WHERE section_id=? AND valid_to IS NULL",id)){
            long staff=n(row,"staff_id");
            if(Objects.equals(desired.get(staff),row.get("role")))desired.remove(staff);
            else db.jdbc().update("UPDATE section_assignments SET valid_to=GREATEST(valid_from,CURRENT_DATE) WHERE id=?",n(row,"id"));
        }
        for(var entry:desired.entrySet())db.jdbc().update("INSERT INTO section_assignments(section_id,staff_id,role,valid_from,assigned_by) VALUES (?,?,?,?,?)",id,entry.getKey(),entry.getValue(),Date.valueOf(s(section,"start_date")),a.id());
        if(!Objects.equals(SchedulingService.nullableId(section.get("staff_id")),main))db.jdbc().update("UPDATE section_schedule_patterns SET staff_id=?,version=version+1 WHERE section_id=?",main,id);
        scheduling.recheck(id,a);
        db.jdbc().update("UPDATE course_sections SET version=version+1,updated_at=now() WHERE id=?",id);db.audit(a,"ASSIGN","course_sections",id);return null;
    }

    public Object groups(Accounts.Actor a, long id, Map<String,Object> f) {
        role(a, "STAFF");
        var section=sections.editable(a,id);if(f.get("version")!=null)version(f.get("version"),section);
        var ids=idSet(f.get("groupIds"));
        db.jdbc().update("DELETE FROM section_target_groups WHERE section_id=?",id);
        for(long group:ids)db.jdbc().update("INSERT INTO section_target_groups(section_id,group_id) VALUES (?,?)",id,group);
        scheduling.recheck(id,a);db.jdbc().update("UPDATE course_sections SET version=version+1,updated_at=now() WHERE id=?",id);db.audit(a,"SET_TARGET_GROUPS","course_sections",id);return null;
    }

    public Object deletePattern(Accounts.Actor a, long id, String version) {
        role(a, "STAFF");
        var pattern=db.need("SELECT p.*,r.campus_id FROM section_schedule_patterns p JOIN teaching_rooms r ON r.id=p.room_id WHERE p.id=?",id);
        sections.editable(a,n(pattern,"section_id"));scheduling.requireCampus(a,n(pattern,"campus_id"));version(version,pattern);
        db.jdbc().update("DELETE FROM section_schedule_patterns WHERE id=?",id);db.audit(a,"DELETE","section_schedule_patterns",id);return null;
    }

    private Set<Long> idSet(Object value){
        var out=new LinkedHashSet<Long>();if(value==null)return out;
        if(!(value instanceof List<?> list)||list.size()>200)throw invalid("Danh sách lựa chọn không hợp lệ.");
        for(Object item:list)out.add(id(item));return out;
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object conflicts(Accounts.Actor a, Map<String,Object> f) {
        role(a,"STAFF","MANAGER");
        var section=db.need(SECTION_SQL+" WHERE c.id=?",id(f.get("sectionId")));unit(a,section.get("managing_unit_id"));
        if(!"DRAFT".equals(section.get("status")))throw state("Chỉ kiểm tra phương án cho lớp nháp.");
        var ctx=scheduling.context();var slot=scheduling.candidate(a,ctx,f,f.get("patternId")==null?null:id(f.get("patternId")),section);
        return scheduling.check(ctx,slot,a,true);
    }

    public Object savePattern(Accounts.Actor a,Map<String,Object> f,Long prior){
        role(a, "STAFF");
        var section=sections.editable(a,id(f.get("sectionId")));var ctx=scheduling.context();var slot=scheduling.candidate(a,ctx,f,prior,section);
        if(prior!=null)version(f.get("version"),db.need("SELECT * FROM section_schedule_patterns WHERE id=?",prior));
        if(prior==null&&n(section,"scheduled")>=n(section,"sessions_per_week"))throw state("Lớp đã xếp đủ số buổi mỗi tuần.");
        var result=scheduling.check(ctx,slot,a,true);
        if(!result.valid())throw new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",result);
        var row=prior==null?db.need("""
          INSERT INTO section_schedule_patterns(section_id,staff_id,room_id,day_of_week,period_from,period_count,effective_from,effective_to,source,created_by)
          VALUES (?,?,?,?,?,?,?,?,'MANUAL',?) RETURNING id
          """,slot.section(),slot.staff(),slot.room(),slot.day(),slot.first(),slot.count(),Date.valueOf(slot.from()),Date.valueOf(slot.to()),a.id()):
          db.need("""
          UPDATE section_schedule_patterns SET room_id=?,day_of_week=?,period_from=?,effective_from=?,effective_to=?,source='MANUAL',run_id=NULL,version=version+1 WHERE id=? RETURNING id
          """,slot.room(),slot.day(),slot.first(),Date.valueOf(slot.from()),Date.valueOf(slot.to()),prior);
        db.audit(a,"SAVE","section_schedule_patterns",n(row,"id"));return row;
    }

}
