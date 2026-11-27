package vn.huce.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;
    private final SqlStore store;

    public NotificationRepository(JdbcTemplate jdbc, SqlStore store) {
        this.jdbc = jdbc;
        this.store = store;
    }

    public void notifyManagers(String message) {
        jdbc.update("""
                INSERT INTO notifications(user_id,message) SELECT u.id,? FROM users u
                JOIN roles r ON r.role_id=u.role_id WHERE u.status='ACTIVE' AND r.code='MANAGER'
                """, message);
    }

    public void notifyUser(long userId, String message) {
        jdbc.update("INSERT INTO notifications(user_id,message) VALUES (?,?)", userId, message);
    }

    public void notifySection(long sectionId, String message) {
        jdbc.update("""
                INSERT INTO notifications(user_id,message) SELECT recipient,? FROM (
                    SELECT p.user_id AS recipient FROM section_assignments a
                    JOIN staff_profiles p ON p.id=a.staff_id WHERE a.section_id=? AND a.valid_to IS NULL
                    UNION SELECT student_user_id FROM section_enrollments WHERE section_id=? AND status='ENROLLED'
                ) audience
                """, message, sectionId, sectionId);
    }

    public void notifyStaffProfile(long staffId, String message) {
        jdbc.update("INSERT INTO notifications(user_id,message) SELECT user_id,? FROM staff_profiles WHERE id=?",
                message, staffId);
    }

    public void notifyScopedStaff(Object unitId, Object roomId, String message) {
        jdbc.update("""
                INSERT INTO notifications(user_id,message)
                SELECT p.user_id,? FROM staff_profiles p JOIN users u ON u.id=p.user_id
                JOIN roles r ON r.role_id=u.role_id
                WHERE p.unit_id=? AND p.status='ACTIVE' AND u.status='ACTIVE' AND r.code='STAFF'
                AND (?::bigint IS NULL OR EXISTS(
                    SELECT 1 FROM teaching_rooms t JOIN staff_campuses sc ON sc.campus_id=t.campus_id
                    JOIN campuses c ON c.id=t.campus_id WHERE t.id=? AND sc.staff_id=p.id AND c.status='ACTIVE'))
                """, message, unitId, roomId, roomId);
    }

    public Object findForUser(long userId, Map<String, String> page) {
        String sql = "SELECT * FROM notifications WHERE user_id=?";
        if (!page.containsKey("page") && !page.containsKey("pageSize")) {
            return store.rows(sql + " ORDER BY id DESC LIMIT 100", userId);
        }
        return store.paged(page, sql, List.of(userId), "id DESC");
    }

    public int markAll(long userId) {
        return jdbc.update("UPDATE notifications SET read_at=now() WHERE user_id=? AND read_at IS NULL", userId);
    }

    public void markRead(long userId, long notificationId) {
        jdbc.update("UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE id=? AND user_id=?",
                notificationId, userId);
    }

    public Map<String, Object> unreadSummary(long userId) {
        return store.need("SELECT count(*) AS count,max(id) AS last_id FROM notifications WHERE user_id=? AND read_at IS NULL", userId);
    }

    public int markThrough(long userId, long lastId) {
        return jdbc.update("UPDATE notifications SET read_at=now() WHERE user_id=? AND read_at IS NULL AND id<=?", userId, lastId);
    }
}
