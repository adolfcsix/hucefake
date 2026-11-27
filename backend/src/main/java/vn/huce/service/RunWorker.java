package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.RunRepository;
import vn.huce.repository.MutationRepository;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.nullableId;

@Service
class RunWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunWorker.class);
    private final RunRepository repository;
    private final MutationRepository mutations;
    private final GeneticScheduler genetic;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    RunWorker(RunRepository repository, MutationRepository mutations, GeneticScheduler genetic, ObjectMapper json,
              PlatformTransactionManager manager) {
        this.repository = repository;
        this.mutations = mutations;
        this.genetic = genetic;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void poll() {
        Long claimedId = null;
        try {
            Map<String, Object> run = tx.execute(status -> {
                mutations.lock();
                repository.failInterrupted();
                return repository.claimNext();
            });
            if (run == null) {
                return;
            }
            claimedId = n(run, "id");
            final long runId = claimedId;
            GeneticScheduler.Parameters settings = json.readValue(s(run, "parameters_json"), GeneticScheduler.Parameters.class);
            GeneticScheduler.Input input = tx.execute(status -> {
                mutations.lock();
                Map<String, Object> user = repository.activeRequester(n(run, "requested_by"));
                Actor actor = new Actor(n(user, "id"), s(user, "username"), s(user, "role"),
                        nullableId(user.get("staff_id")), nullableId(user.get("unit_id")));
                role(actor, "STAFF", "MANAGER");
                return genetic.input(actor, n(run, "semester_id"));
            });
            GeneticScheduler.Outcome outcome = genetic.solve(input, settings, progress ->
                    tx.executeWithoutResult(status -> repository.recordProgress(runId,
                            progress.generation(), progress.hard(), progress.soft())));
            tx.executeWithoutResult(status -> saveOutcome(runId, outcome));
        } catch (Exception error) {
            LOGGER.warn("Schedule run failed", error);
            if (claimedId != null) {
                repository.fail(claimedId, error instanceof ApiFailure
                        ? error.getMessage() : "Không thể hoàn tất xếp lịch.");
            }
        }
    }

    private void saveOutcome(long runId, GeneticScheduler.Outcome outcome) {
        mutations.lock();
        if (!repository.running(runId)) {
            return;
        }
        GeneticScheduler.Score score = outcome.score();
        for (int index = 0; index < score.slots().size(); index++) {
            SchedulingService.Slot slot = score.slots().get(index);
            repository.insertItem(runId, slot.section(), slot.staff(), slot.room(),
                    slot.day(), slot.first(), slot.count(), outcome.notes().get(index));
        }
        double fitness = 1.0 / (1 + 1000.0 * score.hard() + score.soft());
        repository.complete(runId, outcome.generation(), score.hard(), score.soft(), fitness);
    }
}
