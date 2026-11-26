package vn.huce;

import static vn.huce.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class SessionMutationController {
    private final MutationService writes;private final SessionMutationService sessions;
    SessionMutationController(MutationService writes,SessionMutationService sessions){this.writes=writes;this.sessions=sessions;}
    private Accounts.Actor actor(Authentication auth,String... roles){var a=(Accounts.Actor)auth.getPrincipal();role(a,roles);return a;}
    @PostMapping("/class-sessions") ResponseEntity<?> reschedule(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth,"STAFF");var form=new LinkedHashMap<>(f);form.put("action","RESCHEDULE");
        return ResponseEntity.status(201).body(writes.write(a,req,f,()->sessions.change(a,id(f.get("sourceSessionId")),form,null)));
    }
    @PatchMapping("/class-sessions/{id}") Object patch(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth,"STAFF");choice(f.get("action"),"RESCHEDULE","ROOM_CHANGE");return writes.write(a,req,f,()->sessions.change(a,id,f,null));
    }
    @PostMapping("/class-sessions/{id}/exceptions") Object exception(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth,"STAFF");return writes.write(a,req,f,()->sessions.change(a,id,f,null));
    }
    @GetMapping("/me/availability") Object availability(Authentication auth,@RequestParam String semesterId){return sessions.availability(actor(auth,"LECTURER"),id(semesterId));}
    @PutMapping("/me/availability") ResponseEntity<?> setAvailability(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth,"LECTURER");writes.write(a,req,f,()->{sessions.setAvailability(a,f);return null;});return ResponseEntity.noContent().build();
    }
    @GetMapping("/session-change-requests") @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object requests(Authentication auth,@RequestParam Map<String,String> q){return sessions.requests(actor(auth,"STAFF","LECTURER"),q);}
    @PostMapping("/class-sessions/{id}/requests") ResponseEntity<?> request(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth,"LECTURER");return ResponseEntity.status(201).body(writes.write(a,req,f,()->sessions.request(a,id,f)));
    }
    @PostMapping("/session-change-requests/{id}/decide") ResponseEntity<?> decide(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth,"STAFF");writes.write(a,req,f,()->{sessions.decide(a,id,f);return null;});return ResponseEntity.noContent().build();
    }
}
