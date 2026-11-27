package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.service.SessionReadService;
import vn.huce.service.MutationService;

@RestController
@RequestMapping("/api")
public class SessionReadController {
    private final SessionReadService reads;
    private final MutationService writes;

    public SessionReadController(SessionReadService reads, MutationService writes) {
        this.reads = reads;
        this.writes = writes;
    }

    private Actor actor(Authentication authentication) {
        return (Actor) authentication.getPrincipal();
    }

    @GetMapping("/schedule")
    public Object schedule(Authentication authentication, @RequestParam Map<String, String> query) {
        return reads.schedule(actor(authentication), query);
    }

    @GetMapping("/schedule/sections")
    public Object sections(Authentication authentication, @RequestParam Map<String, String> query) {
        return reads.sections(actor(authentication), query);
    }

    @GetMapping("/schedule/statistics")
    public Object stats(Authentication authentication, @RequestParam String semesterId) {
        return reads.stats(actor(authentication), semesterId);
    }

    @GetMapping("/notifications")
    public Object notifications(Authentication authentication, @RequestParam Map<String, String> query) {
        return reads.notifications(actor(authentication), query);
    }

    @PostMapping("/notifications/read-all")
    public Object readAll(Authentication authentication, HttpServletRequest request,
                          @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        return writes.write(actor, request, body == null ? Map.of() : body,
                () -> Map.of("marked", reads.markAll(actor)));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> read(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        writes.write(actor, request, body == null ? Map.of() : body, () -> {
            reads.markRead(actor, id);
            return null;
        });
        return ResponseEntity.noContent().build();
    }
}
