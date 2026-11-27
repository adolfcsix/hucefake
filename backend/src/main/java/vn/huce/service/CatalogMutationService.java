package vn.huce.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.HolidayDraft;
import vn.huce.model.PeriodDraft;
import vn.huce.model.RoomDraft;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.CatalogMutationRepository;

import static vn.huce.validation.Inputs.*;

@Service
public class CatalogMutationService {
    private final CatalogMutationRepository repository;
    private final AuditRepository audit;
    private final SchedulingService scheduling;

    public CatalogMutationService(CatalogMutationRepository repository, AuditRepository audit,
                                  SchedulingService scheduling) {
        this.repository = repository;
        this.audit = audit;
        this.scheduling = scheduling;
    }

    public Object room(Actor actor, Map<String, Object> input, Long roomId) {
        role(actor, "STAFF");
        long campusId = id(input.get("campusId"));
        scheduling.requireCampus(actor, campusId);
        RoomDraft draft = new RoomDraft(campusId,
                text(input.get("code"), 30).toUpperCase(Locale.ROOT),
                text(input.get("name"), 100), number(input.get("capacity"), 1, 10000),
                choice(input.get("type"), "LECTURE", "COMPUTER_LAB", "LAB", "STUDIO"),
                choice(input.get("status"), "ACTIVE", "MAINTENANCE", "INACTIVE"));
        Map<String, Object> result;
        if (roomId == null) {
            result = repository.insertRoom(draft);
        } else {
            Map<String, Object> current = repository.findRoom(roomId);
            if (n(current, "campus_id") != campusId) {
                throw invalid("Không thể chuyển cơ sở của phòng đã lưu.");
            }
            List<Map<String, Object>> issues = repository.affectedRoomSessions(roomId, draft);
            List<Map<String, Object>> patterns = repository.affectedRoomPatterns(roomId, draft);
            if (!issues.isEmpty() || !patterns.isEmpty()) {
                throw conflict(Map.of("issues", issues, "patterns", patterns));
            }
            result = repository.updateRoom(roomId, draft);
        }
        audit.record(actor, "SAVE", "teaching_rooms", n(result, "id"));
        return result;
    }

    private void holidayScope(Actor actor, Long campusId) {
        if (campusId != null) {
            scheduling.requireCampus(actor, campusId);
            return;
        }
        if (scheduling.campuses(actor).size() != repository.activeCampusCount()) {
            throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
        }
    }

    public Object holiday(Actor actor, Map<String, Object> input, Long holidayId) {
        role(actor, "STAFF");
        Long campusId = input.get("campusId") == null ? null : id(input.get("campusId"));
        holidayScope(actor, campusId);
        HolidayDraft draft = new HolidayDraft(campusId,
                LocalDate.parse(date(input.get("startDate"))), LocalDate.parse(date(input.get("endDate"))),
                text(input.get("name"), 150));
        if (draft.startDate().isAfter(draft.endDate())) {
            throw invalid("Khoảng ngày không hợp lệ.");
        }
        List<Map<String, Object>> issues = repository.affectedHolidaySessions(draft);
        if (!issues.isEmpty()) {
            throw conflict(Map.of("issues", issues));
        }
        Map<String, Object> result;
        if (holidayId != null) {
            Map<String, Object> current = repository.findHoliday(holidayId);
            holidayScope(actor, SchedulingService.nullableId(current.get("campus_id")));
            result = repository.updateHoliday(holidayId, draft);
        } else {
            result = repository.insertHoliday(draft, actor.id());
        }
        audit.record(actor, "SAVE", "non_teaching_days", n(result, "id"));
        return result;
    }

    public Object createPeriod(Actor actor, Map<String, Object> input) {
        role(actor, "STAFF");
        PeriodDraft draft = new PeriodDraft(id(input.get("campusId")), number(input.get("periodNo"), 1, 15),
                LocalDate.parse(date(input.get("effectiveFrom"))), time(input.get("startTime")),
                time(input.get("endTime")), choice(input.get("shift"), "MORNING", "AFTERNOON", "EVENING"));
        scheduling.requireCampus(actor, draft.campusId());
        if (!draft.startTime().isBefore(draft.endTime())) {
            throw invalid("Giờ bắt đầu phải trước giờ kết thúc.");
        }
        Map<String, Object> current = repository.latestPeriod(draft);
        if (current != null && (!draft.effectiveFrom().isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                || !draft.effectiveFrom().isAfter(SchedulingService.day(current.get("effective_from"))))) {
            throw invalid("Khung tiết mới phải có ngày hiệu lực trong tương lai và sau khung hiện có.");
        }
        List<Map<String, Object>> issues = repository.affectedPeriodSessions(draft);
        if (!issues.isEmpty()) {
            throw conflict(Map.of("issues", issues));
        }
        if (repository.overlapsOtherPeriods(draft)) {
            throw invalid("Giờ của tiết mới chồng lấn hoặc sai thứ tự.");
        }
        if (current != null) {
            repository.closePeriod(n(current, "id"), draft);
        }
        Map<String, Object> result = repository.insertPeriod(draft);
        SchedulingService.Context context = scheduling.context();
        for (SchedulingService.Slot slot : context.patterns()) {
            if (n(context.rooms().get(slot.room()), "campus_id") == draft.campusId()
                    && draft.periodNumber() >= slot.first() && draft.periodNumber() <= slot.last()
                    && SchedulingService.occurrences(slot).stream().anyMatch(day -> !day.isBefore(draft.effectiveFrom()))) {
                scheduling.verify(context, slot, actor);
            }
        }
        audit.record(actor, "CREATE", "period_definitions", n(result, "id"));
        return result;
    }

    private LocalTime time(Object value) {
        if (!(value instanceof String text) || !text.matches("([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9])?")) {
            throw invalid("Giờ không hợp lệ.");
        }
        return LocalTime.parse(text);
    }

    private ApiFailure conflict(Object details) {
        return new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", details);
    }
}
