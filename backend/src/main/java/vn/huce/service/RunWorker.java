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
class RunWorker {
    private final SqlStore db;
    private final GeneticScheduler genetic;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    RunWorker(SqlStore db,GeneticScheduler genetic,ObjectMapper json,PlatformTransactionManager manager){this.db=db;this.genetic=genetic;this.json=json;this.tx=new TransactionTemplate(manager);}
    private void lock(){db.jdbc().execute("SELECT pg_advisory_xact_lock(5060505)");}
    @Scheduled(fixedDelay=1000,initialDelay=3000)
    public void poll(){
        Long id=null;
        try{
            var run=tx.execute(status->{
                lock();
                db.jdbc().update("UPDATE schedule_runs SET status='FAILED',finished_at=now(),message='Lần chạy bị gián đoạn.' WHERE status='RUNNING' AND started_at<now()-interval '10 minutes'");
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
                db.jdbc().update("UPDATE schedule_runs SET generations_run=? WHERE id=? AND status='RUNNING'",p.generation(),runId);
                db.jdbc().update("""
                  INSERT INTO schedule_run_progress(run_id,generation,hard_violations,soft_penalty) VALUES (?,?,?,?)
                  ON CONFLICT(run_id,generation) DO UPDATE SET hard_violations=EXCLUDED.hard_violations,soft_penalty=EXCLUDED.soft_penalty
                  """,runId,p.generation(),p.hard(),p.soft());
            }));
            tx.executeWithoutResult(status->{
                lock();if(!db.exists("SELECT 1 FROM schedule_runs WHERE id=? AND status='RUNNING'",runId))return;
                var score=outcome.score();
                for(int i=0;i<score.slots().size();i++){var slot=score.slots().get(i);db.jdbc().update("""
                  INSERT INTO schedule_run_items(run_id,section_id,staff_id,room_id,day_of_week,period_from,period_count,violation_note) VALUES (?,?,?,?,?,?,?,?)
                  """,runId,slot.section(),slot.staff(),slot.room(),slot.day(),slot.first(),slot.count(),outcome.notes().get(i));}
                db.jdbc().update("UPDATE schedule_runs SET status='COMPLETED',generations_run=?,hard_violations=?,soft_penalty=?,fitness=?,finished_at=now() WHERE id=?",outcome.generation(),score.hard(),score.soft(),1.0/(1+1000.0*score.hard()+score.soft()),runId);
            });
        }catch(Exception e){
            org.slf4j.LoggerFactory.getLogger(RunWorker.class).warn("Schedule run failed",e);
            if(id!=null)db.jdbc().update("UPDATE schedule_runs SET status='FAILED',finished_at=now(),message=? WHERE id=? AND status='RUNNING'",e instanceof ApiFailure?e.getMessage():"Không thể hoàn tất xếp lịch.",id);
        }
    }
}
