package vn.huce.service;

import vn.huce.model.SchedulingSection;
import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.SchedulePatternSnapshot;
import vn.huce.model.TeachingPeriod;
import vn.huce.model.NonTeachingDay;
import vn.huce.model.StaffAvailability;
import vn.huce.model.AvailabilityEntry.Kind;
import vn.huce.repository.SchedulingRepository;

import static vn.huce.validation.Inputs.date;
import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.invalid;
import static vn.huce.validation.Inputs.n;
import static vn.huce.validation.Inputs.number;

@Service
public class SchedulingService {
    private static final int MAX_SUGGESTIONS = 5;
    private final SchedulingRepository repository;

    public SchedulingService(SchedulingRepository repository) {
        this.repository = repository;
    }

    public record Slot(long id, long section, Long staff, long room, int day, int first,
                       int count, LocalDate from, LocalDate to) {
        public int last() {
            return first + count - 1;
        }

        Slot move(long roomId, int dayOfWeek, int periodFrom) {
            return new Slot(id, section, staff, roomId, dayOfWeek, periodFrom, count, from, to);
        }
    }

    public record Span(LocalDateTime start, LocalDateTime end) {
    }

    public record Context(Map<Long, SchedulingSection> sections, Map<Long, SchedulingRoom> rooms,
                          List<TeachingPeriod> periods, List<StaffAvailability> availability,
                          List<NonTeachingDay> holidays, List<Slot> patterns) {
    }

    public record Result(boolean valid, List<Map<String, Object>> issues, List<Map<String, Object>> suggestions) {
    }

    public static LocalDate day(Object value) {
        return LocalDate.parse(value.toString());
    }

    public static Long nullableId(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public static Set<Long> ids(Object value) {
        Set<Long> result = new LinkedHashSet<>();
        if (value instanceof Object[] array) {
            for (Object item : array) {
                result.add(nullableId(item));
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                result.add(nullableId(item));
            }
        }
        return result;
    }

    public static Map<Long, Map<String, Object>> index(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(n(row, "id"), row);
        }
        return result;
    }

    public static LocalDate firstDay(LocalDate from, int dayOfWeek) {
        return ScheduleCalendar.firstDay(from, dayOfWeek);
    }

    public static List<LocalDate> occurrences(Slot slot) {
        return ScheduleCalendar.occurrences(slot);
    }

    private static Slot slot(SchedulePatternSnapshot pattern) {
        return new Slot(pattern.id(), pattern.sectionId(), pattern.staffId(), pattern.roomId(),
                pattern.dayOfWeek(), pattern.periodFrom(), pattern.periodCount(),
                pattern.effectiveFrom(), pattern.effectiveTo());
    }

    public Context context() {
        Map<Long, SchedulingRoom> rooms = new LinkedHashMap<>();
        for (SchedulingRoom room : repository.rooms()) {
            rooms.put(room.id(), room);
        }
        Map<Long, SchedulingSection> sections = new LinkedHashMap<>();
        for (SchedulingSection section : repository.activeSections()) {
            sections.put(section.id(), section);
        }
        return new Context(sections, rooms,
                repository.activePeriods(), repository.availability(), repository.holidays(),
                repository.draftPatterns().stream().map(SchedulingService::slot).toList());
    }

    public Set<Long> campuses(Actor actor) {
        return repository.allowedCampuses(actor);
    }

    public void requireCampus(Actor actor, long campusId) {
        if (!campuses(actor).contains(campusId)) {
            throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
        }
    }

