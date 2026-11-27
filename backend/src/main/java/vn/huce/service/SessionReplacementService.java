package vn.huce.service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.AvailabilityEntry.Kind;
import vn.huce.model.SchedulingRoom;
import vn.huce.model.SemesterDateRange;
import vn.huce.model.SessionAdjustmentSnapshot;
import vn.huce.model.SessionChangeAction;
import vn.huce.model.SessionSectionSnapshot;
import vn.huce.repository.SessionChangeRepository;

import static vn.huce.service.SchedulingService.Context;
import static vn.huce.service.SchedulingService.Slot;
import static vn.huce.service.SchedulingService.Span;
import static vn.huce.validation.Inputs.date;
import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.invalid;
import static vn.huce.validation.Inputs.number;

/** Validates and inserts replacement sessions within the existing adjustment transaction. */
@Service
class SessionReplacementService {
    private final SessionChangeRepository repository;
    private final SchedulingService scheduling;

    SessionReplacementService(SessionChangeRepository repository, SchedulingService scheduling) {
        this.repository = repository;
        this.scheduling = scheduling;
    }

    private ApiFailure conflict(Object details) {
        return new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", details);
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
            SessionSectionSnapshot section, SessionAdjustmentSnapshot original, SessionChangeAction action, long exclude) {
        var allowed = scheduling.campuses(actor);
        List<Map<String, Object>> result = new ArrayList<>();
        boolean roomChange = action == SessionChangeAction.ROOM_CHANGE;
        int first = roomChange ? slot.first() : 1;
        int last = roomChange ? slot.first() : 15;
        for (int period = first; period <= last; period++) {
            for (SchedulingRoom room : context.rooms().values()) {
                long campusId = room.campusId();
                long roomId = room.id();
                if (!allowed.contains(campusId) || !scheduling.fits(room, section.capacity(), section.requiredRoomType())
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

    long createReplacement(Actor actor, long sessionId, Context context, SessionAdjustmentSnapshot original,
            SessionSectionSnapshot section, LocalDate originalDate, SessionChangeAction action, Map<String, Object> body) {
        SchedulingRoom room = room(context, id(body.get("roomId")));
        long roomId = room.id();
        long campusId = room.campusId();
        scheduling.requireCampus(actor, campusId);
        boolean roomChange = action == SessionChangeAction.ROOM_CHANGE;
        LocalDate replacementDate = roomChange ? originalDate : LocalDate.parse(date(body.get("date")));
        SemesterDateRange semester = repository.semesterDates(section.semesterId());
        if (!semester.contains(replacementDate)) {
            throw invalid("Ngày học bù phải nằm trong học kỳ.");
        }
        if (scheduling.holiday(context, campusId, replacementDate)) {
            throw invalid("Ngày đã chọn là ngày không học tại cơ sở.");
        }
        int period = roomChange ? original.periodFrom() : (int) number(body.get("periodFrom"), 1, 15);
        if (!scheduling.fits(room, section.capacity(), section.requiredRoomType())) {
            throw invalid("Phòng thay thế phải đủ chỗ và phù hợp học phần.");
        }
        if (roomChange && Objects.equals(original.roomId(), roomId)) {
            throw invalid("Cần chọn phòng khác.");
        }
        Slot slot = new Slot(0, section.id(), original.staffId(), roomId,
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
}
