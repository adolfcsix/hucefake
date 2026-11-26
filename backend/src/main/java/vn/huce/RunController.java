package vn.huce;

import static vn.huce.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedule-runs")
class RunController {
    private final SqlStore db;
    private final MutationService writes;
    private final RunService runs;
    RunController(SqlStore db,MutationService writes,RunService runs){this.db=db;this.writes=writes;this.runs=runs;}
    private Accounts.Actor actor(Authentication auth){var a=(Accounts.Actor)auth.getPrincipal();role(a,"STAFF","MANAGER");return a;}
    @GetMapping("/active") Object active(Authentication auth,@RequestParam String semesterId){
        actor(auth);return Map.of("active",db.exists("SELECT 1 FROM schedule_runs WHERE semester_id=? AND status IN ('QUEUED','RUNNING')",id(semesterId)));
    }
    @GetMapping @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object list(Authentication auth,@RequestParam Map<String,String> q){
        var a=actor(auth);String sql="""
          SELECT id,status,generations_run,hard_violations,soft_penalty,fitness,message,requested_by,created_at
          FROM schedule_runs run WHERE semester_id=? AND (? OR requested_by=? OR (status IN ('COMPLETED','APPLIED') AND
          """+RunService.MANAGER_SCOPE+"))";
        var args=Arrays.<Object>asList(id(q.get("semesterId")),"MANAGER".equals(a.role()),a.id(),a.unitId());
        if(!q.containsKey("page")&&!q.containsKey("pageSize"))return db.rows(sql+" ORDER BY id DESC LIMIT 30",args.toArray());
        return db.paged(q,sql,args,"id DESC");
    }
    @GetMapping("/{id}") Object get(Authentication auth,@PathVariable long id){
        var run=runs.get(actor(auth),id);run.put("progress",db.rows("SELECT generation,hard_violations,soft_penalty FROM schedule_run_progress WHERE run_id=? ORDER BY generation",id));return run;
    }
    @GetMapping("/{id}/items") Object items(Authentication auth,@PathVariable long id){
        runs.get(actor(auth),id);return db.rows("""
          SELECT i.*,c.code AS section_code,t.code AS room_code,s.name AS subject_name
          FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id JOIN subjects s ON s.id=c.subject_id
          JOIN teaching_rooms t ON t.id=i.room_id WHERE run_id=? ORDER BY day_of_week,period_from,i.id
          """,id);
    }
    @PostMapping ResponseEntity<?> enqueue(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);return ResponseEntity.status(202).body(writes.write(a,req,f,()->runs.enqueue(a,id(f.get("semesterId")),f.get("parameters"))));
    }
    @PostMapping("/{id}/apply") Object apply(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth);return writes.write(a,req,f==null?Map.of():f,()->runs.apply(a,id));
    }
    @PostMapping("/{id}/discard") ResponseEntity<?> discard(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth);writes.write(a,req,f==null?Map.of():f,()->{runs.discard(a,id);return null;});return ResponseEntity.noContent().build();
    }
}
