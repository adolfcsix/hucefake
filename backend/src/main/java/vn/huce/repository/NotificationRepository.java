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
}
