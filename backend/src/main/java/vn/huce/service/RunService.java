package vn.huce.service;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.sql.Date;

@Service
public class RunService {
    private final SqlStore db;
    private final SchedulingService scheduling;
    private final GeneticScheduler genetic;
    private final ObjectMapper json;
    RunService(SqlStore db,SchedulingService scheduling,GeneticScheduler genetic,ObjectMapper json){this.db=db;this.scheduling=scheduling;this.genetic=genetic;this.json=json;}
    public static final String MANAGER_SCOPE="""
      EXISTS(SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.id=run.requested_by AND r.code='MANAGER')
      AND NOT EXISTS(SELECT 1 FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id JOIN subjects s ON s.id=c.subject_id
        WHERE i.run_id=run.id AND s.managing_unit_id IS DISTINCT FROM ?::bigint)
      """;
    public Map<String,Object> get(Accounts.Actor a,long id){
        role(a,"STAFF","MANAGER");
        var row=db.need("SELECT *,parameters::text AS parameters_json FROM schedule_runs WHERE id=?",id);
        if("STAFF".equals(a.role())&&n(row,"requested_by")!=a.id()&&(!Set.of("COMPLETED","APPLIED").contains(row.get("status"))||!db.exists("SELECT 1 FROM schedule_runs run WHERE run.id=? AND "+MANAGER_SCOPE,id,a.unitId())))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        Object raw=row.remove("parameters_json");
        try{row.put("parameters",json.readValue(raw.toString(),Object.class));}catch(Exception e){throw new IllegalStateException(e);}
        return row;
    }
    public void unlocked(long sem){if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",sem))throw state("Học kỳ đang chờ duyệt.");}
    public Object enqueue(Accounts.Actor a,long sem,Object raw){
        role(a,"STAFF","MANAGER");unlocked(sem);
        if(db.exists("SELECT 1 FROM schedule_runs WHERE semester_id=? AND status IN ('QUEUED','RUNNING')",sem))throw new ApiFailure(409,"DUPLICATE_REQUEST","Học kỳ đang có một lần chạy xếp lịch.");
        var params=GeneticScheduler.parameters(raw);genetic.input(a,sem);
        String encoded;try{encoded=json.writeValueAsString(params);}catch(Exception e){throw new IllegalStateException(e);}
        var row=db.need("INSERT INTO schedule_runs(semester_id,requested_by,parameters) VALUES (?,?,?::jsonb) RETURNING id",sem,a.id(),encoded);
        db.audit(a,"QUEUE","schedule_runs",n(row,"id"));return row;
    }
    public Object apply(Accounts.Actor a,long id){
        var run=get(a,id);
        if("APPLIED".equals(run.get("status")))return Map.of("status","APPLIED");
        if(!"COMPLETED".equals(run.get("status"))||n(run,"hard_violations")!=0)throw state("Chỉ áp dụng được phương án hoàn tất với 0 vi phạm cứng.");
        long sem=n(run,"semester_id");unlocked(sem);var ctx=scheduling.context();
        var expected=genetic.targets(ctx,a,sem);var expectedIds=new HashSet<Long>();for(var row:expected)expectedIds.add(n(row,"id"));
        var plan=new ArrayList<Slot>();var fixed=new ArrayList<>(ctx.patterns());
        var stale=new ApiFailure(409,"VERSION_CONFLICT","Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.",Map.of("runId",id));
        for(var r:db.rows("SELECT * FROM schedule_run_items WHERE run_id=? ORDER BY id",id)){
            var section=ctx.sections().get(n(r,"section_id"));if(section==null)throw stale;
            unit(a,section.get("managing_unit_id"));
            if(!expectedIds.contains(n(section,"id"))||!Objects.equals(nullableId(section.get("staff_id")),nullableId(r.get("staff_id")))||n(section,"periods_per_session")!=n(r,"period_count"))throw stale;
            var room=ctx.rooms().get(n(r,"room_id"));if(room==null)throw stale;scheduling.requireCampus(a,n(room,"campus_id"));
            var slot=GeneticScheduler.make(section,n(room,"id"),(int)n(r,"day_of_week"),(int)n(r,"period_from"));
            var test=new Context(ctx.sections(),ctx.rooms(),ctx.periods(),ctx.availability(),ctx.holidays(),fixed);
            var result=scheduling.check(test,slot,a,false);
            if(!result.valid())throw new ApiFailure(409,"SCHEDULE_CONFLICT","Lịch dự kiến có xung đột.",Map.of("issues",result.issues()));
            plan.add(slot);fixed.add(slot);
        }
        for(var s:expected)if(fixed.stream().filter(p->p.section()==n(s,"id")).count()!=n(s,"sessions_per_week"))throw stale;
        for(var slot:plan)db.jdbc().update("""
          INSERT INTO section_schedule_patterns(section_id,staff_id,room_id,day_of_week,period_from,period_count,effective_from,effective_to,source,run_id,created_by)
          VALUES (?,?,?,?,?,?,?,?,'GENETIC',?,?)
          """,slot.section(),slot.staff(),slot.room(),slot.day(),slot.first(),slot.count(),Date.valueOf(slot.from()),Date.valueOf(slot.to()),id,a.id());
        db.jdbc().update("UPDATE schedule_runs SET status='APPLIED' WHERE id=?",id);db.audit(a,"APPLY","schedule_runs",id);
        return Map.of("status","APPLIED","patterns",plan.size());
    }
    public void discard(Accounts.Actor a,long id){
        var run=get(a,id);
        if("STAFF".equals(a.role())&&n(run,"requested_by")!=a.id())throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        if(!Set.of("QUEUED","COMPLETED","FAILED").contains(run.get("status")))throw state("Lần chạy không thể bỏ ở trạng thái hiện tại.");
        db.jdbc().update("UPDATE schedule_runs SET status='DISCARDED' WHERE id=?",id);db.audit(a,"DISCARD","schedule_runs",id);
    }
}
