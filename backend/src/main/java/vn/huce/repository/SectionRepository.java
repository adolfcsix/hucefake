package vn.huce.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SectionSemester;
import vn.huce.model.SectionSubject;
import vn.huce.model.SectionDraft;

import static vn.huce.repository.SectionQueries.PATTERN_SQL;
import static vn.huce.repository.SectionQueries.SECTION_SQL;

@Repository
public class SectionRepository {
    private static final RowMapper<SectionSemester> SEMESTER_MAPPER = (rs, row) ->
            new SectionSemester(rs.getLong("id"), rs.getString("code"), rs.getString("status"),
                    rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class));
    private static final RowMapper<SectionSubject> SUBJECT_MAPPER = (rs, row) ->
            new SectionSubject(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                    rs.getString("status"), rs.getObject("managing_unit_id", Long.class));
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

    public SectionSemester findSemester(long semesterId) {
        return require(store.jdbc().query(
                "SELECT id,code,status,start_date,end_date FROM semesters WHERE id=?",
                SEMESTER_MAPPER, semesterId));
    }

    public SectionSubject findSubject(long subjectId) {
        return require(store.jdbc().query(
                "SELECT id,code,name,status,managing_unit_id FROM subjects WHERE id=?",
                SUBJECT_MAPPER, subjectId));
    }

    private static <T> T require(List<T> rows) {
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public boolean codeExists(long semesterId, String code) {
        return store.exists("SELECT 1 FROM course_sections WHERE semester_id=? AND code=?",
                semesterId, code);
    }

    public long insertDraft(SectionDraft draft, long creatorId) {
        return require(store.jdbc().query("""
                INSERT INTO course_sections(semester_id,subject_id,code,capacity,sessions_per_week,
                    periods_per_session,required_room_type,start_date,end_date,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """, (rs, row) -> rs.getLong("id"), draft.semesterId(), draft.subjectId(),
                draft.code(), draft.capacity(), draft.sessionsPerWeek(), draft.periodsPerSession(),
                draft.requiredRoomType(), Date.valueOf(draft.startDate()),
                Date.valueOf(draft.endDate()), creatorId));
    }

    public SectionSubject findActiveSubjectByCode(Long unitId, String code) {
        List<SectionSubject> rows = store.jdbc().query("""
                SELECT id,code,name,status,managing_unit_id FROM subjects WHERE managing_unit_id=? AND status='ACTIVE'
                AND (code=? OR code=?) ORDER BY CASE WHEN code=? THEN 0 ELSE 1 END LIMIT 1
                """, SUBJECT_MAPPER, unitId, code, code.toUpperCase(Locale.ROOT), code);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public boolean hasIncompatiblePatterns(long sectionId, SectionDraft draft) {
        return store.exists("""
                SELECT 1 FROM section_schedule_patterns p JOIN teaching_rooms r ON r.id=p.room_id
                WHERE p.section_id=? AND (p.period_count<>? OR p.effective_from<?
                    OR p.effective_to>? OR r.capacity<? OR r.type<>?)
                """, sectionId, draft.periodsPerSession(), Date.valueOf(draft.startDate()),
                Date.valueOf(draft.endDate()), draft.capacity(), draft.requiredRoomType());
    }

    public void updateDraft(long sectionId, SectionDraft draft) {
        store.jdbc().update("""
                UPDATE course_sections SET subject_id=?,code=?,capacity=?,sessions_per_week=?,
                    periods_per_session=?,required_room_type=?,start_date=?,end_date=?,
                    version=version+1,updated_at=now() WHERE id=?
                """, draft.subjectId(), draft.code(), draft.capacity(), draft.sessionsPerWeek(),
                draft.periodsPerSession(), draft.requiredRoomType(), Date.valueOf(draft.startDate()),
                Date.valueOf(draft.endDate()), sectionId);
    }

    public void cancelDraft(long sectionId) {
        store.jdbc().update("DELETE FROM section_schedule_patterns WHERE section_id=?", sectionId);
        store.jdbc().update("""
                UPDATE course_sections SET status='CANCELLED',version=version+1,updated_at=now() WHERE id=?
                """, sectionId);
    }

    public boolean isActiveLecturer(long staffId) {
        return store.exists("""
                SELECT 1 FROM staff_profiles p JOIN users u ON u.id=p.user_id JOIN roles r ON r.role_id=u.role_id
                WHERE p.id=? AND p.status='ACTIVE' AND u.status='ACTIVE' AND r.code='LECTURER'
                """, staffId);
    }

    public record Assignment(long id, long staffId, String role) {
    }

    public List<Assignment> activeAssignments(long sectionId) {
        return store.jdbc().query("""
                SELECT id,staff_id,role FROM section_assignments WHERE section_id=? AND valid_to IS NULL
                """, (rs, row) -> new Assignment(rs.getLong("id"), rs.getLong("staff_id"), rs.getString("role")),
                sectionId);
    }

    public void closeAssignment(long assignmentId) {
        store.jdbc().update("""
                UPDATE section_assignments SET valid_to=GREATEST(valid_from,CURRENT_DATE) WHERE id=?
                """, assignmentId);
    }

    public void addAssignment(long sectionId, long staffId, String role, LocalDate validFrom, long actorId) {
        store.jdbc().update("""
                INSERT INTO section_assignments(section_id,staff_id,role,valid_from,assigned_by) VALUES (?,?,?,?,?)
                """, sectionId, staffId, role, Date.valueOf(validFrom), actorId);
    }

    public void replacePatternLecturer(long sectionId, long staffId) {
        store.jdbc().update("""
                UPDATE section_schedule_patterns SET staff_id=?,version=version+1 WHERE section_id=?
                """, staffId, sectionId);
    }

    public void touch(long sectionId) {
        store.jdbc().update("UPDATE course_sections SET version=version+1,updated_at=now() WHERE id=?", sectionId);
    }

    public void replaceTargetGroups(long sectionId, Iterable<Long> groupIds) {
        store.jdbc().update("DELETE FROM section_target_groups WHERE section_id=?", sectionId);
        for (long groupId : groupIds) {
            store.jdbc().update("INSERT INTO section_target_groups(section_id,group_id) VALUES (?,?)",
                    sectionId, groupId);
        }
    }
}
