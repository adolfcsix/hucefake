package vn.huce.service;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.ApprovalDecisionRequest;
import vn.huce.dto.ApprovalDecisionResponse;
import vn.huce.dto.ApprovalSubmissionResponse;
import vn.huce.dto.TimetableVersionIdResponse;
import vn.huce.model.ApprovalRequestSnapshot;
import vn.huce.model.ApprovalReadiness;
import vn.huce.model.ApprovalStatus;
import vn.huce.model.TimetableVersionStatus;
import vn.huce.model.TimetableVersionSnapshot;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;

import static vn.huce.validation.Inputs.*;

@Service
public class ApprovalService {
    private final ApprovalRepository repository;
    private final AuditRepository audit;
    private final NotificationService notifications;
    private final TimetablePublicationService publication;
    private final ApprovalAssessmentService assessments;
    private final ObjectMapper json;

    public ApprovalService(ApprovalRepository repository, AuditRepository audit,
                           NotificationService notifications, TimetablePublicationService publication,
                           ObjectMapper json, ApprovalAssessmentService assessments) {
        this.repository = repository;
        this.audit = audit;
        this.notifications = notifications;
        this.publication = publication;
        this.json = json;
        this.assessments = assessments;
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

    private void status(ApprovalRequestSnapshot approval, ApprovalStatus... allowed) {
        for (ApprovalStatus candidate : allowed) {
            if (candidate == approval.status()) {
                return;
            }
        }
        throw state("Hồ sơ đang ở trạng thái " + approval.status() + ".");
    }

    private void requireVersionStatus(TimetableVersionSnapshot version, TimetableVersionStatus expected) {
        if (version.status() != expected) {
            throw state("Hồ sơ đang ở trạng thái " + version.status() + ".");
        }
    }

    private void forbidden() {
        throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }

    public Map<String, Object> approval(long approvalId) {
        Map<String, Object> row = repository.findApproval(approvalId);
        row.put("payload", decode(row.remove("payload_json")));
        return row;
    }

    public ApprovalRequestSnapshot approvalForMutation(long approvalId) {
        return repository.findApprovalForMutation(approvalId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> readiness(Actor actor, long semesterId) {
        role(actor, "STAFF");
        repository.requireSemester(semesterId);
        if (repository.hasSubmittedVersion(semesterId)) {
            return Map.of("semesterId", semesterId, "pending", true, "ready", false, "blockedSections", List.of());
        }
        return assessments.assess(actor, semesterId).readiness().toResponse();
    }

    public TimetableVersionIdResponse create(Actor actor, long semesterId) {
        role(actor, "STAFF");
        repository.requireSemester(semesterId);
        if (repository.hasSubmittedVersion(semesterId)) {
            throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");
        }
        Long existing = repository.findDraft(semesterId, actor.id());
        long versionId = existing != null ? existing : repository.createDraft(semesterId, actor.id());
        return new TimetableVersionIdResponse(versionId);
    }

    public ApprovalSubmissionResponse submit(Actor actor, long versionId) {
        role(actor, "STAFF");
        TimetableVersionSnapshot version = repository.findVersion(versionId);
        if (version.createdBy() != actor.id()) {
            forbidden();
        }
        requireVersionStatus(version, TimetableVersionStatus.DRAFT);
        long semesterId = version.semesterId();
        if (repository.hasSubmittedVersion(semesterId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Học kỳ đã có thời khóa biểu chờ duyệt.");
        }
        var assessment = assessments.assess(actor, semesterId);
        ApprovalReadiness ready = assessment.readiness();
        if (ready.draftCount() == 0) {
            throw state("Học kỳ chưa có lớp nháp để gửi duyệt.");
        }
        if (!ready.eligible()) {
            forbidden();
        }
        if (!ready.ready()) {
            assessments.rejectIncomplete(ready);
        }
        long workItemId = repository.createWorkItem(actor.id());
        long approvalId = repository.createApproval(workItemId, actor.id(), encode(assessment.snapshot()));
        repository.submitVersion(versionId, ready.draftCount(), approvalId);
        audit.record(actor, "SUBMIT", "timetable_versions", versionId);
        notifications.notifyManagers("Thời khóa biểu bản " + version.versionNo()
                + " đang chờ duyệt. Mở Duyệt thời khóa biểu để xem phương án.");
        return new ApprovalSubmissionResponse(approvalId);
    }

    public ApprovalDecisionResponse decide(Actor actor, ApprovalRequestSnapshot approval, Map<String, Object> body) {
        role(actor, "MANAGER");
        if (approval.requestedBy() == actor.id()) {
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
        long approvalId = approval.id();
        repository.recordDecision(approvalId, actor.id(), request.decision().name(), reason);
        repository.changeWorkStatus(approval.workItemId(), decision.name());
        if (returned) {
            repository.returnVersion(approvalId);
            notifications.notifyUser(approval.requestedBy(), "Thời khóa biểu được trả lại. Lý do: " + reason);
        }
        audit.record(actor, decision.name(), "approval_requests", approvalId);
        return new ApprovalDecisionResponse(decision);
    }

    public Object apply(Actor actor, ApprovalRequestSnapshot approval) {
        role(actor, "MANAGER");
        if (approval.requestedBy() == actor.id()) {
            forbidden();
        }
        long approvalId = approval.id();
        String prior = repository.priorExecution(approvalId);
        if (prior != null) {
            return decode(prior);
        }
        status(approval, ApprovalStatus.APPROVED);
        TimetableVersionSnapshot version = repository.findVersionForApproval(approvalId);
        requireVersionStatus(version, TimetableVersionStatus.SUBMITTED);
        long versionId = version.id();
        var result = publication.materialize(actor, approval, version, (List<?>) decode(approval.payloadJson()));
        repository.saveExecution(approvalId, encode(result));
        audit.record(actor, "PUBLISH", "timetable_versions", versionId);
        return result;
    }

    public Object publish(Actor actor, long approvalId) {
        role(actor, "MANAGER");
        ApprovalRequestSnapshot approval = approvalForMutation(approvalId);
        if (approval.status() == ApprovalStatus.PENDING) {
            decide(actor, approval, Map.of("decision", ApprovalStatus.APPROVED.name()));
            approval = approvalForMutation(approvalId);
        }
        return apply(actor, approval);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object versions(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "MANAGER");
        return repository.findVersions(query, id(query.get("semesterId")), query.get("filterStatus"));
    }
}
