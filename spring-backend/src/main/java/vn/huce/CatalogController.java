package vn.huce;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
class CatalogController {
    private final JdbcTemplate db;
    CatalogController(JdbcTemplate db){this.db=db;}
    @GetMapping("/health") Map<String,String> health(){db.queryForObject("SELECT 1",Integer.class);return Map.of("status","ok");}
    @GetMapping("/api/catalog") Map<String,Object> catalog(Authentication authentication){
        var actor=(Accounts.Actor)authentication.getPrincipal();
        var out=new LinkedHashMap<String,Object>();
        out.put("semesters",db.queryForList("SELECT * FROM semesters ORDER BY start_date DESC,id DESC"));
        if(!"STAFF".equals(actor.role()) && !"MANAGER".equals(actor.role()))return out;
        boolean manager="MANAGER".equals(actor.role());
        out.put("campuses",db.queryForList("""
          SELECT c.* FROM campuses c WHERE c.status='ACTIVE' AND (? OR EXISTS(SELECT 1 FROM staff_campuses x WHERE x.campus_id=c.id AND x.staff_id=?)) ORDER BY c.code
          """,manager,actor.staffId()));
        out.put("rooms",db.queryForList("SELECT * FROM teaching_rooms ORDER BY code"));
        out.put("lecturers",db.queryForList("""
          SELECT s.id,s.code,u.username FROM staff_profiles s JOIN users u ON u.id=s.user_id JOIN roles r ON r.role_id=u.role_id
          WHERE s.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER' ORDER BY s.code
          """));
        out.put("groups",db.queryForList("SELECT id,code,capacity FROM academic_groups ORDER BY code"));
        out.put("subjects",db.queryForList("SELECT id,code,name FROM subjects WHERE status='ACTIVE' AND (? OR managing_unit_id=?) ORDER BY code",manager,actor.unitId()));
        out.put("periods",db.queryForList("""
          SELECT id,campus_id,period_no,shift,effective_from,effective_to,to_char(start_time,'HH24:MI') AS start_time,
          to_char(end_time,'HH24:MI') AS end_time FROM period_definitions ORDER BY campus_id,period_no,effective_from DESC
          """));
        out.put("holidays",db.queryForList("SELECT * FROM non_teaching_days ORDER BY start_date DESC"));
        return out;
    }
}
