package vn.huce.repository;

import java.sql.Date;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.model.SectionDraft;

import static vn.huce.repository.SectionQueries.PATTERN_SQL;
import static vn.huce.repository.SectionQueries.SECTION_SQL;

@Repository
public class SectionRepository {
    private final SqlStore store;

    public SectionRepository(SqlStore store) {
        this.store = store;
    }

    public Object findSections(Map<String, String> page, long semesterId, Long unitId,
                               String status, Long subjectId, String search) {
        return store.paged(page, SECTION_SQL + """
                WHERE c.semester_id=? AND (?::bigint IS NULL OR s.managing_unit_id=?)
                AND (?::text IS NULL OR c.status=?) AND (?::bigint IS NULL OR c.subject_id=?)
                AND (c.code ILIKE ? OR s.name ILIKE ?)
                """, Arrays.asList(semesterId, unitId, unitId, status, status,
                subjectId, subjectId, search, search), "code,id");
    }

    public Object findPatterns(Map<String, String> page, long semesterId) {
        return store.paged(page, PATTERN_SQL + " WHERE c.semester_id=?",
                List.of(semesterId), "day_of_week,period_from,id");
    }

    public boolean hasSubmittedTimetable(long semesterId) {
        return store.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",
                semesterId);
    }

    public Map<String, Object> findSection(long sectionId) {
        return store.need(SECTION_SQL + " WHERE c.id=?", sectionId);
    }

    public Map<String, Object> findSemester(long semesterId) {
        return store.need("SELECT * FROM semesters WHERE id=?", semesterId);
    }

    public Map<String, Object> findSubject(long subjectId) {
        return store.need("SELECT * FROM subjects WHERE id=?", subjectId);
    }

    public boolean codeExists(long semesterId, String code) {
        return store.exists("SELECT 1 FROM course_sections WHERE semester_id=? AND code=?",
                semesterId, code);
    }

    public Map<String, Object> insertDraft(SectionDraft draft, long creatorId) {
        return store.need("""
                INSERT INTO course_sections(semester_id,subject_id,code,capacity,sessions_per_week,
                    periods_per_session,required_room_type,start_date,end_date,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, draft.semesterId(), draft.subjectId(), draft.code(), draft.capacity(),
                draft.sessionsPerWeek(), draft.periodsPerSession(), draft.requiredRoomType(),
                Date.valueOf(draft.startDate()), Date.valueOf(draft.endDate()), creatorId);
    }
}
