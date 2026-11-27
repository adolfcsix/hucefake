package vn.huce.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRepository {
    private final SqlStore store;

    public ApprovalRepository(SqlStore store) {
        this.store = store;
    }

    public Map<String, Object> findApproval(long approvalId) {
        return store.need("""
                SELECT a.*,a.payload::text AS payload_json FROM approval_requests a
                JOIN timetable_versions t ON t.approval_request_id=a.id WHERE a.id=?
                """, approvalId);
    }

    public Object patternVersion(long patternId) {
        return store.need("SELECT version FROM section_schedule_patterns WHERE id=?", patternId).get("version");
    }

    public void requireSemester(long semesterId) {
        store.need("SELECT id FROM semesters WHERE id=?", semesterId);
    }

    public boolean hasSubmittedVersion(long semesterId) {
        return store.exists("SELECT 1 FROM timetable_versions WHERE semester_id=? AND status='SUBMITTED'",
                semesterId);
    }

    public Map<String, Object> findDraft(long semesterId, long actorId) {
        return store.one("""
                SELECT id FROM timetable_versions WHERE semester_id=? AND created_by=? AND status='DRAFT'
                ORDER BY id DESC LIMIT 1
                """, semesterId, actorId);
    }

    public Map<String, Object> createDraft(long semesterId, long actorId) {
        return store.need("""
                INSERT INTO timetable_versions(semester_id,version_no,created_by)
                SELECT ?,COALESCE(max(version_no),0)+1,? FROM timetable_versions WHERE semester_id=? RETURNING id
                """, semesterId, actorId, semesterId);
    }

    public Map<String, Object> findVersion(long versionId) {
        return store.need("SELECT * FROM timetable_versions WHERE id=?", versionId);
    }

    public Map<String, Object> findVersionForApproval(long approvalId) {
        return store.need("SELECT * FROM timetable_versions WHERE approval_request_id=?", approvalId);
    }

    public long createWorkItem(long actorId) {
        return ((Number) store.need("INSERT INTO work_items(kind,created_by) VALUES ('TIMETABLE',?) RETURNING id",
                actorId).get("id")).longValue();
    }

    public long createApproval(long workItemId, long actorId, String snapshotJson) {
        return ((Number) store.need("""
                INSERT INTO approval_requests(work_item_id,requested_by,payload) VALUES (?,?,?::jsonb) RETURNING id
                """, workItemId, actorId, snapshotJson).get("id")).longValue();
    }

    public void submitVersion(long versionId, long sectionCount, long approvalId) {
        store.jdbc().update("""
                UPDATE timetable_versions SET status='SUBMITTED',section_count=?,approval_request_id=? WHERE id=?
                """, sectionCount, approvalId, versionId);
    }

    public void recordDecision(long approvalId, long actorId, String decision, String reason) {
        store.jdbc().update("""
                UPDATE approval_requests SET status=?,decided_by=?,decided_at=now(),decision_reason=? WHERE id=?
                """, decision, actorId, reason, approvalId);
    }

    public void changeWorkStatus(long workItemId, String status) {
        store.jdbc().update("UPDATE work_items SET status=? WHERE id=?", status, workItemId);
    }

    public void returnVersion(long approvalId) {
        store.jdbc().update("UPDATE timetable_versions SET status='RETURNED' WHERE approval_request_id=?", approvalId);
    }

    public Map<String, Object> priorExecution(long approvalId) {
        return store.one("SELECT result::text AS result FROM execution_records WHERE approval_request_id=?", approvalId);
    }

    public void insertSession(long sectionId, Long staffId, long roomId, int periodFrom, int periodCount,
                              LocalDateTime start, LocalDateTime end, long versionId, long patternId) {
        store.jdbc().update("""
                INSERT INTO class_sessions(section_id,staff_id,room_id,period_from,period_count,
                    start_at,end_at,timetable_version_id,pattern_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, sectionId, staffId, roomId, periodFrom, periodCount,
                Timestamp.valueOf(start), Timestamp.valueOf(end), versionId, patternId);
    }

    public void openSection(long sectionId) {
        store.jdbc().update("""
                UPDATE course_sections SET status='OPEN',version=version+1,updated_at=now() WHERE id=?
                """, sectionId);
    }

    public void supersedePublished(long semesterId) {
        store.jdbc().update("""
                UPDATE timetable_versions SET status='SUPERSEDED' WHERE semester_id=? AND status='PUBLISHED'
                """, semesterId);
    }

    public void publishVersion(long versionId, long actorId) {
        store.jdbc().update("""
                UPDATE timetable_versions SET status='PUBLISHED',published_by=?,published_at=now() WHERE id=?
                """, actorId, versionId);
    }

    public void markApplied(long approvalId) {
        store.jdbc().update("UPDATE approval_requests SET status='APPLIED' WHERE id=?", approvalId);
    }

    public void saveExecution(long approvalId, String resultJson) {
        store.jdbc().update("""
                INSERT INTO execution_records(approval_request_id,idempotency_key,result) VALUES (?,?,?::jsonb)
                """, approvalId, "fm05-publish:" + approvalId, resultJson);
    }

    public Object findVersions(Map<String, String> query, long semesterId, String status) {
        return store.paged(query, """
                SELECT t.*,a.status AS decision_status,a.decision_reason,u.username AS created_by_name
                FROM timetable_versions t JOIN users u ON u.id=t.created_by
                LEFT JOIN approval_requests a ON a.id=t.approval_request_id
                WHERE t.semester_id=? AND (?::text IS NULL OR t.status=?)
                """, Arrays.asList(semesterId, status, status), "version_no DESC,id DESC");
    }
}
