package vn.huce.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import vn.huce.repository.SchedulingRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static vn.huce.service.GeneticScheduler.*;
import static vn.huce.service.SchedulingService.*;

class GeneticEvolutionTest {
    private final LocalDate date = LocalDate.of(2026, 11, 30);
    private final Slot firstA = slot(1L, 1);
    private final Slot firstB = slot(1L, 4);
    private final Slot secondA = slot(1L, 7);
    private final Slot secondB = slot(1L, 10);
    private final Input input = new Input(new Context(Map.of(), Map.of(),
            List.of(), List.of(), List.of(), List.of()),
            List.of(List.of(secondA), List.of(secondB)));
    private final GeneticFitnessEvaluator evaluator =
            new GeneticFitnessEvaluator(input, new SchedulingService(mock(SchedulingRepository.class)));

    private Slot slot(long sectionId, int period) {
        return new Slot(0L, sectionId, 2L, 3L, 1, period, 1, date, date);
    }

    private Score firstParent() {
        return new Score(List.of(firstA, firstB), 0, 0);
    }

    private Score secondParent() {
        return new Score(List.of(secondA, secondB), 0, 0);
    }

    @Test
    void noCrossoverAndNoMutationPreserveFirstParent() {
        var evolution = new GeneticEvolution(input, evaluator, new Random(42));
        assertEquals(firstParent().slots(), evolution.recombine(firstParent(), secondParent(),
                new Parameters(8, 5, 0, 0, 42)));
    }

    @Test
    void fullMutationSamplesOnlyEachGenesOwnDomain() {
        var evolution = new GeneticEvolution(input, evaluator, new Random(42));
        assertEquals(secondParent().slots(), evolution.recombine(firstParent(), secondParent(),
                new Parameters(8, 5, 1, 1, 42)));
    }

    @Test
    void crossoverKeepsSameSectionsGenesTogetherWithoutMutation() {
        for (int seed = 0; seed < 20; seed++) {
            var evolution = new GeneticEvolution(input, evaluator, new Random(seed));
            var result = evolution.recombine(firstParent(), secondParent(),
                    new Parameters(8, 5, 1, 0, seed));
            assertTrue(result.equals(firstParent().slots()) || result.equals(secondParent().slots()));
        }
    }

    @Test
    void scoreOrderingPrioritizesHardViolationsBeforeSoftPenalty() {
        var feasible = new Score(List.of(firstA), 0, 1000);
        var infeasible = new Score(List.of(firstA), 1, 0);
        var better = new Score(List.of(firstA), 0, 5);
        assertTrue(GeneticEvolution.ORDER.compare(feasible, infeasible) < 0);
        assertTrue(GeneticEvolution.ORDER.compare(better, feasible) < 0);
    }

    @Test
    void tournamentSelectsBestOfThreeSeededSamples() {
        var population = List.of(new Score(List.of(firstA), 3, 0),
                new Score(List.of(firstB), 0, 4), new Score(List.of(secondA), 0, 2));
        Random reference = new Random(71);
        Score expected = population.get(reference.nextInt(population.size()));
        for (int i = 0; i < 2; i++) {
            Score candidate = population.get(reference.nextInt(population.size()));
            if (GeneticEvolution.ORDER.compare(candidate, expected) < 0) {
                expected = candidate;
            }
        }
        var evolution = new GeneticEvolution(input, evaluator, new Random(71));
        assertEquals(expected, evolution.tournament(population));
    }
}
