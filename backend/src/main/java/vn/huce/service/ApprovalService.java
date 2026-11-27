package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;
import java.util.*;
import java.sql.Timestamp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {
    private final SqlStore db;
    private final SchedulingService scheduling;
    private final ObjectMapper json;
    ApprovalService(SqlStore db,SchedulingService scheduling,ObjectMapper json){this.db=db;this.scheduling=scheduling;this.json=json;}
    private String encode(Object x){try{return json.writeValueAsString(x);}catch(Exception e){throw new IllegalStateException(e);}}
    private Object decode(Object x){try{return json.readValue(x.toString(),Object.class);}catch(Exception e){throw new IllegalStateException(e);}}
    private void status(Map<String,Object> row,String... allowed){if(!Arrays.asList(allowed).contains(row.get("status")))throw state("Hồ sơ đang ở trạng thái "+row.get("status")+".");}
    private void forbidden(){throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");}
    public Map<String,Object> approval(long id){
        var row=db.need("""
          SELECT a.*,a.payload::text AS payload_json FROM approval_requests a JOIN timetable_versions t ON t.approval_request_id=a.id WHERE a.id=?
          """,id);
        row.put("payload",decode(row.remove("payload_json")));return row;
    }
    record Assessment(Map<String,Object> readiness,List<Map<String,Object>> snapshot){}
    Assessment assess(Accounts.Actor actor,long semesterId){
        var ctx=scheduling.context();
        var drafts=ctx.sections().values().stream().filter(s->n(s,"semester_id")==semesterId&&"DRAFT".equals(s.get("status"))).toList();
        boolean eligible=drafts.stream().anyMatch(s->Objects.equals(actor.unitId(),nullableId(s.get("managing_unit_id"))));
        var blocked=new ArrayList<Map<String,Object>>();
        var snapshot=new ArrayList<Map<String,Object>>();
        var readiness=new LinkedHashMap<String,Object>();
        readiness.put("semesterId",semesterId);readiness.put("draftCount",drafts.size());readiness.put("blockedSections",blocked);readiness.put("eligible",eligible);readiness.put("ready",false);
        if(drafts.isEmpty()||!eligible)return new Assessment(readiness,snapshot);
        for(var section:drafts){
            var slots=ctx.patterns().stream().filter(p->p.section()==n(section,"id")).toList();
            var missing=new ArrayList<Map<String,Object>>();
            var conflicts=new ArrayList<Map<String,Object>>();
            if(section.get("staff_id")==null)missing.add(Map.of("rule","BR02","message","Chưa có giảng viên chính."));
            if(slots.size()!=n(section,"sessions_per_week"))missing.add(Map.of("rule","BR10","message","Số buổi đã xếp khác số buổi yêu cầu."));
            for(var slot:slots){
                var result=scheduling.check(ctx,slot,actor,false);var room=ctx.rooms().get(slot.room());
                if(!result.valid()){
                    var conflict=new LinkedHashMap<String,Object>();conflict.put("id",slot.id());conflict.put("dayOfWeek",slot.day());conflict.put("periodFrom",slot.first());conflict.put("roomCode",room==null?null:room.get("code"));conflict.put("issues",result.issues());conflicts.add(conflict);
                }else{
                    var snap=new LinkedHashMap<String,Object>();
                    snap.put("id",slot.id());snap.put("section",slot.section());snap.put("staff",slot.staff());snap.put("room",slot.room());
                    snap.put("day",slot.day());snap.put("first",slot.first());snap.put("count",slot.count());snap.put("from",slot.from().toString());snap.put("to",slot.to().toString());
                    snap.put("version",db.need("SELECT version FROM section_schedule_patterns WHERE id=?",slot.id()).get("version"));
                    snap.put("sectionCode",section.get("code"));snap.put("subjectName",section.get("subject_name"));snap.put("roomCode",room.get("code"));snapshot.add(snap);
                }
            }
            if(!missing.isEmpty()||!conflicts.isEmpty()){
                var b=new LinkedHashMap<String,Object>();b.put("id",section.get("id"));b.put("code",section.get("code"));b.put("subjectName",section.get("subject_name"));
                b.put("editable",Objects.equals(actor.unitId(),nullableId(section.get("managing_unit_id"))));b.put("scheduled",slots.size());b.put("required",section.get("sessions_per_week"));b.put("missing",missing);b.put("conflicts",conflicts);blocked.add(b);
            }
        }
        readiness.put("ready",blocked.isEmpty());return new Assessment(readiness,snapshot);
    }
    public Map<String,Object> readiness(Accounts.Actor a,long sem){
        db.need("SELECT id FROM semesters WHERE id=?",sem);
        if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",sem))return Map.of("semesterId",sem,"pending",true,"ready",false,"blockedSections",List.of());
        return assess(a,sem).readiness();
    }
    public Object create(Accounts.Actor a,long sem){
        db.need("SELECT id FROM semesters WHERE id=?",sem);
        if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",sem))throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");
        var existing=db.one("SELECT id FROM timetable_versions WHERE semester_id=? AND created_by=? AND status='DRAFT' ORDER BY id DESC LIMIT 1",sem,a.id());
        return existing!=null?existing:db.need("INSERT INTO timetable_versions(semester_id,version_no,created_by) SELECT ?,COALESCE(max(version_no),0)+1,? FROM timetable_versions WHERE semester_id=? RETURNING id",sem,a.id(),sem);
    }
    public Object submit(Accounts.Actor a,long id){
        var v=db.need("SELECT * FROM timetable_versions WHERE id=?",id);
        if(n(v,"created_by")!=a.id())forbidden();status(v,"DRAFT");
        long sem=n(v,"semester_id");
        if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",sem))throw new ApiFailure(409,"DUPLICATE_REQUEST","Học kỳ đã có thời khóa biểu chờ duyệt.");
        var assessment=assess(a,sem);var ready=assessment.readiness();
        if(n(ready,"draftCount")==0)throw state("Học kỳ chưa có lớp nháp để gửi duyệt.");
        if(!Boolean.TRUE.equals(ready.get("eligible")))forbidden();
        if(!Boolean.TRUE.equals(ready.get("ready"))){
            var issues=new ArrayList<Map<String,Object>>();
            for(Object x:(List<?>)ready.get("blockedSections")){
                Map<?,?> section=(Map<?,?>)x;
                for(Object y:(List<?>)section.get("conflicts")){
                    Map<?,?> slot=(Map<?,?>)y;
                    for(Object z:(List<?>)slot.get("issues")){
                        var issue=new LinkedHashMap<String,Object>();
                        ((Map<?,?>)z).forEach((k,value)->issue.put(k.toString(),value));
                        issue.putIfAbsent("patternId",slot.get("id"));issue.put("candidateSectionCode",section.get("code"));issues.add(issue);
                    }
                }
            }
            if(!issues.isEmpty()){var details=new LinkedHashMap<>(ready);details.put("issues",issues);throw new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",details);}
            throw new ApiFailure(409,"INVALID_STATE","Cần hoàn tất phân công và xếp đủ các buổi trước khi gửi duyệt.",ready);
        }
        var work=db.need("INSERT INTO work_items(kind,created_by) VALUES ('TIMETABLE',?) RETURNING id",a.id());
        var created=db.need("INSERT INTO approval_requests(work_item_id,requested_by,payload) VALUES (?,?,?::jsonb) RETURNING id",n(work,"id"),a.id(),encode(assessment.snapshot()));
        long approvalId=n(created,"id");
        db.jdbc().update("UPDATE timetable_versions SET status='SUBMITTED',section_count=?,approval_request_id=? WHERE id=?",n(ready,"draftCount"),approvalId,id);
        db.audit(a,"SUBMIT","timetable_versions",id);
        db.jdbc().update("INSERT INTO notifications(user_id,message) SELECT u.id,? FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.status='ACTIVE' AND r.code='MANAGER'","Thời khóa biểu bản "+v.get("version_no")+" đang chờ duyệt. Mở Duyệt thời khóa biểu để xem phương án.");
        return Map.of("approvalId",approvalId);
    }
    public Object decide(Accounts.Actor actor,Map<String,Object> a,Map<String,Object> body){
        if(n(a,"requested_by")==actor.id())forbidden();
        String decision=choice(body.get("decision"),"APPROVED","RETURNED");
        if("RETURNED".equals(decision))status(a,"PENDING","APPROVED");else status(a,"PENDING");
        String reason="RETURNED".equals(decision)?text(body.get("reason"),500):null;
        long id=n(a,"id");
        db.jdbc().update("UPDATE approval_requests SET status=?,decided_by=?,decided_at=now(),decision_reason=? WHERE id=?",decision,actor.id(),reason,id);
        db.jdbc().update("UPDATE work_items SET status=? WHERE id=?",decision,n(a,"work_item_id"));
        if("RETURNED".equals(decision)){
            db.jdbc().update("UPDATE timetable_versions SET status='RETURNED' WHERE approval_request_id=?",id);
            db.jdbc().update("INSERT INTO notifications(user_id,message) VALUES (?,?)",n(a,"requested_by"),"Thời khóa biểu được trả lại. Lý do: "+reason);
        }
        db.audit(actor,decision,"approval_requests",id);return Map.of("status",decision);
    }
    void notifySection(long section,String message){
        db.jdbc().update("""
          INSERT INTO notifications(user_id,message) SELECT recipient,? FROM (
            SELECT p.user_id AS recipient FROM section_assignments a JOIN staff_profiles p ON p.id=a.staff_id WHERE a.section_id=? AND a.valid_to IS NULL
            UNION SELECT student_user_id FROM section_enrollments WHERE section_id=? AND status='ENROLLED'
          ) audience
          """,message,section,section);
    }
    public Object apply(Accounts.Actor actor,Map<String,Object> a){
        if(n(a,"requested_by")==actor.id())forbidden();long id=n(a,"id");
        var prior=db.one("SELECT result::text AS result FROM execution_records WHERE approval_request_id=?",id);
        if(prior!=null)return decode(prior.get("result"));
        status(a,"APPROVED");
        var v=db.need("SELECT * FROM timetable_versions WHERE approval_request_id=?",id);status(v,"SUBMITTED");
        long versionId=n(v,"id"),semester=n(v,"semester_id");
        var ctx=scheduling.context();var sections=new LinkedHashSet<Long>();long total=0;
        var snapshot=(List<?>)a.get("payload");
        var current=new HashMap<Long,Slot>();
        for(var p:ctx.patterns())if(n(ctx.sections().get(p.section()),"semester_id")==semester)current.put(p.id(),p);
        if(snapshot.isEmpty()||snapshot.size()!=current.size())throw state("Phương án đã thay đổi sau khi gửi duyệt.");
        for(Object raw:snapshot){
            if(!(raw instanceof Map<?,?> r))throw state("Hồ sơ không hợp lệ.");
            long pattern=id(r.get("id"));var slot=current.remove(pattern);
            if(slot==null||slot.section()!=id(r.get("section"))||!Objects.equals(slot.staff(),id(r.get("staff")))||slot.room()!=id(r.get("room"))||slot.day()!=id(r.get("day"))||slot.first()!=id(r.get("first"))||slot.count()!=id(r.get("count"))||!slot.from().toString().equals(r.get("from"))||!slot.to().toString().equals(r.get("to")))throw state("Phương án đã thay đổi sau khi gửi duyệt.");
            var result=scheduling.check(ctx,slot,actor,false);
            if(!result.valid())throw new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",Map.of("issues",result.issues()));
            long count=0;
            for(var date:occurrences(slot)){
                if(scheduling.holiday(ctx,n(ctx.rooms().get(slot.room()),"campus_id"),date))continue;
                var time=scheduling.interval(ctx,slot,date);
                db.jdbc().update("""
                  INSERT INTO class_sessions(section_id,staff_id,room_id,period_from,period_count,start_at,end_at,timetable_version_id,pattern_id)
                  VALUES (?,?,?,?,?,?,?,?,?)
                  """,slot.section(),slot.staff(),slot.room(),slot.first(),slot.count(),Timestamp.valueOf(time.start()),Timestamp.valueOf(time.end()),versionId,slot.id());
                count++;
            }
            if(count==0)throw state("Một lịch định kỳ không sinh được buổi học vì toàn bộ ngày đều nghỉ.");
            total+=count;sections.add(slot.section());
        }
        if(!current.isEmpty()||sections.size()!=n(v,"section_count"))throw state("Phương án đã thay đổi sau khi gửi duyệt.");
        for(long section:sections){
            db.jdbc().update("UPDATE course_sections SET status='OPEN',version=version+1,updated_at=now() WHERE id=?",section);
            notifySection(section,"Đã công bố lịch lớp "+ctx.sections().get(section).get("code")+".");
        }
        db.jdbc().update("UPDATE timetable_versions SET status='SUPERSEDED' WHERE semester_id=? AND status='PUBLISHED'",semester);
        db.jdbc().update("UPDATE timetable_versions SET status='PUBLISHED',published_by=?,published_at=now() WHERE id=?",actor.id(),versionId);
        db.jdbc().update("UPDATE approval_requests SET status='APPLIED' WHERE id=?",id);
        db.jdbc().update("UPDATE work_items SET status='APPLIED' WHERE id=?",n(a,"work_item_id"));
        var result=Map.of("versionId",versionId,"sessions",total,"sections",sections.size());
        db.jdbc().update("INSERT INTO execution_records(approval_request_id,idempotency_key,result) VALUES (?,?,?::jsonb)",id,"fm05-publish:"+id,encode(result));
        db.audit(actor,"PUBLISH","timetable_versions",versionId);return result;
    }
    public Object publish(Accounts.Actor actor,long id){
        var a=approval(id);if("PENDING".equals(a.get("status"))){decide(actor,a,Map.of("decision","APPROVED"));a=approval(id);}return apply(actor,a);
    }
}
