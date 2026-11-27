package vn.huce.service;

import vn.huce.model.SchedulingSection;
import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import vn.huce.model.AvailabilityEntry.Kind;
import vn.huce.service.SchedulingService.Context;
import vn.huce.service.SchedulingService.Slot;
import vn.huce.service.SchedulingService.Span;

import static vn.huce.service.SchedulingService.day;
import static vn.huce.service.SchedulingService.ids;
import static vn.huce.service.SchedulingService.nullableId;
import static vn.huce.validation.Inputs.n;

/** In-memory constraints shared by manual scheduling and the genetic solver. */
final class ScheduleConstraints {
    private ScheduleConstraints() {
    }

    static boolean fits(SchedulingRoom room, SchedulingSection section) {
        return section != null && fits(room, section.capacity(), section.requiredRoomType());
    }

    static boolean fits(SchedulingRoom room, int capacity, String requiredRoomType) {
        return room != null && "ACTIVE".equals(room.status())
                && room.capacity() >= capacity && Objects.equals(room.type(), requiredRoomType);
    }

    static Set<Long> staff(Context context, Slot slot) {
        Set<Long> members = new java.util.LinkedHashSet<>(context.sections().get(slot.section()).assistantIds());
        if (slot.staff() != null) {
            members.add(slot.staff());
        }
        return members;
    }

    static Map<String, Object> issue(String rule, String message, SchedulingSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rule", rule);
        result.put("message", message);
        if (section != null) {
            result.put("sectionCode", section.code());
        }
        return result;
    }

    static Map<String, Object> pairIssue(Context context, Slot other, String rule, String message) {
        Map<String, Object> result = issue(rule, message, context.sections().get(other.section()));
        result.put("patternId", other.id());
        result.put("roomCode", context.rooms().get(other.room()).code());
        result.put("dayOfWeek", other.day());
        result.put("periodFrom", other.first());
        result.put("periodCount", other.count());
        return result;
    }

    static List<Map<String, Object>> pair(Context context, Slot first, Slot second) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (first.day() != second.day()) {
            return result;
        }
        LocalDate from = first.from().isAfter(second.from()) ? first.from() : second.from();
        LocalDate to = first.to().isBefore(second.to()) ? first.to() : second.to();
        boolean sameSection = first.section() == second.section();
        boolean sameRoom = first.room() == second.room();
        boolean sameStaff = !Collections.disjoint(staff(context, first), staff(context, second));
        Set<Long> groups = context.sections().get(first.section()).groups().keySet();
        SchedulingSection otherSection = context.sections().get(second.section());
        boolean sameGroup = !Collections.disjoint(groups, otherSection.groups().keySet());
        if (!sameSection && !sameRoom && !sameStaff && !sameGroup) {
            return result;
        }
        long firstCampus = context.rooms().get(first.room()).campusId();
        long secondCampus = context.rooms().get(second.room()).campusId();
        boolean teaches = false;
        boolean overlap = false;
        for (LocalDate date = ScheduleCalendar.firstDay(from, first.day()); !date.isAfter(to); date = date.plusWeeks(1)) {
            if (ScheduleCalendar.holiday(context, firstCampus, date)
                    || ScheduleCalendar.holiday(context, secondCampus, date)) {
                continue;
            }
            teaches = true;
            if (firstCampus == secondCampus) {
                overlap = first.first() <= second.last() && second.first() <= first.last();
                break;
            }
            Span a = ScheduleCalendar.interval(context, first, date);
            Span b = ScheduleCalendar.interval(context, second, date);
            if (a != null && b != null && a.start().isBefore(b.end()) && b.start().isBefore(a.end())) {
                overlap = true;
                break;
            }
        }
        if (!teaches) {
            return result;
        }
        if (sameSection) {
            result.add(pairIssue(context, second, "BR09", "Lớp đã có buổi khác trong ngày."));
        }
        if (overlap) {
            if (sameRoom) {
                result.add(pairIssue(context, second, "BR04", "Phòng đã được xếp cho lớp khác."));
            }
            if (sameStaff) {
                result.add(pairIssue(context, second, "BR05", "Giảng viên đã có lịch dạy."));
            }
            if (sameGroup) {
                var conflict = pairIssue(context, second, "BR06", "Lớp hành chính đối tượng đã có lịch.");
                List<String> shared = otherSection.groups().entrySet().stream()
                        .filter(group -> groups.contains(group.getKey())).map(Map.Entry::getValue).toList();
                conflict.put("groupCodes", shared);
                result.add(conflict);
            }
        }
        return result;
    }

    static List<Map<String, Object>> problems(Context context, Slot slot) {
        SchedulingSection section = context.sections().get(slot.section());
        if (section == null || slot.staff() == null
                || !Objects.equals(slot.staff(), section.staffId())) {
            return List.of(issue("BR02", "Lớp chưa có giảng viên chính hợp lệ.", section));
        }
        if (!fits(context.rooms().get(slot.room()), section)) {
            return List.of(issue("BR07", "Phòng phải hoạt động, đúng loại và đủ sức chứa.", section));
        }
        if (slot.from().isBefore(section.startDate())
                || slot.to().isAfter(section.endDate()) || slot.from().isAfter(slot.to())
                || ScheduleCalendar.firstDay(slot.from(), slot.day()).isAfter(slot.to())
                || ScheduleCalendar.occurrences(slot).stream().anyMatch(date -> ScheduleCalendar.interval(context, slot, date) == null)) {
            return List.of(issue("BR03", "Tiết học phải nằm trọn trong một ca và trong khoảng ngày của lớp.", section));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        if (busy(context, slot, Kind.BUSY)) {
            result.add(issue("BR08", "Giảng viên đã khai báo bận.", section));
        }
        for (Slot other : context.patterns()) {
            if (slot.id() == 0 || slot.id() != other.id()) {
                result.addAll(pair(context, slot, other));
            }
        }
        return result;
    }

    static boolean busy(Context context, Slot slot, Kind kind) {
        SchedulingSection section = context.sections().get(slot.section());
        Set<Long> members = staff(context, slot);
        return context.availability().stream().anyMatch(row -> members.contains(row.staffId())
                && row.semesterId() == section.semesterId()
                && row.dayOfWeek() == slot.day() && row.kind() == kind
                && row.overlaps(slot.first(), slot.last()));
    }
}
