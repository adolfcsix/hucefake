package vn.huce.repository;

import java.sql.Timestamp;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;

import vn.huce.model.SchedulePatternSnapshot;
import vn.huce.model.PublishedSession;
import vn.huce.model.TeachingPeriod;
import vn.huce.model.NonTeachingDay;

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

    public List<TeachingPeriod> activePeriods() {
        return store.jdbc().query("""
                SELECT campus_id,period_no,start_time,end_time,shift,effective_from,effective_to
                FROM period_definitions WHERE status='ACTIVE' ORDER BY effective_from DESC
                """, (rs, row) -> new TeachingPeriod(rs.getLong("campus_id"), rs.getInt("period_no"),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                rs.getString("shift"), rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class)));
    }

    public List<Map<String, Object>> availability() {
        return store.rows("SELECT * FROM staff_availability ORDER BY id");
    }

    public List<NonTeachingDay> holidays() {
        return store.jdbc().query("SELECT campus_id,start_date,end_date FROM non_teaching_days",
                (rs, row) -> new NonTeachingDay(rs.getObject("campus_id", Long.class),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)));
    }

    public List<SchedulePatternSnapshot> draftPatterns() {
        return store.jdbc().query("SELECT " + SchedulePatternRepository.COLUMNS + """
                FROM section_schedule_patterns p JOIN course_sections c ON c.id=p.section_id
                WHERE c.status='DRAFT' ORDER BY p.id
                """, (rs, row) -> SchedulePatternRepository.snapshot(rs, null));
    }

    public Set<Long> allowedCampuses(Actor actor) {
        List<Long> rows = "STAFF".equals(actor.role())
                ? store.jdbc().query("SELECT campus_id AS id FROM staff_campuses JOIN campuses c ON c.id=campus_id WHERE staff_id=? AND c.status='ACTIVE'",
                        (rs, row) -> rs.getLong("id"), actor.staffId())
                : store.jdbc().query("SELECT id FROM campuses WHERE status='ACTIVE'",
                        (rs, row) -> rs.getLong("id"));
        return new LinkedHashSet<>(rows);
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

    public List<PublishedSession> publishedSessions(LocalDate from, LocalDate to) {
        return store.jdbc().query("""
          SELECT x.section_id,x.room_id,x.start_at,x.end_at,
          ARRAY[x.staff_id] || ARRAY(SELECT a.staff_id FROM section_assignments a WHERE a.section_id=x.section_id AND a.role='ASSISTANT' AND a.valid_to IS NULL) AS staff_ids,
          ARRAY(SELECT g.group_id FROM section_target_groups g WHERE g.section_id=x.section_id) AS group_ids
          FROM class_sessions x WHERE x.start_at<?::date+1 AND x.end_at>=?::date AND x.status<>'CANCELLED'
          """, (rs, row) -> new PublishedSession(rs.getLong("section_id"),
                rs.getObject("room_id", Long.class), rs.getTimestamp("start_at").toLocalDateTime(),
                rs.getTimestamp("end_at").toLocalDateTime(), readIds(rs, "staff_ids"),
                readIds(rs, "group_ids")), Date.valueOf(to), Date.valueOf(from));
    }

    private static Set<Long> readIds(ResultSet rs, String column) throws SQLException {
        var array = rs.getArray(column);
        if (array == null) {
            return new LinkedHashSet<>();
        }
        try {
            return new LinkedHashSet<>(Arrays.asList((Long[]) array.getArray()));
        } finally {
            array.free();
        }
    }
}
