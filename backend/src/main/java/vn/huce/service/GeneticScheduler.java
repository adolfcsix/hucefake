package vn.huce.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import vn.huce.model.AvailabilityEntry.Kind;
import vn.huce.Accounts.Actor;
import vn.huce.service.SchedulingService.Context;
import vn.huce.service.SchedulingService.Slot;

import static vn.huce.validation.Inputs.*;

@Service
public class GeneticScheduler {
    private final SchedulingService scheduling;
    private final GeneticInputBuilder inputBuilder;

    GeneticScheduler(SchedulingService scheduling, GeneticInputBuilder inputBuilder) {
        this.scheduling = scheduling;
        this.inputBuilder = inputBuilder;
    }

    public record Parameters(int population, int generations, double crossover, double mutation, int seed) {
    }

    record Input(Context ctx, List<List<Slot>> genes) {
    }

    record Score(List<Slot> slots, int hard, double soft) {
    }

    record Progress(int generation, int hard, double soft) {
    }

    record Outcome(Score score, int generation, List<String> notes) {
    }

    public static Parameters parameters(Object raw) {
        if (raw != null && !(raw instanceof Map<?, ?>)) {
            throw invalid("Tham số không hợp lệ.");
        }
        Map<?, ?> parameters = raw == null ? Map.of() : (Map<?, ?>) raw;
        return new Parameters(
                (int) number(or(parameters, "population", 100), 8, 200),
                (int) number(or(parameters, "generations", 500), 1, 1000),
                rate(or(parameters, "crossover", 0.9)),
                rate(or(parameters, "mutation", 0.02)),
                (int) number(or(parameters, "seed", new SecureRandom().nextInt()), Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    static Object or(Map<?, ?> parameters, String key, Object fallback) {
        Object value = parameters.get(key);
        return value == null ? fallback : value;
    }

    static double rate(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > 1) {
            throw invalid("Xác suất phải nằm trong khoảng 0 đến 1.");
        }
        return number.doubleValue();
    }

    List<Map<String, Object>> targets(Context context, Actor actor, long semesterId) {
        return inputBuilder.targets(context, actor, semesterId);
    }

    static Slot make(Map<String, Object> section, long roomId, int dayOfWeek, int periodFrom) {
        return GeneticInputBuilder.make(section, roomId, dayOfWeek, periodFrom);
    }

    Input input(Actor actor, long semesterId) {
        return inputBuilder.build(actor, semesterId);
    }

    Outcome solve(Input input, Parameters settings, Consumer<Progress> publish) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        long softDeadline = deadline - TimeUnit.SECONDS.toNanos(15);
        Random random = new Random(settings.seed());
        GeneticFitnessEvaluator evaluator = new GeneticFitnessEvaluator(input, scheduling);
        GeneticEvolution evolution = new GeneticEvolution(input, evaluator, random);
        List<Score> population = new ArrayList<>();
        for (int index = 0; index < settings.population() && System.nanoTime() < deadline; index++) {
            population.add(evolution.initial(index < settings.population() / 5, deadline));
        }
        if (population.isEmpty()) {
            throw state("Không còn thời gian tạo quần thể.");
        }

        population.sort(GeneticEvolution.ORDER);
        Score best = population.getFirst();
        int generation = 0;
        int stagnant = 0;
        publish.accept(new Progress(0, best.hard(), best.soft()));

        while (generation < settings.generations() && stagnant < 100
                && (best.hard() > 0 || best.soft() > 0)
                && System.nanoTime() < (best.hard() > 0 ? deadline : softDeadline)) {
            List<Score> next = new ArrayList<>(population.subList(0, Math.min(2, population.size())));
            while (next.size() < settings.population()
                    && System.nanoTime() < (best.hard() > 0 ? deadline : softDeadline)) {
                next.add(evolution.offspring(population, settings, deadline));
            }
            next.sort(GeneticEvolution.ORDER);
            population = next;
            generation++;
            if (GeneticEvolution.ORDER.compare(population.getFirst(), best) < 0) {
                best = population.getFirst();
                stagnant = 0;
            } else {
                stagnant++;
            }
            if (generation % 5 == 0) {
                publish.accept(new Progress(generation, best.hard(), best.soft()));
            }
        }

        if (System.nanoTime() >= deadline && best.hard() > 0) {
            throw state("Vượt giới hạn 60 giây.");
        }
        publish.accept(new Progress(generation, best.hard(), best.soft()));
        return new Outcome(best, generation, violationNotes(input, best, evaluator));
    }

    private List<String> violationNotes(Input input, Score best, GeneticFitnessEvaluator evaluator) {
        List<String> notes = new ArrayList<>();
        for (int index = 0; index < best.slots().size(); index++) {
            Slot slot = best.slots().get(index);
            Set<String> messages = new LinkedHashSet<>();
            for (Slot fixed : input.ctx().patterns()) {
                for (Map<String, Object> issue : scheduling.pair(input.ctx(), slot, fixed)) {
                    messages.add(s(issue, "message"));
                }
            }
            for (int other = 0; other < best.slots().size(); other++) {
                if (index != other) {
                    for (Map<String, Object> issue : scheduling.pair(input.ctx(), slot, best.slots().get(other))) {
                        messages.add(s(issue, "message"));
                    }
                }
            }
            if (evaluator.busy(slot, Kind.BUSY > 0) {
                messages.add("Giảng viên bận trong khung tiết.");
            }
            String message = String.join(" ", messages);
            notes.add(message.isEmpty() ? null : message.substring(0, Math.min(1000, message.length())));
        }
        return notes;
    }
}
