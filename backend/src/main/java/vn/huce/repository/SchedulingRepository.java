package vn.huce.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;

import static vn.huce.validation.Inputs.n;

@Repository
public class SchedulingRepository {
    private final SqlStore store;

    public SchedulingRepository(SqlStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> activeSections() {
        return store.rows(SectionQueries.SECTION_SQL + " WHERE c.status IN ('OPEN','DRAFT') ORDER BY c.id");
    }

    public List<Map<String, Object>> rooms() {
        return store.rows("""
                SELECT r.id,r.campus_id,r.code,r.capacity,r.type,
                    CASE WHEN c.status='ACTIVE' THEN r.status ELSE 'INACTIVE' END AS status
                FROM teaching_rooms r JOIN campuses c ON c.id=r.campus_id ORDER BY r.id
                """);
    }

    public List<Map<String, Object>> activePeriods() {
        return store.rows("SELECT * FROM period_definitions WHERE status='ACTIVE' ORDER BY effective_from DESC");
    }

    public List<Map<String, Object>> availability() {
        return store.rows("SELECT * FROM staff_availability ORDER BY id");
    }

    public List<Map<String, Object>> holidays() {
        return store.rows("SELECT * FROM non_teaching_days");
    }

    public List<Map<String, Object>> draftPatterns() {
        return store.rows("""
                SELECT p.* FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
                WHERE c.status='DRAFT' ORDER BY p.id
                """);
    }

    public Set<Long> allowedCampuses(Actor actor) {
        List<Map<String, Object>> rows = "STAFF".equals(actor.role())
                ? store.rows("SELECT campus_id AS id FROM staff_campuses JOIN campuses c ON c.id=campus_id WHERE staff_id=? AND c.status='ACTIVE'", actor.staffId())
                : store.rows("SELECT id FROM campuses WHERE status='ACTIVE'");
        Set<Long> result = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            result.add(n(row, "id"));
        }
        return result;
    }

    public List<Map<String, Object>> actualConflicts(long sectionId, long roomId, Set<Long> staffIds,
                                                     LocalDateTime start, LocalDateTime end, long excludedSessionId) {
        String staffArray = "{" + String.join(",", staffIds.stream().map(String::valueOf).toList()) + "}";
        return store.rows("""
              SELECT x.id,c.code AS section_code,r.code AS "roomCode",x.period_from AS "periodFrom",x.period_count AS "periodCount",
                to_char(x.start_at,'YYYY-MM-DD"T"HH24:MI:SS') AS start_at,
                ARRAY(SELECT g.code FROM section_target_groups a JOIN section_target_groups b USING(group_id) JOIN academic_groups g ON g.id=a.group_id
                  WHERE a.section_id=x.section_id AND b.section_id=?) AS "groupCodes",
                CASE WHEN x.room_id=? THEN 'BR04'
                  WHEN (ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL)) && ?::bigint[] THEN 'BR05' ELSE 'BR06' END AS rule
              FROM class_sessions x JOIN course_sections c ON c.id=x.section_id LEFT JOIN teaching_rooms r ON r.id=x.room_id
              WHERE x.id<>? AND x.status<>'CANCELLED' AND x.start_at<? AND x.end_at>?
              AND (x.room_id=? OR (ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL)) && ?::bigint[]
                  OR x.section_id=? OR EXISTS(SELECT 1 FROM section_target_groups a JOIN section_target_groups b USING(group_id) WHERE a.section_id=x.section_id AND b.section_id=?))
              ORDER BY x.id
              """, sectionId, roomId, staffArray, excludedSessionId,
                Timestamp.valueOf(end), Timestamp.valueOf(start), roomId, staffArray, sectionId, sectionId);
    }
}
