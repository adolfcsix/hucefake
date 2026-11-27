package vn.huce.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.huce.exception.ApiFailure;
import vn.huce.model.PatternDraft;
import vn.huce.model.SchedulePatternSnapshot;

@Repository
public class SchedulePatternRepository {
    private final SqlStore store;

    public SchedulePatternRepository(SqlStore store) {
        this.store = store;
    }

    static final String COLUMNS = """
            p.id,p.section_id,p.staff_id,p.room_id,p.day_of_week,p.period_from,
            p.period_count,p.effective_from,p.effective_to,p.source,p.run_id,
            p.created_by,p.created_at,p.version
            """;

    public SchedulePatternSnapshot find(long patternId) {
        return require(store.jdbc().query("SELECT " + COLUMNS
                + " FROM section_schedule_patterns p WHERE p.id=?",
                (rs, row) -> snapshot(rs, null), patternId));
    }

    public SchedulePatternSnapshot findWithCampus(long patternId) {
        return require(store.jdbc().query("SELECT " + COLUMNS + """
                ,r.campus_id FROM section_schedule_patterns p
                JOIN teaching_rooms r ON r.id=p.room_id WHERE p.id=?
                """, (rs, row) -> snapshot(rs, rs.getObject("campus_id", Long.class)), patternId));
    }

    static SchedulePatternSnapshot snapshot(ResultSet rs, Long campusId) throws SQLException {
        return new SchedulePatternSnapshot(rs.getLong("id"), rs.getLong("section_id"),
                rs.getLong("staff_id"), rs.getLong("room_id"), rs.getInt("day_of_week"),
                rs.getInt("period_from"), rs.getInt("period_count"),
                rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                rs.getString("source"), rs.getObject("run_id", Long.class), rs.getLong("created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getInt("version"), campusId);
    }

    public long insert(PatternDraft draft, long actorId) {
        return require(store.jdbc().query("""
                INSERT INTO section_schedule_patterns(section_id,staff_id,room_id,day_of_week,
                    period_from,period_count,effective_from,effective_to,source,created_by)
                VALUES (?,?,?,?,?,?,?,?,'MANUAL',?) RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.sectionId(), draft.staffId(),
                draft.roomId(), draft.dayOfWeek(), draft.periodFrom(), draft.periodCount(),
                Date.valueOf(draft.effectiveFrom()), Date.valueOf(draft.effectiveTo()), actorId));
    }

    public long update(long patternId, PatternDraft draft) {
        return require(store.jdbc().query("""
                UPDATE section_schedule_patterns SET room_id=?,day_of_week=?,period_from=?,
                    effective_from=?,effective_to=?,source='MANUAL',run_id=NULL,version=version+1
                WHERE id=? RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.roomId(), draft.dayOfWeek(),
                draft.periodFrom(), Date.valueOf(draft.effectiveFrom()),
                Date.valueOf(draft.effectiveTo()), patternId));
    }

    private static <T> T require(List<T> rows) {
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public void delete(long patternId) {
        store.jdbc().update("DELETE FROM section_schedule_patterns WHERE id=?", patternId);
    }
}
