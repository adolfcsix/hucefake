package vn.huce.controller;

import vn.huce.Accounts;
import vn.huce.service.MutationService;
import vn.huce.service.CatalogMutationService;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogMutationController {
    private final MutationService writes;
    private final CatalogMutationService catalog;

    public CatalogMutationController(MutationService writes, CatalogMutationService catalog) {
        this.writes = writes;
        this.catalog = catalog;
    }

    private Accounts.Actor actor(Authentication auth) {
        var a = (Accounts.Actor) auth.getPrincipal();
        role(a, "STAFF");
        return a;
    }

    @PostMapping("/rooms") ResponseEntity<?> createRoom(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);return ResponseEntity.status(201).body(writes.write(a,req,f,()->catalog.room(a,f,null)));
    }
    @PutMapping("/rooms/{id}") Object updateRoom(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);return writes.write(a,req,f,()->catalog.room(a,f,id));
    }
    @PostMapping("/holidays") ResponseEntity<?> createHoliday(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);return ResponseEntity.status(201).body(writes.write(a,req,f,()->catalog.holiday(a,f,null)));
    }
    @PutMapping("/holidays/{id}") Object updateHoliday(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody Map<String,Object> f){
        var a=actor(auth);return writes.write(a,req,f,()->catalog.holiday(a,f,id));
    }
    @PostMapping("/periods")
    public ResponseEntity<?> createPeriod(Authentication auth, HttpServletRequest req, @RequestBody Map<String,Object> f) {
        var a = actor(auth);
        return ResponseEntity.status(201).body(writes.write(a, req, f, () -> catalog.createPeriod(a, f)));
    }
}
