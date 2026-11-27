package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.model.SessionChangeAction;
import vn.huce.service.MutationService;
import vn.huce.service.AvailabilityService;
import vn.huce.service.SessionMutationService;

import static vn.huce.validation.Inputs.choice;
import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.role;

@RestController
@RequestMapping("/api")
public class SessionMutationController {
    private final MutationService writes;
    private final SessionMutationService sessions;
    private final AvailabilityService availability;

    SessionMutationController(MutationService writes, SessionMutationService sessions, AvailabilityService availability) {
        this.writes = writes;
        this.sessions = sessions;
        this.availability = availability;
    }

    private Actor actor(Authentication authentication, String... roles) {
        Actor actor = (Actor) authentication.getPrincipal();
        role(actor, roles);
        return actor;
    }

    @PostMapping("/class-sessions")
    public ResponseEntity<?> reschedule(Authentication authentication, HttpServletRequest request,
                                        @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "STAFF");
        Map<String, Object> form = new LinkedHashMap<>(body);
        form.put("action", SessionChangeAction.RESCHEDULE.name());
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> sessions.change(actor, id(body.get("sourceSessionId")), form, null)));
    }

    @PatchMapping("/class-sessions/{id}")
    public Object patch(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                        @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "STAFF");
        choice(body.get("action"), "RESCHEDULE", "ROOM_CHANGE");
        return writes.write(actor, request, body, () -> sessions.change(actor, id, body, null));
    }

    @PostMapping("/class-sessions/{id}/exceptions")
    public Object exception(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                            @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "STAFF");
        return writes.write(actor, request, body, () -> sessions.change(actor, id, body, null));
    }

    @GetMapping("/me/availability")
    public Object availability(Authentication authentication, @RequestParam String semesterId) {
        return availability.find(actor(authentication, "LECTURER"), id(semesterId));
    }

    @PutMapping("/me/availability")
    public ResponseEntity<Void> setAvailability(Authentication authentication, HttpServletRequest request,
                                                @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "LECTURER");
        writes.write(actor, request, body, () -> {
            availability.replace(actor, body);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session-change-requests")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object requests(Authentication authentication, @RequestParam Map<String, String> query) {
        return sessions.requests(actor(authentication, "STAFF", "LECTURER"), query);
    }

    @PostMapping("/class-sessions/{id}/requests")
    public ResponseEntity<?> request(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                     @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "LECTURER");
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> sessions.request(actor, id, body)));
    }

    @PostMapping("/session-change-requests/{id}/decide")
    public ResponseEntity<Void> decide(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                       @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication, "STAFF");
        writes.write(actor, request, body, () -> {
            sessions.decide(actor, id, body);
            return null;
        });
        return ResponseEntity.noContent().build();
    }
}
