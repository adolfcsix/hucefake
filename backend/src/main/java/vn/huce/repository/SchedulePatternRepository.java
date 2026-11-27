package vn.huce.repository;

import java.sql.Date;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.model.PatternDraft;

@Repository
public class SchedulePatternRepository {
    private final SqlStore store;

    public SchedulePatternRepository(SqlStore store) {
        this.store = store;
    }

    public Map<String, Object> find(long patternId) {
        return store.need("SELECT * FROM section_schedule_patterns WHERE id=?", patternId);
    }

    public Map<String, Object> findWithCampus(long patternId) {
        return store.need("""
                SELECT p.*,r.campus_id FROM section_schedule_patterns p
                JOIN teaching_rooms r ON r.id=p.room_id WHERE p.id=?
                """, patternId);
    }

    public Map<String, Object> insert(PatternDraft draft, long actorId) {
        return store.need("""
                INSERT INTO section_schedule_patterns(section_id,staff_id,room_id,day_of_week,
                    period_from,period_count,effective_from,effective_to,source,created_by)
                VALUES (?,?,?,?,?,?,?,?,'MANUAL',?) RETURNING id
                """, draft.sectionId(), draft.staffId(), draft.roomId(), draft.dayOfWeek(),
                draft.periodFrom(), draft.periodCount(), Date.valueOf(draft.effectiveFrom()),
                Date.valueOf(draft.effectiveTo()), actorId);
    }

    public Map<String, Object> update(long patternId, PatternDraft draft) {
        return store.need("""
                UPDATE section_schedule_patterns SET room_id=?,day_of_week=?,period_from=?,
                    effective_from=?,effective_to=?,source='MANUAL',run_id=NULL,version=version+1
                WHERE id=? RETURNING id
                """, draft.roomId(), draft.dayOfWeek(), draft.periodFrom(),
                Date.valueOf(draft.effectiveFrom()), Date.valueOf(draft.effectiveTo()), patternId);
    }

    public void delete(long patternId) {
        store.jdbc().update("DELETE FROM section_schedule_patterns WHERE id=?", patternId);
    }
}
