package vn.huce.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.CatalogRepository;

@Service
public class CatalogService {
    private final CatalogRepository catalog;

    public CatalogService(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    public Map<String, String> health() {
        if (!catalog.ready()) {
            throw new ApiFailure(503, "NOT_READY", "Hệ thống đang khởi tạo.");
        }
        return Map.of("status", "ok");
    }

    public Map<String, Object> catalog(Actor actor) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("semesters", catalog.semesters());
        boolean manager = "MANAGER".equals(actor.role());
        if (!manager && !"STAFF".equals(actor.role())) {
            return response;
        }
        response.put("campuses", catalog.campuses(manager, actor.staffId()));
        response.put("rooms", catalog.rooms());
        response.put("lecturers", catalog.lecturers());
        response.put("groups", catalog.groups());
        response.put("subjects", catalog.subjects(manager, actor.unitId()));
        response.put("periods", catalog.periods());
        response.put("holidays", catalog.holidays());
        return response;
    }
}
