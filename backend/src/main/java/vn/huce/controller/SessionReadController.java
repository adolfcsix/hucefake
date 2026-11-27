package vn.huce.controller;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.huce.Accounts;
import vn.huce.service.MutationService;
import vn.huce.service.SessionReadService;

@RestController
@RequestMapping("/api")
public class SessionReadController {
    private final SessionReadService reads;
    private final MutationService writes;

    public SessionReadController(SessionReadService reads, MutationService writes) {
        this.reads = reads;
        this.writes = writes;
    }

    private Accounts.Actor actor(Authentication auth) {
        return (Accounts.Actor) auth.getPrincipal();
    }

    @GetMapping("/schedule")
    public Object schedule(Authentication auth, @RequestParam Map<String,String> q) {
        return reads.schedule(actor(auth), q);
    }

    @GetMapping("/schedule/sections")
    public Object sections(Authentication auth, @RequestParam Map<String,String> q) {
        return reads.sections(actor(auth), q);
    }

    @GetMapping("/schedule/statistics")
    public Object stats(Authentication auth, @RequestParam String semesterId) {
        return reads.stats(actor(auth), semesterId);
    }

    @GetMapping("/notifications")
    public Object notifications(Authentication auth, @RequestParam Map<String,String> q) {
        return reads.notifications(actor(auth), q);
    }

    @PostMapping("/notifications/read-all")
    public Object readAll(Authentication auth, HttpServletRequest req, @RequestBody(required=false) Map<String,Object> body) {
        var actor = actor(auth);
        return writes.write(actor, req, body == null ? Map.of() : body, () -> Map.of("marked", reads.markAll(actor)));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<?> read(Authentication auth, HttpServletRequest req, @PathVariable long id, @RequestBody(required=false) Map<String,Object> body) {
        var actor = actor(auth);
        writes.write(actor, req, body == null ? Map.of() : body, () -> {
            reads.markRead(actor, id);
            return null;
        });
        return ResponseEntity.noContent().build();
    }
}
