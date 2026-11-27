package vn.huce.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import vn.huce.service.GeneticScheduler.Input;
import vn.huce.service.GeneticScheduler.Score;
import static vn.huce.service.SchedulingService.*;
import static vn.huce.validation.Inputs.*;

/** Per-solve fitness evaluation and bounded pair-conflict cache. */
final class GeneticFitnessEvaluator {
    record Base(int hard, double soft, Set<Long> staff, String shift) {
    }

    private record Pair(Slot first, Slot second) {
    }

    private final Input input;
    private final SchedulingService scheduling;
    private final Map<Slot, Base> intrinsic = new HashMap<>();
    private final Map<Pair, Integer> pairs = new HashMap<>();

    GeneticFitnessEvaluator(Input input, SchedulingService scheduling) {
        this.input = input;
        this.scheduling = scheduling;
    }

    int busy(Slot slot, String kind) {
        Context context = input.ctx();
        Map<String, Object> section = context.sections().get(slot.section());
        Set<Long> staff = scheduling.staff(context, slot);
        int count = 0;
        for (int p = slot.first(); p <= slot.last(); p++) {
            final int period = p;
            if (context.availability().stream().anyMatch(row -> staff.contains(n(row, "staff_id"))
                    && n(row, "semester_id") == n(section, "semester_id")
                    && n(row, "day_of_week") == slot.day() && kind.equals(row.get("kind"))
                    && n(row, "period_from") <= period && n(row, "period_to") >= period)) {
                count++;
            }
        }
        return count;
    }

    int clash(Slot first, Slot second) {
        if (first.day() != second.day()) {
            return 0;
        }
        Pair key = new Pair(first, second);
        Integer cached = pairs.get(key);
        if (cached != null) {
            return cached;
        }
        int value = 0;
        for (Map<String, Object> issue : scheduling.pair(input.ctx(), first, second)) {
            value += "BR09".equals(issue.get("rule")) ? 1
                    : Math.max(1, Math.min(first.last(), second.last()) - Math.max(first.first(), second.first()) + 1);
        }
        if (pairs.size() < 250000) {
            pairs.put(key, value);
        }
        return value;
    }

    Base base(Slot candidate) {
        return intrinsic.computeIfAbsent(candidate, slot -> {
            Context context = input.ctx();
            Map<String, Object> room = context.rooms().get(slot.room());
            Map<String, Object> section = context.sections().get(slot.section());
            int hard = busy(slot, "BUSY");
            for (Slot fixed : context.patterns()) {
                hard += clash(slot, fixed);
            }
            double soft = 5.0 * busy(slot, "AVOID")
                    + 10.0 * (n(room, "capacity") - n(section, "capacity")) / n(room, "capacity");
            LocalDate date = firstDay(slot.from(), slot.day());
            String shift = context.periods().stream()
                    .filter(period -> n(period, "campus_id") == n(room, "campus_id")
                            && n(period, "period_no") == slot.first()
                            && !day(period.get("effective_from")).isAfter(date)
                            && (period.get("effective_to") == null || !day(period.get("effective_to")).isBefore(date)))
                    .findFirst().map(period -> s(period, "shift")).orElse("");
            return new Base(hard, soft, scheduling.staff(context, slot), shift);
        });
    }

    Score score(List<Slot> slots) {
        int hard = 0;
        double soft = 0;
        Map<String, Integer> daily = new HashMap<>();
        Map<String, TreeSet<Integer>> groupPeriods = new HashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            Base base = base(slot);
            hard += base.hard();
            soft += base.soft();
            for (int j = 0; j < i; j++) {
                Slot other = slots.get(j);
                hard += clash(slot, other);
                if (slot.section() == other.section() && Math.abs(slot.day() - other.day()) == 1) {
                    soft += 3;
                }
            }
            for (long staffId : base.staff()) {
                daily.merge(staffId + ":" + slot.day(), slot.count(), Integer::sum);
            }
            for (long groupId : ids(input.ctx().sections().get(slot.section()).get("group_ids"))) {
                String key = groupId + ":" + slot.day() + ":" + base.shift() + ":" + slot.from() + ":" + slot.to();
                TreeSet<Integer> periods = groupPeriods.computeIfAbsent(key, ignored -> new TreeSet<>());
                for (int period = slot.first(); period <= slot.last(); period++) {
                    periods.add(period);
                }
            }
        }
        for (int count : daily.values()) {
            soft += 4 * Math.max(0, count - 8);
        }
        for (TreeSet<Integer> periods : groupPeriods.values()) {
            soft += 2 * (periods.last() - periods.first() + 1 - periods.size());
        }
        return new Score(List.copyOf(slots), hard, soft);
    }
}
