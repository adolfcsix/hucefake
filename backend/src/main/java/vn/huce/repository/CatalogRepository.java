package vn.huce.repository;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final SqlStore store;

    public CatalogRepository(SqlStore store) {
        this.store = store;
    }

    public boolean ready() {
        return store.exists("SELECT 1 FROM fm05_runtime_mode WHERE singleton=true");
    }

    public List<Map<String, Object>> semesters() {
        return store.rows("SELECT * FROM semesters ORDER BY start_date DESC,id DESC");
    }

    public List<Map<String, Object>> campuses(boolean manager, Long staffId) {
        return store.rows("""
                SELECT c.* FROM campuses c WHERE c.status='ACTIVE'
                AND (? OR EXISTS(SELECT 1 FROM staff_campuses x WHERE x.campus_id=c.id AND x.staff_id=?))
                ORDER BY c.code
                """, manager, staffId);
    }

    public List<Map<String, Object>> rooms() {
        return store.rows("SELECT * FROM teaching_rooms ORDER BY code");
    }

    public List<Map<String, Object>> lecturers() {
        return store.rows("""
                SELECT s.id,s.code,u.username FROM staff_profiles s
                JOIN users u ON u.id=s.user_id JOIN roles r ON r.role_id=u.role_id
                WHERE s.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER' ORDER BY s.code
                """);
    }

    public List<Map<String, Object>> groups() {
        return store.rows("SELECT id,code,capacity FROM academic_groups ORDER BY code");
    }

    public List<Map<String, Object>> subjects(boolean manager, Long unitId) {
        return store.rows("SELECT id,code,name FROM subjects WHERE status='ACTIVE' AND (? OR managing_unit_id=?) ORDER BY code",
                manager, unitId);
    }

    public List<Map<String, Object>> periods() {
        return store.rows("""
                SELECT id,campus_id,period_no,shift,effective_from,effective_to,to_char(start_time,'HH24:MI') AS start_time,
                to_char(end_time,'HH24:MI') AS end_time FROM period_definitions ORDER BY campus_id,period_no,effective_from DESC
                """);
    }

    public List<Map<String, Object>> holidays() {
        return store.rows("SELECT * FROM non_teaching_days ORDER BY start_date DESC");
    }
}
