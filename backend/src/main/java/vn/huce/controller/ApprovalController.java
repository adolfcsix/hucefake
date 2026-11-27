package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.service.ApprovalService;
import vn.huce.service.MutationService;

import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.role;

@RestController
@RequestMapping("/api")
public class ApprovalController {
    private final MutationService writes;
    private final ApprovalService approvals;

    ApprovalController(MutationService writes, ApprovalService approvals) {
        this.writes = writes;
        this.approvals = approvals;
    }

    private Actor actor(Authentication authentication, String... roles) {
        Actor actor = (Actor) authentication.getPrincipal();
        role(actor, roles);
        return actor;
    }

    @GetMapping("/timetable-versions")
    public Object versions(Authentication authentication, @RequestParam Map<String, String> query) {
        return approvals.versions(actor(authentication, "STAFF", "MANAGER"), query);
    }

    @GetMapping("/approvals/{id}")
    public Map<String, Object> get(Authentication authentication, @PathVariable long id) {
        actor(authentication, "STAFF", "MANAGER");
        return approvals.approval(id);
    }

    @GetMapping("/timetable-versions/readiness")
    public Map<String, Object> readiness(Authentication authentication, @RequestParam String semesterId) {
        return approvals.readiness(actor(authentication, "STAFF"), id(semesterId));
    }

    @PostMapping("/timetable-versions")
    public ResponseEntity<?> create(Authentication authentication, HttpServletRequest request,
                                    @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "STAFF");
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> approvals.create(actor, id(body.get("semesterId")))));
    }

    @PostMapping("/timetable-versions/{id}/submit")
    public Object submit(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                         @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication, "STAFF");
        return writes.write(actor, request, body == null ? Map.of() : body, () -> approvals.submit(actor, id));
    }

    @PostMapping("/approvals/{id}/decide")
    public Object decide(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                         @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "MANAGER");
        return writes.write(actor, request, body, () -> approvals.decide(actor, approvals.approval(id), body));
    }

    @PostMapping("/approvals/{id}/apply")
    public Object apply(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                        @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication, "MANAGER");
        return writes.write(actor, request, body == null ? Map.of() : body,
                () -> approvals.apply(actor, approvals.approval(id)));
    }

    @PostMapping("/approvals/{id}/publish")
    public Object publish(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                          @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication, "MANAGER");
        return writes.write(actor, request, body == null ? Map.of() : body, () -> approvals.publish(actor, id));
    }
}
