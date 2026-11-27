package vn.huce.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.service.CatalogService;

@RestController
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return catalog.health();
    }

    @GetMapping("/api/catalog")
    public Map<String, Object> catalog(Authentication authentication) {
        return catalog.catalog((Actor) authentication.getPrincipal());
    }
}
