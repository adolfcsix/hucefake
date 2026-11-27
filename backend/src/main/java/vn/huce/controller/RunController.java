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
import vn.huce.service.MutationService;
import vn.huce.service.RunService;

import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.role;

@RestController
@RequestMapping("/api/schedule-runs")
public class RunController {
    private final MutationService writes;
    private final RunService runs;

    RunController(MutationService writes, RunService runs) {
        this.writes = writes;
        this.runs = runs;
    }

    private Actor actor(Authentication authentication) {
        Actor actor = (Actor) authentication.getPrincipal();
        role(actor, "STAFF", "MANAGER");
        return actor;
    }

    @GetMapping("/active")
    public Object active(Authentication authentication, @RequestParam String semesterId) {
        return runs.active(actor(authentication), semesterId);
    }

    @GetMapping
    public Object list(Authentication authentication, @RequestParam Map<String, String> query) {
        return runs.list(actor(authentication), query);
    }

    @GetMapping("/{id}")
    public Object get(Authentication authentication, @PathVariable long id) {
        return runs.detail(actor(authentication), id);
    }

    @GetMapping("/{id}/items")
    public Object items(Authentication authentication, @PathVariable long id) {
        return runs.items(actor(authentication), id);
    }

    @PostMapping
    public ResponseEntity<?> enqueue(Authentication authentication, HttpServletRequest request,
                                     @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        return ResponseEntity.status(202).body(writes.write(actor, request, body,
                () -> runs.enqueue(actor, id(body.get("semesterId")), body.get("parameters"))));
    }

    @PostMapping("/{id}/apply")
    public Object apply(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                        @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        return writes.write(actor, request, body == null ? Map.of() : body, () -> runs.apply(actor, id));
    }

    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discard(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        writes.write(actor, request, body == null ? Map.of() : body, () -> {
            runs.discard(actor, id);
            return null;
        });
        return ResponseEntity.noContent().build();
    }
}
