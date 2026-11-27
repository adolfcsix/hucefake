package vn.huce.service;

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

    static boolean fits(Map<String, Object> room, Map<String, Object> section) {
        return room != null && section != null && "ACTIVE".equals(room.get("status"))
                && n(room, "capacity") >= n(section, "capacity")
                && Objects.equals(room.get("type"), section.get("required_room_type"));
    }

    static Set<Long> staff(Context context, Slot slot) {
        Set<Long> members = ids(context.sections().get(slot.section()).get("assistant_ids"));
        if (slot.staff() != null) {
            members.add(slot.staff());
        }
        return members;
    }

    static Map<String, Object> issue(String rule, String message, Map<String, Object> section) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rule", rule);
        result.put("message", message);
        if (section != null) {
            result.put("sectionCode", section.get("code"));
        }
        return result;
    }

    static Map<String, Object> pairIssue(Context context, Slot other, String rule, String message) {
        Map<String, Object> result = issue(rule, message, context.sections().get(other.section()));
        result.put("patternId", other.id());
        result.put("roomCode", context.rooms().get(other.room()).get("code"));
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
        Set<Long> groups = ids(context.sections().get(first.section()).get("group_ids"));
        Map<String, Object> otherSection = context.sections().get(second.section());
        boolean sameGroup = !Collections.disjoint(groups, ids(otherSection.get("group_ids")));
        if (!sameSection && !sameRoom && !sameStaff && !sameGroup) {
            return result;
        }
        long firstCampus = n(context.rooms().get(first.room()), "campus_id");
        long secondCampus = n(context.rooms().get(second.room()), "campus_id");
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
                Object[] otherIds = (Object[]) otherSection.get("group_ids");
                Object[] otherCodes = (Object[]) otherSection.get("group_codes");
                List<Object> shared = new ArrayList<>();
                for (int index = 0; index < otherIds.length; index++) {
                    if (groups.contains(nullableId(otherIds[index]))) {
                        shared.add(otherCodes[index]);
                    }
                }
                conflict.put("groupCodes", shared);
                result.add(conflict);
            }
        }
        return result;
    }

    static List<Map<String, Object>> problems(Context context, Slot slot) {
        Map<String, Object> section = context.sections().get(slot.section());
        if (section == null || slot.staff() == null
                || !Objects.equals(slot.staff(), nullableId(section.get("staff_id")))) {
            return List.of(issue("BR02", "Lớp chưa có giảng viên chính hợp lệ.", section));
        }
        if (!fits(context.rooms().get(slot.room()), section)) {
            return List.of(issue("BR07", "Phòng phải hoạt động, đúng loại và đủ sức chứa.", section));
        }
        if (slot.from().isBefore(day(section.get("start_date")))
                || slot.to().isAfter(day(section.get("end_date"))) || slot.from().isAfter(slot.to())
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
        Map<String, Object> section = context.sections().get(slot.section());
        Set<Long> members = staff(context, slot);
        return context.availability().stream().anyMatch(row -> members.contains(row.staffId())
                && row.semesterId() == n(section, "semester_id")
                && row.dayOfWeek() == slot.day() && row.kind() == kind
                && row.overlaps(slot.first(), slot.last()));
    }
}
