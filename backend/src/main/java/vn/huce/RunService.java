package vn.huce;

import static vn.huce.Inputs.*;
import static vn.huce.SchedulingService.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.sql.Date;

@Service
class RunService {
    private final SqlStore db;
    private final SchedulingService scheduling;
    private final GeneticScheduler genetic;
    private final ObjectMapper json;
    RunService(SqlStore db,SchedulingService scheduling,GeneticScheduler genetic,ObjectMapper json){this.db=db;this.scheduling=scheduling;this.genetic=genetic;this.json=json;}
    static final String MANAGER_SCOPE="""
      EXISTS(SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.id=run.requested_by AND r.code='MANAGER')
      AND NOT EXISTS(SELECT 1 FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id JOIN subjects s ON s.id=c.subject_id
        WHERE i.run_id=run.id AND s.managing_unit_id IS DISTINCT FROM ?::bigint)
      """;
    Map<String,Object> get(Accounts.Actor a,long id){
        role(a,"STAFF","MANAGER");
        var row=db.need("SELECT *,parameters::text AS parameters_json FROM schedule_runs WHERE id=?",id);
        if("STAFF".equals(a.role())&&n(row,"requested_by")!=a.id()&&(!Set.of("COMPLETED","APPLIED").contains(row.get("status"))||!db.exists("SELECT 1 FROM schedule_runs run WHERE run.id=? AND "+MANAGER_SCOPE,id,a.unitId())))throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        Object raw=row.remove("parameters_json");
        try{row.put("parameters",json.readValue(raw.toString(),Object.class));}catch(Exception e){throw new IllegalStateException(e);}
        return row;
    }
    void unlocked(long sem){if(db.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",sem))throw state("Học kỳ đang chờ duyệt.");}
    Object enqueue(Accounts.Actor a,long sem,Object raw){
        role(a,"STAFF","MANAGER");unlocked(sem);
        if(db.exists("SELECT 1 FROM schedule_runs WHERE semester_id=? AND status IN ('QUEUED','RUNNING')",sem))throw new ApiFailure(409,"DUPLICATE_REQUEST","Học kỳ đang có một lần chạy xếp lịch.");
        var params=GeneticScheduler.parameters(raw);genetic.input(a,sem);
        String encoded;try{encoded=json.writeValueAsString(params);}catch(Exception e){throw new IllegalStateException(e);}
        var row=db.need("INSERT INTO schedule_runs(semester_id,requested_by,parameters) VALUES (?,?,?::jsonb) RETURNING id",sem,a.id(),encoded);
        db.audit(a,"QUEUE","schedule_runs",n(row,"id"));return row;
    }
    Object apply(Accounts.Actor a,long id){
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
        for(var slot:plan)db.db.update("""
          INSERT INTO section_schedule_patterns(section_id,staff_id,room_id,day_of_week,period_from,period_count,effective_from,effective_to,source,run_id,created_by)
          VALUES (?,?,?,?,?,?,?,?,'GENETIC',?,?)
          """,slot.section(),slot.staff(),slot.room(),slot.day(),slot.first(),slot.count(),Date.valueOf(slot.from()),Date.valueOf(slot.to()),id,a.id());
        db.db.update("UPDATE schedule_runs SET status='APPLIED' WHERE id=?",id);db.audit(a,"APPLY","schedule_runs",id);
        return Map.of("status","APPLIED","patterns",plan.size());
    }
    void discard(Accounts.Actor a,long id){
        var run=get(a,id);
        if("STAFF".equals(a.role())&&n(run,"requested_by")!=a.id())throw new ApiFailure(403,"FORBIDDEN","Bạn không có quyền trong phạm vi này.");
        if(!Set.of("QUEUED","COMPLETED","FAILED").contains(run.get("status")))throw state("Lần chạy không thể bỏ ở trạng thái hiện tại.");
        db.db.update("UPDATE schedule_runs SET status='DISCARDED' WHERE id=?",id);db.audit(a,"DISCARD","schedule_runs",id);
    }
}

@Service
class RunWorker {
    private final SqlStore db;
    private final GeneticScheduler genetic;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    RunWorker(SqlStore db,GeneticScheduler genetic,ObjectMapper json,PlatformTransactionManager manager){this.db=db;this.genetic=genetic;this.json=json;this.tx=new TransactionTemplate(manager);}
    private void lock(){db.db.execute("SELECT pg_advisory_xact_lock(5060505)");}
    @Scheduled(fixedDelay=1000,initialDelay=3000)
    public void poll(){
        Long id=null;
        try{
            var run=tx.execute(status->{
                lock();
                db.db.update("UPDATE schedule_runs SET status='FAILED',finished_at=now(),message='Lần chạy bị gián đoạn.' WHERE status='RUNNING' AND started_at<now()-interval '10 minutes'");
                return db.one("""
                  UPDATE schedule_runs SET status='RUNNING',started_at=now() WHERE id=(
                    SELECT id FROM schedule_runs WHERE status='QUEUED' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
                  RETURNING id,semester_id,requested_by,parameters::text AS parameters_json
                  """);
            });
            if(run==null)return;id=n(run,"id");final long runId=id;
            var settings=json.readValue(s(run,"parameters_json"),GeneticScheduler.Parameters.class);
            var input=tx.execute(status->{
                lock();
                var u=db.need("""
                  SELECT u.id,u.username,r.code AS role,p.id AS staff_id,p.unit_id FROM users u JOIN roles r ON r.role_id=u.role_id
                  LEFT JOIN staff_profiles p ON p.user_id=u.id AND p.status='ACTIVE' WHERE u.id=? AND u.status='ACTIVE'
                  """,n(run,"requested_by"));
                var actor=new Accounts.Actor(n(u,"id"),s(u,"username"),s(u,"role"),nullableId(u.get("staff_id")),nullableId(u.get("unit_id")));
                role(actor,"STAFF","MANAGER");return genetic.input(actor,n(run,"semester_id"));
            });
            var outcome=genetic.solve(input,settings,p->tx.executeWithoutResult(status->{
                db.db.update("UPDATE schedule_runs SET generations_run=? WHERE id=? AND status='RUNNING'",p.generation(),runId);
                db.db.update("""
                  INSERT INTO schedule_run_progress(run_id,generation,hard_violations,soft_penalty) VALUES (?,?,?,?)
                  ON CONFLICT(run_id,generation) DO UPDATE SET hard_violations=EXCLUDED.hard_violations,soft_penalty=EXCLUDED.soft_penalty
                  """,runId,p.generation(),p.hard(),p.soft());
            }));
            tx.executeWithoutResult(status->{
                lock();if(!db.exists("SELECT 1 FROM schedule_runs WHERE id=? AND status='RUNNING'",runId))return;
                var score=outcome.score();
                for(int i=0;i<score.slots().size();i++){var slot=score.slots().get(i);db.db.update("""
                  INSERT INTO schedule_run_items(run_id,section_id,staff_id,room_id,day_of_week,period_from,period_count,violation_note) VALUES (?,?,?,?,?,?,?,?)
                  """,runId,slot.section(),slot.staff(),slot.room(),slot.day(),slot.first(),slot.count(),outcome.notes().get(i));}
                db.db.update("UPDATE schedule_runs SET status='COMPLETED',generations_run=?,hard_violations=?,soft_penalty=?,fitness=?,finished_at=now() WHERE id=?",outcome.generation(),score.hard(),score.soft(),1.0/(1+1000.0*score.hard()+score.soft()),runId);
            });
        }catch(Exception e){
            org.slf4j.LoggerFactory.getLogger(RunWorker.class).warn("Schedule run failed",e);
            if(id!=null)db.db.update("UPDATE schedule_runs SET status='FAILED',finished_at=now(),message=? WHERE id=? AND status='RUNNING'",e instanceof ApiFailure?e.getMessage():"Không thể hoàn tất xếp lịch.",id);
        }
    }
}
