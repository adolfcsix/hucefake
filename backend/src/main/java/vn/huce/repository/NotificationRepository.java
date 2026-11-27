package vn.huce.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
}
