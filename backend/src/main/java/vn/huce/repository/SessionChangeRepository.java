package vn.huce.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.model.SessionChangeAction;
import vn.huce.model.SessionRequestStatus;
import vn.huce.model.SessionRequestSnapshot;
import vn.huce.model.SessionRequestResolution;
import vn.huce.exception.ApiFailure;

import static vn.huce.repository.SessionQueries.STAFF_IDS;

@Repository
public class SessionChangeRepository {
    private final SqlStore store;

    public SessionChangeRepository(SqlStore store) {
        this.store = store;
    }

    public Map<String, Object> findSession(long sessionId) {
        return store.need("""
                SELECT x.*,r.campus_id FROM class_sessions x
                LEFT JOIN teaching_rooms r ON r.id=x.room_id WHERE x.id=?
                """, sessionId);
    }

    public boolean hasException(long sessionId) {
        return store.exists("SELECT 1 FROM session_exceptions WHERE session_id=?", sessionId);
    }

    public Map<String, Object> semesterDates(long semesterId) {
        return store.need("SELECT start_date,end_date FROM semesters WHERE id=?", semesterId);
    }

    public long insertReplacement(long sectionId, Long staffId, long roomId, int periodFrom, int periodCount,
                                  LocalDateTime start, LocalDateTime end, Object versionId, long sourceId) {
        return ((Number) store.need("""
                INSERT INTO class_sessions(section_id,staff_id,room_id,period_from,period_count,start_at,end_at,
                    timetable_version_id,source_session_id)
                VALUES (?,?,?,?,?,?,?,?,?) RETURNING id
                """, sectionId, staffId, roomId, periodFrom, periodCount, Timestamp.valueOf(start),
                Timestamp.valueOf(end), versionId, sourceId).get("id")).longValue();
    }

    public Map<String, Object> insertException(long sessionId, LocalDate occurrenceDate, SessionChangeAction action,
                                                Long replacementId, String reason, long actorId) {
        return store.need("""
                INSERT INTO session_exceptions(session_id,occurrence_date,action,replacement_id,reason,created_by)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, sessionId, Date.valueOf(occurrenceDate), action.name(), replacementId, reason, actorId);
    }

    public void cancelSession(long sessionId) {
        store.jdbc().update("UPDATE class_sessions SET status='CANCELLED',version=version+1 WHERE id=?", sessionId);
    }

    public List<SessionRequestResolution> closePendingRequests(long sessionId, SessionChangeAction action) {
        return store.jdbc().query("""
                UPDATE session_change_requests SET status=CASE WHEN action=? THEN 'APPROVED' ELSE 'RETURNED' END,
                decision_reason=CASE WHEN action=? THEN 'Đề nghị đã được xử lý bằng điều chỉnh trực tiếp. Xem lại thời khóa biểu.'
                    ELSE 'Buổi học có điều chỉnh trực tiếp khác với đề nghị. Xem lại thời khóa biểu.' END
                WHERE session_id=? AND status='PENDING' RETURNING id,staff_id,status,decision_reason
                """, (rs, row) -> new SessionRequestResolution(rs.getLong("id"),
                rs.getLong("staff_id"), SessionRequestStatus.valueOf(rs.getString("status")),
                rs.getString("decision_reason")), action.name(), action.name(), sessionId);
    }

    public Object findRequests(Map<String, String> query, long semesterId, boolean staff, Long unitId, Long staffId) {
        String sql = """
                SELECT r.*,c.code AS section_code,s.name AS subject_name,
                to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS session_time,to_char(x.end_at,'YYYY-MM-DD HH24:MI') AS end_at,
                x.period_from,x.room_id,room.code AS room_code
                FROM session_change_requests r JOIN class_sessions x ON x.id=r.session_id
                JOIN course_sections c ON c.id=x.section_id JOIN subjects s ON s.id=c.subject_id
                LEFT JOIN teaching_rooms room ON room.id=x.room_id WHERE c.semester_id=? AND
                """;
        List<Object> args = new ArrayList<>();
        args.add(semesterId);
        if (staff) {
            sql += """
                     s.managing_unit_id=? AND (room.campus_id IS NULL OR EXISTS(
                       SELECT 1 FROM staff_campuses sc JOIN campuses ca ON ca.id=sc.campus_id
                       WHERE sc.campus_id=room.campus_id AND sc.staff_id=? AND ca.status='ACTIVE'))
                    """;
            args.add(unitId);
            args.add(staffId);
        } else {
            sql += " r.staff_id=?";
            args.add(staffId);
        }
        return store.paged(query, sql, args, "id DESC");
    }

    public Map<String, Object> findSessionWithStaff(long sessionId) {
        return store.need("SELECT x.*," + STAFF_IDS + " AS staff_ids FROM class_sessions x WHERE x.id=?", sessionId);
    }

    public boolean hasPendingRequest(long sessionId) {
        return store.exists("SELECT 1 FROM session_change_requests WHERE session_id=? AND status='PENDING'", sessionId);
    }

    public Map<String, Object> insertRequest(long sessionId, long staffId, SessionChangeAction action, String reason) {
        return store.need("""
                INSERT INTO session_change_requests(session_id,staff_id,action,reason) VALUES (?,?,?,?) RETURNING id
                """, sessionId, staffId, action.name(), reason);
    }

    public SessionRequestSnapshot findRequest(long requestId) {
        List<SessionRequestSnapshot> rows = store.jdbc().query("""
                SELECT r.id,r.session_id,r.staff_id,r.status,r.action,s.managing_unit_id,room.campus_id
                FROM session_change_requests r
                JOIN class_sessions x ON x.id=r.session_id JOIN course_sections c ON c.id=x.section_id
                JOIN subjects s ON s.id=c.subject_id LEFT JOIN teaching_rooms room ON room.id=x.room_id WHERE r.id=?
                """, (rs, row) -> new SessionRequestSnapshot(rs.getLong("id"), rs.getLong("session_id"),
                rs.getLong("staff_id"), rs.getObject("managing_unit_id", Long.class),
                rs.getObject("campus_id", Long.class), SessionRequestStatus.valueOf(rs.getString("status")),
                SessionChangeAction.valueOf(rs.getString("action"))), requestId);
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public void decideRequest(long requestId, SessionRequestStatus status, String reason) {
        store.jdbc().update("UPDATE session_change_requests SET status=?,decision_reason=? WHERE id=?",
                status.name(), reason, requestId);
    }
}
