package vn.huce.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import vn.huce.service.GeneticScheduler.Input;
import vn.huce.service.GeneticScheduler.Parameters;
import vn.huce.service.GeneticScheduler.Score;
import vn.huce.service.SchedulingService.Slot;

/** Population operators. Random is supplied by the solver to preserve a single seeded sequence. */
final class GeneticEvolution {
    static final Comparator<Score> ORDER = Comparator.comparingInt(Score::hard)
            .thenComparingDouble(Score::soft);

    private final Input input;
    private final GeneticFitnessEvaluator evaluator;
    private final Random random;

    GeneticEvolution(Input input, GeneticFitnessEvaluator evaluator, Random random) {
        this.input = input;
        this.evaluator = evaluator;
        this.random = random;
    }

    private Slot sample(int index) {
        List<Slot> domain = input.genes().get(index);
        return domain.get(random.nextInt(domain.size()));
    }

    Score initial(boolean greedy, long deadline) {
        List<Slot> slots = new ArrayList<>();
        for (int index = 0; index < input.genes().size(); index++) {
            Slot best = sample(index);
            if (greedy) {
                var cost = evaluator.base(best);
                int hard = cost.hard();
                for (Slot other : slots) {
                    hard += evaluator.clash(best, other);
                }
                double soft = cost.soft();
                for (int trial = 0; trial < 32 && System.nanoTime() < deadline; trial++) {
                    Slot candidate = sample(index);
                    var base = evaluator.base(candidate);
                    int candidateHard = base.hard();
                    for (Slot other : slots) {
                        candidateHard += evaluator.clash(candidate, other);
                    }
                    if (candidateHard < hard || (candidateHard == hard && base.soft() < soft)) {
                        best = candidate;
                        hard = candidateHard;
                        soft = base.soft();
                    }
                }
            }
            slots.add(best);
        }
        return evaluator.score(slots);
    }

    Score tournament(List<Score> population) {
        Score best = population.get(random.nextInt(population.size()));
        for (int index = 0; index < 2; index++) {
            Score candidate = population.get(random.nextInt(population.size()));
            if (ORDER.compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    List<Slot> recombine(Score first, Score second, Parameters settings) {
        List<Slot> slots = new ArrayList<>();
        Map<Long, Boolean> groups = new HashMap<>();
        boolean cross = random.nextDouble() < settings.crossover();
        for (int index = 0; index < first.slots().size(); index++) {
            Slot slot = first.slots().get(index);
            if (random.nextDouble() < settings.mutation()) {
                slots.add(sample(index));
            } else {
                slots.add(cross && groups.computeIfAbsent(slot.section(), key -> random.nextBoolean())
                        ? second.slots().get(index) : slot);
            }
        }
        return slots;
    }

    Score offspring(List<Score> population, Parameters settings, long deadline) {
        Score first = tournament(population);
        Score second = tournament(population);
        List<Slot> slots = recombine(first, second, settings);
        Score child = evaluator.score(slots);
        if (child.hard() > 0 && random.nextDouble() < 0.3 && System.nanoTime() < deadline) {
            int index = random.nextInt(slots.size());
            for (int trial = 0; trial < 10 && System.nanoTime() < deadline; trial++) {
                List<Slot> candidate = new ArrayList<>(child.slots());
                candidate.set(index, sample(index));
                Score attempt = evaluator.score(candidate);
                if (ORDER.compare(attempt, child) < 0) {
                    child = attempt;
                }
            }
        }
        return child;
    }
}
