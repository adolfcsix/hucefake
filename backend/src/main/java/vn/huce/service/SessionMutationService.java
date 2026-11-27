package vn.huce.service;

import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import vn.huce.model.AvailabilityEntry.Kind;
import vn.huce.Accounts.Actor;
import vn.huce.dto.SessionChangeRequest;
import vn.huce.dto.SessionRequestDecision;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SessionChangeAction;
import vn.huce.model.SessionRequestStatus;
import vn.huce.model.SessionRequestSnapshot;
import vn.huce.model.SessionRequestResolution;
import vn.huce.model.SessionAdjustmentSnapshot;
import vn.huce.model.SessionLecturerScope;
import vn.huce.model.SemesterDateRange;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.repository.SessionChangeRepository;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;

@Service
public class SessionMutationService {
    private final SessionChangeRepository repository;
    private final SectionRepository sectionRepository;
    private final AuditRepository audit;
    private final SchedulingService scheduling;
    private final NotificationService notifications;
    private final AvailabilityService availabilityService;

    SessionMutationService(SessionChangeRepository repository, SectionRepository sectionRepository,
                           AuditRepository audit, SchedulingService scheduling, NotificationService notifications,
                           AvailabilityService availabilityService) {
        this.repository = repository;
        this.sectionRepository = sectionRepository;
        this.audit = audit;
        this.scheduling = scheduling;
        this.notifications = notifications;
        this.availabilityService = availabilityService;
    }

    private void forbidden() {
        throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }

    private ApiFailure conflict(Object details) {
        return new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", details);
    }

    private void campus(Actor actor, Object campusId) {
        if (campusId != null) {
            scheduling.requireCampus(actor, id(campusId));
        }
    }

    private SchedulingRoom room(Context context, long roomId) {
        SchedulingRoom room = context.rooms().get(roomId);
        if (room == null) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return room;
    }

    private boolean unchanged(Slot slot, Span time, SessionAdjustmentSnapshot original) {
        return Objects.equals(original.roomId(), slot.room())
                && time.start().equals(original.startAt());
    }

    private List<Map<String, Object>> alternatives(Actor actor, Context context, Slot slot,
            Map<String, Object> section, SessionAdjustmentSnapshot original, SessionChangeAction action, long exclude) {
        var allowed = scheduling.campuses(actor);
        List<Map<String, Object>> result = new ArrayList<>();
        boolean roomChange = action == SessionChangeAction.ROOM_CHANGE;
        int first = roomChange ? slot.first() : 1;
        int last = roomChange ? slot.first() : 15;
        for (int period = first; period <= last; period++) {
            for (SchedulingRoom room : context.rooms().values()) {
                long campusId = room.campusId();
                long roomId = room.id();
                if (!allowed.contains(campusId) || !scheduling.fits(room, section)
                        || scheduling.holiday(context, campusId, slot.from())
                        || (roomChange && Objects.equals(original.roomId(), roomId))) {
                    continue;
                }
                Slot proposed = slot.move(roomId, slot.day(), period);
                Span time = scheduling.interval(context, proposed, slot.from());
                if (time == null || scheduling.busy(context, proposed, Kind.BUSY) || unchanged(proposed, time, original)) {
                    continue;
                }
                if (scheduling.actualConflicts(context, proposed, time, exclude).isEmpty()) {
                    result.add(Map.of("roomId", roomId, "roomCode", room.code(),
                            "dayOfWeek", slot.day(), "periodFrom", period));
                }
                if (result.size() == 5) {
                    return result;
                }
            }
        }
        return result;
    }

