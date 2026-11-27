package vn.huce.repository;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import vn.huce.exception.ApiFailure;
import vn.huce.model.PeriodRevision;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.model.HolidayDraft;
import vn.huce.model.PeriodDraft;
import vn.huce.model.RoomDraft;

@Repository
public class CatalogMutationRepository {
    private final SqlStore store;

    public CatalogMutationRepository(SqlStore store) {
        this.store = store;
    }

    private static <T> T require(List<T> rows) {
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public long roomCampus(long roomId) {
        return require(store.jdbc().query("SELECT campus_id FROM teaching_rooms WHERE id=?",
                (rs, row) -> rs.getLong("campus_id"), roomId));
    }

    public long insertRoom(RoomDraft draft) {
        return require(store.jdbc().query("""
                INSERT INTO teaching_rooms(campus_id,code,name,capacity,type,status)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.campusId(), draft.code(), draft.name(), draft.capacity(), draft.type(), draft.status()));
    }

    public long updateRoom(long roomId, RoomDraft draft) {
        return require(store.jdbc().query("""
                UPDATE teaching_rooms SET code=?,name=?,capacity=?,type=?,status=? WHERE id=? RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.code(), draft.name(), draft.capacity(), draft.type(), draft.status(), roomId));
    }

    public List<Map<String, Object>> affectedRoomSessions(long roomId, RoomDraft draft) {
        return store.rows("""
                SELECT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
                FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
                WHERE x.room_id=? AND x.status='PLANNED' AND x.start_at>=CURRENT_DATE
                AND (?<>'ACTIVE' OR c.capacity>? OR c.required_room_type<>?)
                """, roomId, draft.status(), draft.capacity(), draft.type());
    }

    public List<Map<String, Object>> affectedRoomPatterns(long roomId, RoomDraft draft) {
        return store.rows("""
                SELECT p.id,c.code AS section_code FROM section_schedule_patterns p
                JOIN course_sections c ON c.id=p.section_id
                WHERE p.room_id=? AND c.status='DRAFT' AND (?<>'ACTIVE' OR c.capacity>? OR c.required_room_type<>?)
                """, roomId, draft.status(), draft.capacity(), draft.type());
    }

    public long activeCampusCount() {
        return store.jdbc().queryForObject("SELECT count(*) FROM campuses WHERE status='ACTIVE'", Long.class);
    }

    public Long holidayCampus(long holidayId) {
        return require(store.jdbc().query("SELECT campus_id FROM non_teaching_days WHERE id=?",
                (rs, row) -> rs.getObject("campus_id", Long.class), holidayId));
    }

    public List<Map<String, Object>> affectedHolidaySessions(HolidayDraft draft) {
        return store.rows("""
                SELECT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
                FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
                LEFT JOIN teaching_rooms r ON r.id=x.room_id
                WHERE (?::bigint IS NULL OR r.campus_id=?) AND x.status='PLANNED'
                AND x.start_at>=?::date AND x.start_at<?::date+1
                """, draft.campusId(), draft.campusId(), Date.valueOf(draft.startDate()), Date.valueOf(draft.endDate()));
    }

    public long insertHoliday(HolidayDraft draft, long actorId) {
        return require(store.jdbc().query("""
                INSERT INTO non_teaching_days(campus_id,start_date,end_date,name,created_by)
                VALUES (?,?,?,?,?) RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.campusId(), Date.valueOf(draft.startDate()), Date.valueOf(draft.endDate()), draft.name(), actorId));
    }

    public long updateHoliday(long holidayId, HolidayDraft draft) {
        return require(store.jdbc().query("""
                UPDATE non_teaching_days SET campus_id=?,start_date=?,end_date=?,name=? WHERE id=? RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.campusId(), Date.valueOf(draft.startDate()), Date.valueOf(draft.endDate()), draft.name(), holidayId));
    }

    public PeriodRevision latestPeriod(PeriodDraft draft) {
        List<PeriodRevision> rows = store.jdbc().query("""
                SELECT id,effective_from FROM period_definitions WHERE campus_id=? AND period_no=?
                ORDER BY effective_from DESC LIMIT 1
                """, (rs, row) -> new PeriodRevision(rs.getLong("id"),
                rs.getObject("effective_from", LocalDate.class)), draft.campusId(), draft.periodNumber());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<Map<String, Object>> affectedPeriodSessions(PeriodDraft draft) {
        return store.rows("""
                SELECT x.id,c.code AS section_code FROM class_sessions x
                JOIN teaching_rooms t ON t.id=x.room_id JOIN course_sections c ON c.id=x.section_id
                WHERE t.campus_id=? AND x.status='PLANNED' AND x.start_at>=?::date
                AND ? BETWEEN x.period_from AND x.period_from+x.period_count-1
                """, draft.campusId(), Date.valueOf(draft.effectiveFrom()), draft.periodNumber());
    }

    public boolean overlapsOtherPeriods(PeriodDraft draft) {
        return store.exists("""
                SELECT 1 FROM period_definitions WHERE campus_id=? AND period_no<>? AND status='ACTIVE'
                AND (effective_to IS NULL OR effective_to>=?::date)
                AND ((period_no<? AND end_time>?::time) OR (period_no>? AND start_time<?::time))
                """, draft.campusId(), draft.periodNumber(), Date.valueOf(draft.effectiveFrom()),
                draft.periodNumber(), Time.valueOf(draft.startTime()), draft.periodNumber(), Time.valueOf(draft.endTime()));
    }

    public void closePeriod(long periodId, PeriodDraft replacement) {
        store.jdbc().update("UPDATE period_definitions SET effective_to=?::date-1 WHERE id=?",
                Date.valueOf(replacement.effectiveFrom()), periodId);
    }

    public long insertPeriod(PeriodDraft draft) {
        return require(store.jdbc().query("""
                INSERT INTO period_definitions(campus_id,period_no,start_time,end_time,shift,effective_from)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.campusId(), draft.periodNumber(), Time.valueOf(draft.startTime()),
                Time.valueOf(draft.endTime()), draft.shift(), Date.valueOf(draft.effectiveFrom())));
    }
}
