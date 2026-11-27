package vn.huce.controller;

import vn.huce.Accounts;
import vn.huce.service.MutationService;
import vn.huce.service.RunService;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedule-runs")
public class RunController {
    private final MutationService writes;
    private final RunService runs;
    RunController(MutationService writes, RunService runs) {
        this.writes = writes;
        this.runs = runs;
    }
    private Accounts.Actor actor(Authentication auth){var a=(Accounts.Actor)auth.getPrincipal();role(a,"STAFF","MANAGER");return a;}
    @GetMapping("/active")
    public Object active(Authentication auth, @RequestParam String semesterId) {
        return runs.active(actor(auth), semesterId);
    }

    @GetMapping
    public Object list(Authentication auth, @RequestParam Map<String,String> q) {
        return runs.list(actor(auth), q);
    }

    @GetMapping("/{id}")
    public Object get(Authentication auth, @PathVariable long id) {
        return runs.detail(actor(auth), id);
    }

    @GetMapping("/{id}/items")
    public Object items(Authentication auth, @PathVariable long id) {
        return runs.items(actor(auth), id);
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
