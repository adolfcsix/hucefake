package vn.huce.controller;

import vn.huce.Accounts;
import vn.huce.service.MutationService;
import vn.huce.service.SectionMutationService;
import vn.huce.service.SectionService;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    private Accounts.Actor actor(Authentication a){return (Accounts.Actor)a.getPrincipal();}
    @GetMapping("/course-sections")
    public Object sections(Authentication auth, @RequestParam Map<String,String> q) {
        return sections.sections(actor(auth), q);
    }

    @GetMapping("/schedule-patterns")
    public Object patterns(Authentication auth, @RequestParam Map<String,String> q) {
        return sections.patterns(actor(auth), q);
    }

    @PostMapping("/course-sections") ResponseEntity<?> create(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        return ResponseEntity.status(201).body(writes.write(a,req,f,()->sections.saveDraft(a,f)));
    }
    @PostMapping("/course-sections/import") ResponseEntity<?> imports(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        return ResponseEntity.status(201).body(writes.write(a,req,f,() -> mutations.importSections(a, f)));
    }
    @PatchMapping("/course-sections/{id}") Object update(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        return writes.write(a,req,f,() -> mutations.update(a, id, f));
    }
    @PostMapping("/course-sections/{id}/cancel") ResponseEntity<?> cancel(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestParam(required=false) String version){
        var a=actor(auth);role(a,"STAFF");
        writes.write(a,req,Map.of(),() -> mutations.cancel(a, id, version));return ResponseEntity.noContent().build();
    }
    @PutMapping("/course-sections/{id}/assignments") ResponseEntity<?> assign(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        writes.write(a,req,f,() -> mutations.assign(a, id, f));return ResponseEntity.noContent().build();
    }
    @PutMapping("/course-sections/{id}/target-groups") ResponseEntity<?> groups(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");
        writes.write(a,req,f,() -> mutations.groups(a, id, f));return ResponseEntity.noContent().build();
    }
    @PostMapping("/schedule/conflicts/check")
    public Object conflicts(Authentication auth, @RequestBody Map<String,Object> f) {
        return mutations.conflicts(actor(auth), f);
    }
    @PostMapping("/schedule-patterns") ResponseEntity<?> createPattern(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");return ResponseEntity.status(201).body(writes.write(a,req,f,()->mutations.savePattern(a,f,null)));
    }
    @PatchMapping("/schedule-patterns/{id}") Object updatePattern(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF");return writes.write(a,req,f,()->mutations.savePattern(a,f,id));
    }
    @DeleteMapping("/schedule-patterns/{id}") ResponseEntity<?> deletePattern(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestParam(required=false) String version){
        var a=actor(auth);role(a,"STAFF");
        writes.write(a,req,Map.of(),() -> mutations.deletePattern(a, id, version));return ResponseEntity.noContent().build();
    }
}
