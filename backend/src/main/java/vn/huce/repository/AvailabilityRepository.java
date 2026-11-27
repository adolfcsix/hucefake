package vn.huce.repository;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.model.AvailabilityEntry;

import static vn.huce.repository.SessionQueries.STAFF_IDS;

@Repository
public class AvailabilityRepository {
    private final SqlStore store;

    public AvailabilityRepository(SqlStore store) {
        this.store = store;
    }

    public List<Map<String, Object>> find(long staffId, long semesterId) {
        return store.rows("""
                SELECT * FROM staff_availability WHERE staff_id=? AND semester_id=?
                ORDER BY day_of_week,period_from,id
                """, staffId, semesterId);
    }

    public void replace(long staffId, long semesterId, List<AvailabilityEntry> entries) {
        store.jdbc().update("DELETE FROM staff_availability WHERE staff_id=? AND semester_id=?",
                staffId, semesterId);
        for (AvailabilityEntry entry : entries) {
            store.jdbc().update("""
                    INSERT INTO staff_availability(staff_id,semester_id,day_of_week,period_from,period_to,kind,reason)
                    VALUES (?,?,?,?,?,?,?)
                    """, staffId, semesterId, entry.dayOfWeek(), entry.periodFrom(), entry.periodTo(),
                    entry.kind().name(), entry.reason());
        }
    }

    public List<Map<String, Object>> findConflictingSessions(long staffId, long semesterId) {
        return store.rows("""
                SELECT DISTINCT x.id,c.code AS section_code,to_char(x.start_at,'YYYY-MM-DD HH24:MI') AS start_at
                FROM class_sessions x JOIN course_sections c ON c.id=x.section_id
                JOIN staff_availability a ON a.staff_id=? AND a.semester_id=c.semester_id AND a.kind='BUSY'
                    AND a.day_of_week=EXTRACT(ISODOW FROM x.start_at)
                    AND a.period_from<=x.period_from+x.period_count-1 AND a.period_to>=x.period_from
                WHERE ?=ANY(
                """ + STAFF_IDS + ") AND c.semester_id=? AND x.status='PLANNED' AND x.start_at>=CURRENT_DATE",
                staffId, staffId, semesterId);
    }
}
