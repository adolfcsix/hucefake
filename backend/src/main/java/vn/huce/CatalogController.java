package vn.huce;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
class CatalogController {
    private final SqlStore store;
    CatalogController(SqlStore store){this.store=store;}
    @GetMapping("/health") Map<String,String> health(){if(!store.exists("SELECT 1 FROM fm05_runtime_mode WHERE singleton=true"))throw new ApiFailure(503,"NOT_READY","Hệ thống đang khởi tạo.");return Map.of("status","ok");}
    @GetMapping("/api/catalog") Map<String,Object> catalog(Authentication authentication){
        var actor=(Accounts.Actor)authentication.getPrincipal();
        var out=new LinkedHashMap<String,Object>();
        out.put("semesters",store.rows("SELECT * FROM semesters ORDER BY start_date DESC,id DESC"));
        if(!"STAFF".equals(actor.role()) && !"MANAGER".equals(actor.role()))return out;
        boolean manager="MANAGER".equals(actor.role());
        out.put("campuses",store.rows("""
          SELECT c.* FROM campuses c WHERE c.status='ACTIVE' AND (? OR EXISTS(SELECT 1 FROM staff_campuses x WHERE x.campus_id=c.id AND x.staff_id=?)) ORDER BY c.code
          """,manager,actor.staffId()));
        out.put("rooms",store.rows("SELECT * FROM teaching_rooms ORDER BY code"));
        out.put("lecturers",store.rows("""
          SELECT s.id,s.code,u.username FROM staff_profiles s JOIN users u ON u.id=s.user_id JOIN roles r ON r.role_id=u.role_id
          WHERE s.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER' ORDER BY s.code
          """));
        out.put("groups",store.rows("SELECT id,code,capacity FROM academic_groups ORDER BY code"));
        out.put("subjects",store.rows("SELECT id,code,name FROM subjects WHERE status='ACTIVE' AND (? OR managing_unit_id=?) ORDER BY code",manager,actor.unitId()));
        out.put("periods",store.rows("""
          SELECT id,campus_id,period_no,shift,effective_from,effective_to,to_char(start_time,'HH24:MI') AS start_time,
          to_char(end_time,'HH24:MI') AS end_time FROM period_definitions ORDER BY campus_id,period_no,effective_from DESC
          """));
        out.put("holidays",store.rows("SELECT * FROM non_teaching_days ORDER BY start_date DESC"));
        return out;
    }
}