    public Object change(Actor actor, long sessionId, Map<String, Object> body, Long requestId) {
        role(actor, "STAFF");
        SessionAdjustmentSnapshot original = repository.findSession(sessionId);
        Map<String, Object> section = sectionRepository.findSection(original.sectionId());
        unit(actor, section.get("managing_unit_id"));
        if (repository.hasException(sessionId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Buổi học đã có ngoại lệ.");
        }
        if (!"PLANNED".equals(original.status()) || !"OPEN".equals(section.get("status"))) {
            throw state("Chỉ điều chỉnh buổi học đang lên lịch của lớp đã mở.");
        }
        campus(actor, original.campusId());
        Context context = scheduling.context();
        SessionChangeRequest request = SessionChangeRequest.adjustment(body);
        LocalDate originalDate = original.startAt().toLocalDate();
        Long replacementId = null;
        if (request.action() != SessionChangeAction.CANCEL) {
            replacementId = createReplacement(actor, sessionId, context, original, section, originalDate, request.action(), body);
        }

        long exceptionId = repository.insertException(sessionId, originalDate, request.action(),
                replacementId, request.reason(), actor.id());
        repository.cancelSession(sessionId);
        audit.record(actor, request.action().name(), "session_exceptions", exceptionId);
        if (requestId == null) {
            closePendingRequests(actor, sessionId, request.action());
        }
        notifications.notifySection(n(section, "id"), "Lớp " + section.get("code")
                + " thay đổi lịch ngày " + originalDate + ". " + request.reason());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", exceptionId);
        response.put("replacementId", replacementId);
        return response;
    }

    private long createReplacement(Actor actor, long sessionId, Context context, SessionAdjustmentSnapshot original,
            Map<String, Object> section, LocalDate originalDate, SessionChangeAction action, Map<String, Object> body) {
        SchedulingRoom room = room(context, id(body.get("roomId")));
        long roomId = room.id();
        long campusId = room.campusId();
        scheduling.requireCampus(actor, campusId);
        boolean roomChange = action == SessionChangeAction.ROOM_CHANGE;
        LocalDate replacementDate = roomChange ? originalDate : LocalDate.parse(date(body.get("date")));
        SemesterDateRange semester = repository.semesterDates(n(section, "semester_id"));
        if (!semester.contains(replacementDate)) {
            throw invalid("Ngày học bù phải nằm trong học kỳ.");
        }
        if (scheduling.holiday(context, campusId, replacementDate)) {
            throw invalid("Ngày đã chọn là ngày không học tại cơ sở.");
        }
        int period = roomChange ? original.periodFrom() : (int) number(body.get("periodFrom"), 1, 15);
        if (!scheduling.fits(room, section)) {
            throw invalid("Phòng thay thế phải đủ chỗ và phù hợp học phần.");
        }
        if (roomChange && Objects.equals(original.roomId(), roomId)) {
            throw invalid("Cần chọn phòng khác.");
        }
        Slot slot = new Slot(0, n(section, "id"), original.staffId(), roomId,
                replacementDate.getDayOfWeek().getValue(), period, original.periodCount(),
                replacementDate, replacementDate);
        Span time = scheduling.interval(context, slot, replacementDate);
        if (time == null || scheduling.busy(context, slot, Kind.BUSY)) {
            throw invalid("Tiết thay thế qua ca hoặc rơi vào giờ bận.");
        }
        if (unchanged(slot, time, original)) {
            throw invalid("Giờ hoặc phòng thay thế phải khác buổi ban đầu.");
        }
        var issues = scheduling.actualConflicts(context, slot, time, sessionId);
        if (!issues.isEmpty()) {
            throw conflict(Map.of("issues", issues, "suggestions",
                    alternatives(actor, context, slot, section, original, action, sessionId)));
        }
        return repository.insertReplacement(slot.section(), slot.staff(), slot.room(), slot.first(), slot.count(),
                time.start(), time.end(), original.timetableVersionId(), sessionId);
    }

    private void closePendingRequests(Actor actor, long sessionId, SessionChangeAction action) {
        for (SessionRequestResolution request : repository.closePendingRequests(sessionId, action)) {
            audit.record(actor, request.status().name(), "session_change_requests", request.id());
            notifications.notifyStaffProfile(request.staffId(), request.reason());
        }
    }

    public List<Map<String, Object>> availability(Actor actor, long semesterId) {
        return availabilityService.find(actor, semesterId);
    }

    public void setAvailability(Actor actor, Map<String, Object> input) {
        availabilityService.replace(actor, input);
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
        Map<String, Object> section = sectionRepository.findSection(session.sectionId());
        if (!"PLANNED".equals(session.status()) || !"OPEN".equals(section.get("status"))) {
            throw state("Buổi học không còn ở trạng thái đã lên lịch.");
        }
        if (repository.hasPendingRequest(sessionId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Buổi học đã có đề nghị chờ xử lý.");
        }
        SessionChangeRequest request = SessionChangeRequest.lecturer(body);
        long requestId = repository.insertRequest(sessionId, actor.staffId(), request.action(), request.reason());
        audit.record(actor, "REQUEST_CHANGE", "class_sessions", sessionId);
        notifications.notifyScopedStaff(section.get("managing_unit_id"), session.roomId(),
                "Có đề nghị đổi lịch cho lớp " + section.get("code") + ". Mở Đề nghị đổi lịch để xử lý.");
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
