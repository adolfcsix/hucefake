package vn.huce.controller;

import vn.huce.Accounts;
import vn.huce.service.ApprovalService;
import vn.huce.service.MutationService;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApprovalController {
    private final MutationService writes;private final ApprovalService approvals;
    ApprovalController(MutationService writes, ApprovalService approvals) {
        this.writes = writes;
        this.approvals = approvals;
    }
    private Accounts.Actor actor(Authentication auth,String... roles){var a=(Accounts.Actor)auth.getPrincipal();role(a,roles);return a;}
    @GetMapping("/timetable-versions")
    public Object versions(Authentication auth, @RequestParam Map<String,String> q) {
        return approvals.versions(actor(auth, "STAFF", "MANAGER"), q);
    }
    @GetMapping("/approvals/{id}") Object get(Authentication auth,@PathVariable long id){actor(auth,"STAFF","MANAGER");return approvals.approval(id);}
    @GetMapping("/timetable-versions/readiness") @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object readiness(Authentication auth,@RequestParam String semesterId){return approvals.readiness(actor(auth,"STAFF"),id(semesterId));}
    @PostMapping("/timetable-versions") ResponseEntity<?> create(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth,"STAFF");return ResponseEntity.status(201).body(writes.write(a,req,f,()->approvals.create(a,id(f.get("semesterId")))));
    }
    @PostMapping("/timetable-versions/{id}/submit") Object submit(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth,"STAFF");return writes.write(a,req,f==null?Map.of():f,()->approvals.submit(a,id));
    }
    @PostMapping("/approvals/{id}/decide") Object decide(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth,"MANAGER");return writes.write(a,req,f,()->approvals.decide(a,approvals.approval(id),f));
    }
    @PostMapping("/approvals/{id}/apply") Object apply(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth,"MANAGER");return writes.write(a,req,f==null?Map.of():f,()->approvals.apply(a,approvals.approval(id)));
    }
    @PostMapping("/approvals/{id}/publish") Object publish(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth,"MANAGER");return writes.write(a,req,f==null?Map.of():f,()->approvals.publish(a,id));
    }
}
