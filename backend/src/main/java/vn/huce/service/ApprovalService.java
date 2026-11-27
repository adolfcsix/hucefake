package vn.huce.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.ApprovalDecisionRequest;
import vn.huce.model.ApprovalSnapshot;
import vn.huce.model.ApprovalStatus;
import vn.huce.model.TimetableVersionStatus;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;

@Service
public class ApprovalService {
    private final ApprovalRepository repository;
    private final AuditRepository audit;
    private final NotificationService notifications;
    private final SchedulingService scheduling;
    private final ObjectMapper json;

    public ApprovalService(ApprovalRepository repository, AuditRepository audit,
                           NotificationService notifications, SchedulingService scheduling,
                           ObjectMapper json) {
        this.repository = repository;
        this.audit = audit;
        this.notifications = notifications;
        this.scheduling = scheduling;
        this.json = json;
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    private Object decode(Object value) {
        try {
            return json.readValue(value.toString(), Object.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    @SafeVarargs
    private final <S extends Enum<S>> void status(Map<String, Object> row, S... allowed) {
        for (S candidate : allowed) {
            if (candidate.name().equals(row.get("status"))) {
                return;
            }
        }
        throw state("Hồ sơ đang ở trạng thái " + row.get("status") + ".");
    }

    private void forbidden() {
        throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }

    public Map<String, Object> approval(long approvalId) {
        Map<String, Object> row = repository.findApproval(approvalId);
        row.put("payload", decode(row.remove("payload_json")));
        return row;
    }

    record Assessment(Map<String, Object> readiness, List<ApprovalSnapshot> snapshot) {
    }

    Assessment assess(Actor actor, long semesterId) {
        Context context = scheduling.context();
        var drafts = context.sections().values().stream()
                .filter(section -> n(section, "semester_id") == semesterId
                        && "DRAFT".equals(section.get("status"))).toList();
        boolean eligible = drafts.stream().anyMatch(section ->
                Objects.equals(actor.unitId(), nullableId(section.get("managing_unit_id"))));
        List<Map<String, Object>> blocked = new ArrayList<>();
        List<ApprovalSnapshot> snapshot = new ArrayList<>();
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("semesterId", semesterId);
        readiness.put("draftCount", drafts.size());
        readiness.put("blockedSections", blocked);
        readiness.put("eligible", eligible);
        readiness.put("ready", false);
        if (drafts.isEmpty() || !eligible) {
            return new Assessment(readiness, snapshot);
        }
        for (Map<String, Object> section : drafts) {
            List<Slot> slots = context.patterns().stream()
                    .filter(pattern -> pattern.section() == n(section, "id")).toList();
            List<Map<String, Object>> missing = new ArrayList<>();
            List<Map<String, Object>> conflicts = new ArrayList<>();
            if (section.get("staff_id") == null) {
                missing.add(Map.of("rule", "BR02", "message", "Chưa có giảng viên chính."));
            }
            if (slots.size() != n(section, "sessions_per_week")) {
                missing.add(Map.of("rule", "BR10", "message", "Số buổi đã xếp khác số buổi yêu cầu."));
            }
            for (Slot slot : slots) {
                Result result = scheduling.check(context, slot, actor, false);
                Map<String, Object> room = context.rooms().get(slot.room());
                if (!result.valid()) {
                    Map<String, Object> conflict = new LinkedHashMap<>();
                    conflict.put("id", slot.id());
                    conflict.put("dayOfWeek", slot.day());
                    conflict.put("periodFrom", slot.first());
                    conflict.put("roomCode", room == null ? null : room.get("code"));
                    conflict.put("issues", result.issues());
                    conflicts.add(conflict);
                } else {
                    snapshot.add(new ApprovalSnapshot(slot.id(), slot.section(), slot.staff(), slot.room(),
                            slot.day(), slot.first(), slot.count(), slot.from().toString(), slot.to().toString(),
                            repository.patternVersion(slot.id()), (String) section.get("code"),
                            (String) section.get("subject_name"), (String) room.get("code")));
                }
            }
            if (!missing.isEmpty() || !conflicts.isEmpty()) {
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("id", section.get("id"));
                block.put("code", section.get("code"));
                block.put("subjectName", section.get("subject_name"));
                block.put("editable", Objects.equals(actor.unitId(), nullableId(section.get("managing_unit_id"))));
                block.put("scheduled", slots.size());
                block.put("required", section.get("sessions_per_week"));
                block.put("missing", missing);
                block.put("conflicts", conflicts);
                blocked.add(block);
            }
        }
        readiness.put("ready", blocked.isEmpty());
        return new Assessment(readiness, snapshot);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> readiness(Actor actor, long semesterId) {
        role(actor, "STAFF");
        repository.requireSemester(semesterId);
        if (repository.hasSubmittedVersion(semesterId)) {
            return Map.of("semesterId", semesterId, "pending", true, "ready", false, "blockedSections", List.of());
        }
        return assess(actor, semesterId).readiness();
    }

    public Object create(Actor actor, long semesterId) {
        role(actor, "STAFF");
        repository.requireSemester(semesterId);
        if (repository.hasSubmittedVersion(semesterId)) {
            throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");
        }
        Map<String, Object> existing = repository.findDraft(semesterId, actor.id());
        return existing != null ? existing : repository.createDraft(semesterId, actor.id());
    }

    public Object submit(Actor actor, long versionId) {
        role(actor, "STAFF");
        Map<String, Object> version = repository.findVersion(versionId);
        if (n(version, "created_by") != actor.id()) {
            forbidden();
        }
        status(version, TimetableVersionStatus.DRAFT);
        long semesterId = n(version, "semester_id");
        if (repository.hasSubmittedVersion(semesterId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Học kỳ đã có thời khóa biểu chờ duyệt.");
        }
        Assessment assessment = assess(actor, semesterId);
        Map<String, Object> ready = assessment.readiness();
        if (n(ready, "draftCount") == 0) {
            throw state("Học kỳ chưa có lớp nháp để gửi duyệt.");
        }
        if (!Boolean.TRUE.equals(ready.get("eligible"))) {
            forbidden();
        }
        if (!Boolean.TRUE.equals(ready.get("ready"))) {
            rejectIncomplete(ready);
        }
        long workItemId = repository.createWorkItem(actor.id());
        long approvalId = repository.createApproval(workItemId, actor.id(), encode(assessment.snapshot()));
        repository.submitVersion(versionId, n(ready, "draftCount"), approvalId);
        audit.record(actor, "SUBMIT", "timetable_versions", versionId);
        notifications.notifyManagers("Thời khóa biểu bản " + version.get("version_no")
                + " đang chờ duyệt. Mở Duyệt thời khóa biểu để xem phương án.");
        return Map.of("approvalId", approvalId);
    }

    private void rejectIncomplete(Map<String, Object> ready) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (Object rawSection : (List<?>) ready.get("blockedSections")) {
            Map<?, ?> section = (Map<?, ?>) rawSection;
            for (Object rawSlot : (List<?>) section.get("conflicts")) {
                Map<?, ?> slot = (Map<?, ?>) rawSlot;
                for (Object rawIssue : (List<?>) slot.get("issues")) {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    ((Map<?, ?>) rawIssue).forEach((key, value) -> issue.put(key.toString(), value));
                    issue.putIfAbsent("patternId", slot.get("id"));
                    issue.put("candidateSectionCode", section.get("code"));
                    issues.add(issue);
                }
            }
        }
        if (!issues.isEmpty()) {
            Map<String, Object> details = new LinkedHashMap<>(ready);
            details.put("issues", issues);
            throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", details);
        }
        throw new ApiFailure(409, "INVALID_STATE",
                "Cần hoàn tất phân công và xếp đủ các buổi trước khi gửi duyệt.", ready);
    }

    public Object decide(Actor actor, Map<String, Object> approval, Map<String, Object> body) {
        role(actor, "MANAGER");
        if (n(approval, "requested_by") == actor.id()) {
            forbidden();
        }
        ApprovalStatus decision = ApprovalDecisionRequest.parseDecision(body);
        boolean returned = decision == ApprovalStatus.RETURNED;
        if (returned) {
            status(approval, ApprovalStatus.PENDING, ApprovalStatus.APPROVED);
        } else {
            status(approval, ApprovalStatus.PENDING);
        }
        ApprovalDecisionRequest request = ApprovalDecisionRequest.from(body, decision);
        String reason = request.reason();
        long approvalId = n(approval, "id");
        repository.recordDecision(approvalId, actor.id(), request.decision().name(), reason);
        repository.changeWorkStatus(n(approval, "work_item_id"), decision.name());
        if (returned) {
            repository.returnVersion(approvalId);
            notifications.notifyUser(n(approval, "requested_by"), "Thời khóa biểu được trả lại. Lý do: " + reason);
        }
        audit.record(actor, decision.name(), "approval_requests", approvalId);
        return Map.of("status", decision.name());
    }

    public Object apply(Actor actor, Map<String, Object> approval) {
        role(actor, "MANAGER");
        if (n(approval, "requested_by") == actor.id()) {
            forbidden();
        }
        long approvalId = n(approval, "id");
        Map<String, Object> prior = repository.priorExecution(approvalId);
        if (prior != null) {
            return decode(prior.get("result"));
        }
        status(approval, ApprovalStatus.APPROVED);
        Map<String, Object> version = repository.findVersionForApproval(approvalId);
        status(version, TimetableVersionStatus.SUBMITTED);
        long versionId = n(version, "id");
        long semesterId = n(version, "semester_id");
        Context context = scheduling.context();
        Set<Long> sections = new LinkedHashSet<>();
        long total = 0;
        List<?> snapshot = (List<?>) approval.get("payload");
        Map<Long, Slot> current = new HashMap<>();
        for (Slot pattern : context.patterns()) {
            if (n(context.sections().get(pattern.section()), "semester_id") == semesterId) {
                current.put(pattern.id(), pattern);
            }
        }
        if (snapshot.isEmpty() || snapshot.size() != current.size()) {
            throw state("Phương án đã thay đổi sau khi gửi duyệt.");
        }
        for (Object raw : snapshot) {
            if (!(raw instanceof Map<?, ?> row)) {
                throw state("Hồ sơ không hợp lệ.");
            }
            long patternId = id(row.get("id"));
            Slot slot = current.remove(patternId);
            if (!matchesSnapshot(slot, row)) {
                throw state("Phương án đã thay đổi sau khi gửi duyệt.");
            }
            Result result = scheduling.check(context, slot, actor, false);
            if (!result.valid()) {
                throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.",
                        Map.of("issues", result.issues()));
            }
            long count = 0;
            for (var date : occurrences(slot)) {
                if (scheduling.holiday(context, n(context.rooms().get(slot.room()), "campus_id"), date)) {
                    continue;
                }
                Span time = scheduling.interval(context, slot, date);
                repository.insertSession(slot.section(), slot.staff(), slot.room(), slot.first(),
                        slot.count(), time.start(), time.end(), versionId, slot.id());
                count++;
            }
            if (count == 0) {
                throw state("Một lịch định kỳ không sinh được buổi học vì toàn bộ ngày đều nghỉ.");
            }
            total += count;
            sections.add(slot.section());
        }
        if (!current.isEmpty() || sections.size() != n(version, "section_count")) {
            throw state("Phương án đã thay đổi sau khi gửi duyệt.");
        }
        for (long sectionId : sections) {
            repository.openSection(sectionId);
            notifications.notifySection(sectionId, "Đã công bố lịch lớp "
                    + context.sections().get(sectionId).get("code") + ".");
        }
        repository.supersedePublished(semesterId);
        repository.publishVersion(versionId, actor.id());
        repository.markApplied(approvalId);
        repository.changeWorkStatus(n(approval, "work_item_id"), ApprovalStatus.APPLIED.name());
        var result = Map.of("versionId", versionId, "sessions", total, "sections", sections.size());
        repository.saveExecution(approvalId, encode(result));
        audit.record(actor, "PUBLISH", "timetable_versions", versionId);
        return result;
    }

    private boolean matchesSnapshot(Slot slot, Map<?, ?> row) {
        return slot != null && slot.section() == id(row.get("section"))
                && Objects.equals(slot.staff(), id(row.get("staff")))
                && slot.room() == id(row.get("room")) && slot.day() == id(row.get("day"))
                && slot.first() == id(row.get("first")) && slot.count() == id(row.get("count"))
                && slot.from().toString().equals(row.get("from")) && slot.to().toString().equals(row.get("to"));
    }

    public Object publish(Actor actor, long approvalId) {
        role(actor, "MANAGER");
        Map<String, Object> approval = approval(approvalId);
        if (ApprovalStatus.PENDING.name().equals(approval.get("status"))) {
            decide(actor, approval, Map.of("decision", ApprovalStatus.APPROVED.name()));
            approval = approval(approvalId);
        }
        return apply(actor, approval);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object versions(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "MANAGER");
        return repository.findVersions(query, id(query.get("semesterId")), query.get("filterStatus"));
    }
}
