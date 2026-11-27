package vn.huce.service;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.dto.SessionChangeRequest;
import vn.huce.dto.SessionRequestDecision;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SessionAdjustmentSnapshot;
import vn.huce.model.SessionChangeAction;
import vn.huce.model.SessionLecturerScope;
import vn.huce.model.SessionRequestResolution;
import vn.huce.model.SessionRequestSnapshot;
import vn.huce.model.SessionRequestStatus;
import vn.huce.model.SessionSectionSnapshot;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.repository.SessionChangeRepository;

import static vn.huce.service.SchedulingService.Context;
import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.invalid;
import static vn.huce.validation.Inputs.role;
import static vn.huce.validation.Inputs.state;
import static vn.huce.validation.Inputs.unit;

@Service
public class SessionMutationService {
    private final SessionChangeRepository repository;
    private final SectionRepository sectionRepository;
    private final AuditRepository audit;
    private final SchedulingService scheduling;
    private final NotificationService notifications;
    private final SessionReplacementService replacements;

    SessionMutationService(SessionChangeRepository repository, SectionRepository sectionRepository,
                           AuditRepository audit, SchedulingService scheduling, NotificationService notifications,
                           SessionReplacementService replacements) {
        this.repository = repository;
        this.sectionRepository = sectionRepository;
        this.audit = audit;
        this.scheduling = scheduling;
        this.notifications = notifications;
        this.replacements = replacements;
    }

    private void forbidden() {
        throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }

    private void campus(Actor actor, Long campusId) {
        if (campusId != null) {
            scheduling.requireCampus(actor, campusId);
        }
    }

    public Object change(Actor actor, long sessionId, Map<String, Object> body, Long requestId) {
        role(actor, "STAFF");
        SessionAdjustmentSnapshot original = repository.findSession(sessionId);
        SessionSectionSnapshot section = sectionRepository.findSectionForSession(original.sectionId());
        unit(actor, section.managingUnitId());
        if (repository.hasException(sessionId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Buổi học đã có ngoại lệ.");
        }
        if (!"PLANNED".equals(original.status()) || !"OPEN".equals(section.status())) {
            throw state("Chỉ điều chỉnh buổi học đang lên lịch của lớp đã mở.");
        }
        campus(actor, original.campusId());
        Context context = scheduling.context();
        SessionChangeRequest request = SessionChangeRequest.adjustment(body);
        LocalDate originalDate = original.startAt().toLocalDate();
        Long replacementId = null;
        if (request.action() != SessionChangeAction.CANCEL) {
            replacementId = replacements.createReplacement(actor, sessionId, context, original, section, originalDate, request.action(), body);
        }

        long exceptionId = repository.insertException(sessionId, originalDate, request.action(),
                replacementId, request.reason(), actor.id());
        repository.cancelSession(sessionId);
        audit.record(actor, request.action().name(), "session_exceptions", exceptionId);
        if (requestId == null) {
            closePendingRequests(actor, sessionId, request.action());
        }
        notifications.notifySection(section.id(), "Lớp " + section.code()
                + " thay đổi lịch ngày " + originalDate + ". " + request.reason());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", exceptionId);
        response.put("replacementId", replacementId);
        return response;
    }

    private void closePendingRequests(Actor actor, long sessionId, SessionChangeAction action) {
        for (SessionRequestResolution request : repository.closePendingRequests(sessionId, action)) {
            audit.record(actor, request.status().name(), "session_change_requests", request.id());
            notifications.notifyStaffProfile(request.staffId(), request.reason());
        }
    }

    public Object requests(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "LECTURER");
        return repository.findRequests(query, id(query.get("semesterId")), "STAFF".equals(actor.role()),
                actor.unitId(), actor.staffId());
    }

    public Object request(Actor actor, long sessionId, Map<String, Object> body) {
        role(actor, "LECTURER");
        SessionLecturerScope session = repository.findSessionWithStaff(sessionId);
        if (actor.staffId() == null || !session.staffIds().contains(actor.staffId())) {
            forbidden();
        }
        SessionSectionSnapshot section = sectionRepository.findSectionForSession(session.sectionId());
        if (!"PLANNED".equals(session.status()) || !"OPEN".equals(section.status())) {
            throw state("Buổi học không còn ở trạng thái đã lên lịch.");
        }
        if (repository.hasPendingRequest(sessionId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Buổi học đã có đề nghị chờ xử lý.");
        }
        SessionChangeRequest request = SessionChangeRequest.lecturer(body);
        long requestId = repository.insertRequest(sessionId, actor.staffId(), request.action(), request.reason());
        audit.record(actor, "REQUEST_CHANGE", "class_sessions", sessionId);
        notifications.notifyScopedStaff(section.managingUnitId(), session.roomId(),
                "Có đề nghị đổi lịch cho lớp " + section.code() + ". Mở Đề nghị đổi lịch để xử lý.");
        return Map.of("id", requestId);
    }

    public void decide(Actor actor, long requestId, Map<String, Object> body) {
        role(actor, "STAFF");
        SessionRequestSnapshot record = repository.findRequest(requestId);
        unit(actor, record.managingUnitId());
        campus(actor, record.campusId());
        if (record.status() != SessionRequestStatus.PENDING) {
            throw state("Đề nghị đã được xử lý.");
        }
        SessionRequestDecision decision = SessionRequestDecision.from(body);
        boolean approved = decision.status() == SessionRequestStatus.APPROVED;
        if (approved) {
            if (decision.action() != record.action()) {
                throw invalid("Loại điều chỉnh phải khớp với đề nghị.");
            }
            change(actor, record.sessionId(), body, requestId);
        }
        repository.decideRequest(requestId, decision.status(), decision.reason());
        notifications.notifyStaffProfile(record.staffId(), (approved
                ? "Đề nghị đổi lịch đã được xử lý. " : "Đề nghị đổi lịch đã được trả lại. ") + decision.reason());
        audit.record(actor, decision.status().name(), "session_change_requests", requestId);
    }
}