    public Slot candidate(Actor actor, Context context, Map<String, Object> body,
                          Long priorId, Map<String, Object> section) {
        Slot prior = priorId == null ? null : context.patterns().stream()
                .filter(pattern -> pattern.id() == priorId)
                .findFirst().orElseThrow(() -> new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu."));
        if (prior != null && prior.section() != n(section, "id")) {
            throw invalid("Không thể chuyển lịch sang lớp khác.");
        }

        long roomId = id(body.get("roomId"));
        SchedulingRoom room = context.rooms().get(roomId);
        if (room == null) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        requireCampus(actor, room.campusId());
        if (prior != null) {
            requireCampus(actor, context.rooms().get(prior.room()).campusId());
        }

        LocalDate from = effectiveDate(body.get("effectiveFrom"), section.get("start_date"));
        LocalDate to = effectiveDate(body.get("effectiveTo"), section.get("end_date"));
        return new Slot(prior == null ? 0 : prior.id(), n(section, "id"),
                nullableId(section.get("staff_id")), roomId,
                (int) number(body.get("dayOfWeek"), 1, 7), (int) number(body.get("periodFrom"), 1, 15),
                (int) n(section, "periods_per_session"), from, to);
    }

    private LocalDate effectiveDate(Object value, Object fallback) {
        return value == null || "".equals(value) ? day(fallback) : day(date(value));
    }

    public boolean fits(SchedulingRoom room, Map<String, Object> section) {
        return section != null && ScheduleConstraints.fits(room, n(section, "capacity"),
                section.get("required_room_type"));
    }

    public boolean fits(SchedulingRoom room, SchedulingSection section) {
        return ScheduleConstraints.fits(room, section);
    }

    public Span interval(Context context, Slot slot, LocalDate date) {
        return ScheduleCalendar.interval(context, slot, date);
    }

    public boolean holiday(Context context, long campusId, LocalDate date) {
        return ScheduleCalendar.holiday(context, campusId, date);
    }

    public Set<Long> staff(Context context, Slot slot) {
        return ScheduleConstraints.staff(context, slot);
    }

    public Map<String, Object> issue(String rule, String message, SchedulingSection section) {
        return ScheduleConstraints.issue(rule, message, section);
    }

    public List<Map<String, Object>> pair(Context context, Slot first, Slot second) {
        return ScheduleConstraints.pair(context, first, second);
    }

    public List<Map<String, Object>> problems(Context context, Slot slot) {
        return ScheduleConstraints.problems(context, slot);
    }

    public boolean busy(Context context, Slot slot, Kind kind) {
        return ScheduleConstraints.busy(context, slot, kind);
    }

    public List<Map<String, Object>> actualConflicts(Context context, Slot slot, Span span, long excludedSessionId) {
        return repository.actualConflicts(slot.section(), slot.room(), staff(context, slot),
                span.start(), span.end(), excludedSessionId);
    }

    public List<Map<String, Object>> actual(Context context, Slot slot) {
        long campusId = context.rooms().get(slot.room()).campusId();
        boolean teaching = false;
        for (LocalDate date : occurrences(slot)) {
            if (holiday(context, campusId, date)) {
                continue;
            }
            teaching = true;
            var conflicts = actualConflicts(context, slot, interval(context, slot, date), 0);
            if (!conflicts.isEmpty()) {
                return conflicts;
            }
        }
        return teaching ? List.of() : List.of(issue("BR14",
                "Lịch định kỳ không có ngày học vì toàn bộ ngày đều nghỉ.", null));
    }

    public Result check(Context context, Slot slot, Actor actor, boolean suggest) {
        List<Map<String, Object>> issues = problems(context, slot);
        if (issues.isEmpty()) {
            issues = actual(context, slot);
        }
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (suggest && !issues.isEmpty() && slot.staff() != null) {
            suggestions = suggestAlternatives(context, slot, actor);
        }
        return new Result(issues.isEmpty(), issues, suggestions);
    }

    private List<Map<String, Object>> suggestAlternatives(Context context, Slot slot, Actor actor) {
        Set<Long> allowed = campuses(actor);
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (SchedulingRoom room : context.rooms().values()) {
            if (!allowed.contains(room.campusId()) || !fits(room, context.sections().get(slot.section()))) {
                continue;
            }
            for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
                for (int period = 1; period <= 15; period++) {
                    Slot candidate = slot.move(room.id(), dayOfWeek, period);
                    if (problems(context, candidate).isEmpty() && actual(context, candidate).isEmpty()) {
                        suggestions.add(Map.of("roomId", candidate.room(), "roomCode", room.code(),
                                "dayOfWeek", dayOfWeek, "periodFrom", period));
                        if (suggestions.size() == MAX_SUGGESTIONS) {
                            return suggestions;
                        }
                    }
                }
            }
        }
        return suggestions;
    }

    public void verify(Context context, Slot slot, Actor actor) {
        Result result = check(context, slot, actor, false);
        if (!result.valid()) {
            throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", result);
        }
    }

    public void recheck(long sectionId, Actor actor) {
        Context context = context();
        for (Slot slot : context.patterns()) {
            if (slot.section() == sectionId) {
                verify(context, slot, actor);
            }
        }
    }
}
