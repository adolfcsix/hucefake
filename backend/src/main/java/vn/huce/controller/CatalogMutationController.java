package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.service.MutationService;
import vn.huce.service.CatalogMutationService;
import static vn.huce.validation.Inputs.role;

@RestController
@RequestMapping("/api/catalog")
public class CatalogMutationController {
    private final MutationService writes;
    private final CatalogMutationService catalog;

    public CatalogMutationController(MutationService writes, CatalogMutationService catalog) {
        this.writes = writes;
        this.catalog = catalog;
    }

    private Actor actor(Authentication authentication) {
        Actor actor = (Actor) authentication.getPrincipal();
        role(actor, "STAFF");
        return actor;
    }

    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(Authentication authentication, HttpServletRequest request,
                                        @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> catalog.room(actor, body, null)));
    }

    @PutMapping("/rooms/{id}")
    public Object updateRoom(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                             @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        return writes.write(actor, request, body, () -> catalog.room(actor, body, id));
    }

    @PostMapping("/holidays")
    public ResponseEntity<?> createHoliday(Authentication authentication, HttpServletRequest request,
                                           @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> catalog.holiday(actor, body, null)));
    }

    @PutMapping("/holidays/{id}")
    public Object updateHoliday(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        return writes.write(actor, request, body, () -> catalog.holiday(actor, body, id));
    }

    @PostMapping("/periods")
    public ResponseEntity<?> createPeriod(Authentication authentication, HttpServletRequest request,
                                          @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> catalog.createPeriod(actor, body)));
    }
}
