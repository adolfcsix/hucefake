package vn.huce.repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;
import vn.huce.model.ScheduleFilter;

import static vn.huce.repository.SessionQueries.STAFF_IDS;

@Repository
public class SessionReadRepository {
    private static final String SESSION_SQL = """
            SELECT x.id,x.section_id,x.staff_id,x.room_id,x.period_from,x.period_count,x.status,x.source_session_id,
              to_char(x.start_at,'YYYY-MM-DD"T"HH24:MI:SS') AS start_at,to_char(x.end_at,'YYYY-MM-DD"T"HH24:MI:SS') AS end_at,
              c.code AS section_code,q.name AS subject_name,t.code AS room_code,sp.code AS staff_code,
              q.managing_unit_id,t.campus_id,c.status AS section_status,e.reason AS exception_reason
            FROM class_sessions x JOIN course_sections c ON c.id=x.section_id JOIN subjects q ON q.id=c.subject_id
            JOIN staff_profiles sp ON sp.id=x.staff_id LEFT JOIN teaching_rooms t ON t.id=x.room_id
            LEFT JOIN session_exceptions e ON e.session_id=x.id WHERE c.semester_id=?
            """;
    private final SqlStore store;

    public SessionReadRepository(SqlStore store) {
        this.store = store;
    }

    private record Query(String sql, List<Object> args) {
    }

    private Query scoped(Actor actor, ScheduleFilter filter) {
        Date start = filter.from() == null ? null : Date.valueOf(filter.from());
        Date end = filter.to() == null ? null : Date.valueOf(filter.to());
        List<Object> args = new ArrayList<>(Arrays.asList(filter.semesterId(), start, start, end, end));
        String sql = SESSION_SQL + " AND (?::date IS NULL OR x.start_at>=?::date) AND (?::date IS NULL OR x.start_at<?::date+1)";
        if ("STUDENT".equals(actor.role())) {
            sql += " AND EXISTS(SELECT 1 FROM section_enrollments en WHERE en.section_id=c.id AND en.student_user_id=? AND en.status='ENROLLED')";
            args.add(actor.id());
        } else if ("LECTURER".equals(actor.role())) {
            sql += " AND ?=ANY(" + STAFF_IDS + ")";
            args.add(actor.staffId());
        }
        String condition = switch (filter.view()) {
            case ROOM -> " AND x.room_id=?";
            case SECTION -> " AND x.section_id=?";
            case STAFF -> " AND ?=ANY(" + STAFF_IDS + ")";
            case GROUP -> " AND EXISTS(SELECT 1 FROM section_target_groups g WHERE g.section_id=c.id AND g.group_id=?)";
            case PERSONAL, ALL -> "";
        };
        if (!condition.isEmpty()) {
            sql += condition;
            args.add(filter.targetId());
        }
        return new Query(sql, args);
    }

    public Object schedule(Actor actor, ScheduleFilter filter, Map<String, String> page, boolean nextOnly) {
        Query query = scoped(actor, filter);
        String sql = query.sql();
        if (nextOnly) {
            sql = "SELECT * FROM (" + sql + ") scoped WHERE scoped.status='PLANNED' AND scoped.start_at::timestamp > LOCALTIMESTAMP";
        }
        return store.paged(page, sql, query.args(), "start_at,section_code,id");
    }

    public List<Map<String, Object>> sections(Actor actor, ScheduleFilter filter) {
        Query query = scoped(actor, filter);
        return store.rows("SELECT DISTINCT section_id AS id,section_code AS code,subject_name AS name FROM ("
                + query.sql() + ") scoped ORDER BY code,id", query.args().toArray());
    }

    public List<Map<String, Object>> roomStatistics(long semesterId) {
        return store.rows("""
                SELECT r.id,r.code,ca.code AS campus_code,count(*) AS sessions,sum(x.period_count) AS periods
                FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
                LEFT JOIN teaching_rooms r ON r.id=x.room_id LEFT JOIN campuses ca ON ca.id=r.campus_id
                WHERE c.semester_id=? AND x.status<>'CANCELLED'
                GROUP BY r.id,r.code,ca.code ORDER BY sessions DESC,r.code,ca.code
                """, semesterId);
    }

    public List<Map<String, Object>> lecturerStatistics(long semesterId) {
        return store.rows("""
                SELECT s.id,s.code,count(*) AS sessions,sum(x.period_count) AS periods
                FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
                JOIN staff_profiles s ON s.id=ANY(
                """ + STAFF_IDS + ") WHERE c.semester_id=? AND x.status<>'CANCELLED' GROUP BY s.id,s.code ORDER BY sessions DESC,s.code",
                semesterId);
    }
}
