package vn.huce.repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;
import vn.huce.model.ClaimedScheduleRun;
import vn.huce.model.ScheduleRunItem;
import vn.huce.model.ScheduleRunSnapshot;
import vn.huce.model.ScheduleRunStatus;
import vn.huce.exception.ApiFailure;

@Repository
public class RunRepository {
    private static final String MANAGER_SCOPE = """
            EXISTS(SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.id=run.requested_by AND r.code='MANAGER')
            AND NOT EXISTS(SELECT 1 FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id
                JOIN subjects s ON s.id=c.subject_id WHERE i.run_id=run.id AND s.managing_unit_id IS DISTINCT FROM ?::bigint)
            """;
    private final SqlStore store;

    public RunRepository(SqlStore store) {
        this.store = store;
    }

    public Map<String, Object> find(long runId) {
        return store.need("SELECT *,parameters::text AS parameters_json FROM schedule_runs WHERE id=?", runId);
    }

    public ScheduleRunSnapshot findSnapshot(long runId) {
        List<ScheduleRunSnapshot> rows = store.jdbc().query("""
                SELECT id,semester_id,requested_by,status,hard_violations FROM schedule_runs WHERE id=?
                """, (rs, row) -> new ScheduleRunSnapshot(rs.getLong("id"), rs.getLong("semester_id"),
                rs.getLong("requested_by"), ScheduleRunStatus.valueOf(rs.getString("status")),
                rs.getObject("hard_violations", Integer.class)), runId);
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public boolean visibleManagerRun(long runId, Long unitId) {
        return store.exists("SELECT 1 FROM schedule_runs run WHERE run.id=? AND " + MANAGER_SCOPE, runId, unitId);
    }

    public boolean active(long semesterId) {
        return store.exists("SELECT 1 FROM schedule_runs WHERE semester_id=? AND status IN ('QUEUED','RUNNING')", semesterId);
    }

    public long enqueue(long semesterId, long actorId, String parametersJson) {
        List<Long> rows = store.jdbc().query("""
                INSERT INTO schedule_runs(semester_id,requested_by,parameters) VALUES (?,?,?::jsonb) RETURNING id
                """, (rs, row) -> rs.getLong("id"), semesterId, actorId, parametersJson);
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public List<ScheduleRunItem> rawItems(long runId) {
        return store.jdbc().query("""
                SELECT section_id,staff_id,room_id,day_of_week,period_from,period_count
                FROM schedule_run_items WHERE run_id=? ORDER BY id
                """, (row, index) -> new ScheduleRunItem(
                row.getLong("section_id"), row.getLong("staff_id"), row.getLong("room_id"),
                row.getInt("day_of_week"), row.getInt("period_from"), row.getInt("period_count")), runId);
    }

    public void insertPattern(long runId, long actorId, long sectionId, Long staffId, long roomId,
                              int dayOfWeek, int periodFrom, int periodCount, LocalDate from, LocalDate to) {
        store.jdbc().update("""
                INSERT INTO section_schedule_patterns(section_id,staff_id,room_id,day_of_week,period_from,
                    period_count,effective_from,effective_to,source,run_id,created_by)
                VALUES (?,?,?,?,?,?,?,?,'GENETIC',?,?)
                """, sectionId, staffId, roomId, dayOfWeek, periodFrom, periodCount,
                Date.valueOf(from), Date.valueOf(to), runId, actorId);
    }

    public void markApplied(long runId) {
        store.jdbc().update("UPDATE schedule_runs SET status='APPLIED' WHERE id=?", runId);
    }

    public void discard(long runId) {
        store.jdbc().update("UPDATE schedule_runs SET status='DISCARDED' WHERE id=?", runId);
    }

    public Object list(Actor actor, long semesterId, Map<String, String> page) {
        String sql = """
                SELECT id,status,generations_run,hard_violations,soft_penalty,fitness,message,requested_by,created_at
                FROM schedule_runs run WHERE semester_id=? AND (? OR requested_by=? OR (status IN ('COMPLETED','APPLIED') AND
                """ + MANAGER_SCOPE + "))";
        List<Object> args = Arrays.asList(semesterId, "MANAGER".equals(actor.role()), actor.id(), actor.unitId());
        if (!page.containsKey("page") && !page.containsKey("pageSize")) {
            return store.rows(sql + " ORDER BY id DESC LIMIT 30", args.toArray());
        }
        return store.paged(page, sql, args, "id DESC");
    }

    public List<Map<String, Object>> progress(long runId) {
        return store.rows("SELECT generation,hard_violations,soft_penalty FROM schedule_run_progress WHERE run_id=? ORDER BY generation", runId);
    }

    public List<Map<String, Object>> items(long runId) {
        return store.rows("""
                SELECT i.*,c.code AS section_code,t.code AS room_code,s.name AS subject_name
                FROM schedule_run_items i JOIN course_sections c ON c.id=i.section_id JOIN subjects s ON s.id=c.subject_id
                JOIN teaching_rooms t ON t.id=i.room_id WHERE run_id=? ORDER BY day_of_week,period_from,i.id
                """, runId);
    }


    public void failInterrupted() {
        store.jdbc().update("""
                UPDATE schedule_runs SET status='FAILED',finished_at=now(),message='Lần chạy bị gián đoạn.'
                WHERE status='RUNNING' AND started_at<now()-interval '10 minutes'
                """);
    }

    public ClaimedScheduleRun claimNext() {
        List<ClaimedScheduleRun> rows = store.jdbc().query("""
                UPDATE schedule_runs SET status='RUNNING',started_at=now() WHERE id=(
                    SELECT id FROM schedule_runs WHERE status='QUEUED' ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING id,semester_id,requested_by,parameters::text AS parameters_json
                """, (row, index) -> new ClaimedScheduleRun(row.getLong("id"),
                row.getLong("semester_id"), row.getLong("requested_by"), row.getString("parameters_json")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Actor activeRequester(long userId) {
        List<Actor> rows = store.jdbc().query("""
                SELECT u.id,u.username,r.code AS role,p.id AS staff_id,p.unit_id FROM users u
                JOIN roles r ON r.role_id=u.role_id LEFT JOIN staff_profiles p ON p.user_id=u.id AND p.status='ACTIVE'
                WHERE u.id=? AND u.status='ACTIVE'
                """, (row, index) -> new Actor(row.getLong("id"), row.getString("username"),
                row.getString("role"), row.getObject("staff_id", Long.class),
                row.getObject("unit_id", Long.class)), userId);
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public void recordProgress(long runId, int generation, int hard, double soft) {
        int updated = store.jdbc().update(
                "UPDATE schedule_runs SET generations_run=? WHERE id=? AND status='RUNNING' AND generations_run<=?",
                generation, runId, generation);
        if (updated == 0) {
            return;
        }
        store.jdbc().update("""
                INSERT INTO schedule_run_progress(run_id,generation,hard_violations,soft_penalty) VALUES (?,?,?,?)
                ON CONFLICT(run_id,generation) DO UPDATE SET hard_violations=EXCLUDED.hard_violations,soft_penalty=EXCLUDED.soft_penalty
                """, runId, generation, hard, soft);
    }

    public boolean running(long runId) {
        return store.exists("SELECT 1 FROM schedule_runs WHERE id=? AND status='RUNNING'", runId);
    }

    public void insertItem(long runId, long sectionId, Long staffId, long roomId, int dayOfWeek,
                           int periodFrom, int periodCount, String note) {
        store.jdbc().update("""
                INSERT INTO schedule_run_items(run_id,section_id,staff_id,room_id,day_of_week,period_from,period_count,violation_note)
                VALUES (?,?,?,?,?,?,?,?)
                """, runId, sectionId, staffId, roomId, dayOfWeek, periodFrom, periodCount, note);
    }

    public void complete(long runId, int generation, int hard, double soft, double fitness) {
        store.jdbc().update("""
                UPDATE schedule_runs SET status='COMPLETED',generations_run=?,hard_violations=?,soft_penalty=?,
                    fitness=?,finished_at=now() WHERE id=?
                """, generation, hard, soft, fitness, runId);
    }

    public void fail(long runId, String message) {
        store.jdbc().update("""
                UPDATE schedule_runs SET status='FAILED',finished_at=now(),message=? WHERE id=? AND status='RUNNING'
                """, message, runId);
    }
}
