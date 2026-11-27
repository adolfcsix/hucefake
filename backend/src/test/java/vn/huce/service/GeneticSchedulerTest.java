package vn.huce.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SchedulingRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static vn.huce.service.SchedulingService.*;

class GeneticSchedulerTest {
    private final SchedulingRepository repository = mock(SchedulingRepository.class);
    private final SchedulingService scheduling = new SchedulingService(repository);
    private final GeneticInputBuilder builder = new GeneticInputBuilder(scheduling, repository);
    private final GeneticScheduler scheduler = new GeneticScheduler(scheduling, builder);
    private final LocalDate monday = LocalDate.of(2026, 11, 30);

    private Slot slot(int day, int first) {
        return new Slot(0L, 1L, 2L, 3L, day, first, 2, monday, monday.plusWeeks(2));
    }

    private Context context(List<Map<String, Object>> availability) {
        return new Context(Map.of(1L, Map.of("id", 1L, "semester_id", 4L, "staff_id", 2L,
                        "capacity", 30, "assistant_ids", new Long[0], "group_ids", new Long[0])),
                Map.of(3L, Map.of("id", 3L, "campus_id", 5L, "capacity", 60)),
                List.of(), availability, List.of(), List.of());
    }

    @Test
    void preservesDefaultParametersAndRejectsInvalidRate() {
        var settings = GeneticScheduler.parameters(Map.of("seed", 42));
        assertEquals(100, settings.population());
        assertEquals(500, settings.generations());
        assertEquals(0.9, settings.crossover());
        assertEquals(0.02, settings.mutation());
        assertEquals(42, settings.seed());
        assertThrows(ApiFailure.class, () -> GeneticScheduler.parameters(Map.of("mutation", 1.1)));
    }

    @Test
    void fitnessKeepsBusyAndAvoidWeightsAndRoomCapacityPenalty() {
        var availability = List.of(
                Map.<String, Object>of("staff_id", 2L, "semester_id", 4L, "day_of_week", 1,
                        "period_from", 1, "period_to", 1, "kind", "BUSY"),
                Map.<String, Object>of("staff_id", 2L, "semester_id", 4L, "day_of_week", 1,
                        "period_from", 2, "period_to", 2, "kind", "AVOID"));
        Slot candidate = slot(1, 1);
        var input = new GeneticScheduler.Input(context(availability), List.of(List.of(candidate)));
        var score = new GeneticFitnessEvaluator(input, scheduling).score(List.of(candidate));
        assertEquals(1, score.hard());
        assertEquals(10.0, score.soft(), 1e-9);
    }

    @Test
    void fixedSeedProducesSameOutcomeForSmallInput() {
        var input = new GeneticScheduler.Input(context(List.of()), List.of(List.of(slot(1, 1), slot(2, 1))));
        var settings = new GeneticScheduler.Parameters(8, 5, 0.9, 0.02, 42);
        var firstProgress = new ArrayList<GeneticScheduler.Progress>();
        var secondProgress = new ArrayList<GeneticScheduler.Progress>();
        var first = scheduler.solve(input, settings, firstProgress::add);
        var second = scheduler.solve(input, settings, secondProgress::add);
        assertEquals(first, second);
        assertEquals(firstProgress, secondProgress);
        assertEquals(0, first.score().hard());
    }

    @Test
    void targetsRespectSemesterUnitStatusAndRemainingSessions() {
        Map<String, Object> a = Map.of("id", 1L, "semester_id", 4L, "status", "DRAFT",
                "managing_unit_id", 6L, "scheduled", 0, "sessions_per_week", 2);
        Map<String, Object> b = Map.of("id", 2L, "semester_id", 4L, "status", "DRAFT",
                "managing_unit_id", 7L, "scheduled", 0, "sessions_per_week", 2);
        Map<String, Object> c = Map.of("id", 3L, "semester_id", 4L, "status", "DRAFT",
                "managing_unit_id", 6L, "scheduled", 2, "sessions_per_week", 2);
        Context context = new Context(Map.of(1L, a, 2L, b, 3L, c), Map.of(),
                List.of(), List.of(), List.of(), List.of());
        Actor staff = new Actor(1L, "staff", "STAFF", 2L, 6L);
        Actor manager = new Actor(2L, "manager", "MANAGER", null, null);
        assertEquals(List.of(a), builder.targets(context, staff, 4L));
        assertEquals(Set.of(a, b), Set.copyOf(builder.targets(context, manager, 4L)));
    }
}
