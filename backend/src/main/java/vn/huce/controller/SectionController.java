package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.service.MutationService;
import vn.huce.service.SectionMutationService;
import vn.huce.service.SectionService;
import static vn.huce.validation.Inputs.role;

@RestController
@RequestMapping("/api")
public class SectionController {
    private final MutationService writes;
    private final SectionService sections;
    private final SectionMutationService mutations;

    SectionController(MutationService writes, SectionService sections, SectionMutationService mutations) {
        this.writes = writes;
        this.sections = sections;
        this.mutations = mutations;
    }

    private Actor actor(Authentication authentication) {
        return (Actor) authentication.getPrincipal();
    }

    private Actor staff(Authentication authentication) {
        Actor actor = actor(authentication);
        role(actor, "STAFF");
        return actor;
    }

    @GetMapping("/course-sections")
    public Object sections(Authentication authentication, @RequestParam Map<String, String> query) {
        return sections.sections(actor(authentication), query);
    }

    @GetMapping("/schedule-patterns")
    public Object patterns(Authentication authentication, @RequestParam Map<String, String> query) {
        return sections.patterns(actor(authentication), query);
    }

    @PostMapping("/course-sections")
    public ResponseEntity<?> create(Authentication authentication, HttpServletRequest request,
                                    @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> sections.saveDraft(actor, body)));
    }

    @PostMapping("/course-sections/import")
    public ResponseEntity<?> imports(Authentication authentication, HttpServletRequest request,
                                     @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> mutations.importSections(actor, body)));
    }

    @PatchMapping("/course-sections/{id}")
    public Object update(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                         @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        return writes.write(actor, request, body, () -> mutations.update(actor, id, body));
    }

    @PostMapping("/course-sections/{id}/cancel")
    public ResponseEntity<Void> cancel(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                       @RequestParam(required = false) String version) {
        Actor actor = staff(authentication);
        writes.write(actor, request, Map.of(), () -> mutations.cancel(actor, id, version));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/course-sections/{id}/assignments")
    public ResponseEntity<Void> assign(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                       @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        writes.write(actor, request, body, () -> mutations.assign(actor, id, body));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/course-sections/{id}/target-groups")
    public ResponseEntity<Void> groups(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                       @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        writes.write(actor, request, body, () -> mutations.groups(actor, id, body));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schedule/conflicts/check")
    public Object conflicts(Authentication authentication, @RequestBody Map<String, Object> body) {
        return mutations.conflicts(actor(authentication), body);
    }

    @PostMapping("/schedule-patterns")
    public ResponseEntity<?> createPattern(Authentication authentication, HttpServletRequest request,
                                           @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> mutations.savePattern(actor, body, null)));
    }

    @PatchMapping("/schedule-patterns/{id}")
    public Object updatePattern(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                @RequestBody Map<String, Object> body) {
        Actor actor = staff(authentication);
        return writes.write(actor, request, body, () -> mutations.savePattern(actor, body, id));
    }

    @DeleteMapping("/schedule-patterns/{id}")
    public ResponseEntity<Void> deletePattern(Authentication authentication, HttpServletRequest request,
                                              @PathVariable long id, @RequestParam(required = false) String version) {
        Actor actor = staff(authentication);
        writes.write(actor, request, Map.of(), () -> mutations.deletePattern(actor, id, version));
        return ResponseEntity.noContent().build();
    }
}
