package vn.huce.service;

import vn.huce.model.SchedulingSection;
import vn.huce.model.SchedulingRoom;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.ScheduleRunIdResponse;
import vn.huce.dto.ScheduleRunApplyResponse;
import vn.huce.model.ScheduleRunItem;
import vn.huce.model.ScheduleRunStatus;
import vn.huce.model.ScheduleRunSnapshot;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.RunRepository;

import static vn.huce.validation.Inputs.*;
import static vn.huce.service.SchedulingService.*;

@Service
public class RunService {
    private final RunRepository repository;
    private final ApprovalRepository approvals;
    private final AuditRepository audit;
    private final SchedulingService scheduling;
    private final GeneticScheduler genetic;
    private final ObjectMapper json;

    RunService(RunRepository repository, ApprovalRepository approvals, AuditRepository audit,
               SchedulingService scheduling, GeneticScheduler genetic, ObjectMapper json) {
        this.repository = repository;
        this.approvals = approvals;
        this.audit = audit;
        this.scheduling = scheduling;
        this.genetic = genetic;
        this.json = json;
    }

    public Map<String, Object> get(Actor actor, long runId) {
        role(actor, "STAFF", "MANAGER");
        Map<String, Object> row = repository.find(runId);
        requireVisible(actor, runId, n(row, "requested_by"),
                ScheduleRunStatus.valueOf(s(row, "status")));
        Object raw = row.remove("parameters_json");
        try {
            row.put("parameters", json.readValue(raw.toString(), Object.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
        return row;
    }

    public ScheduleRunSnapshot snapshot(Actor actor, long runId) {
        role(actor, "STAFF", "MANAGER");
        ScheduleRunSnapshot run = repository.findSnapshot(runId);
        requireVisible(actor, runId, run.requestedBy(), run.status());
        return run;
    }

    private void requireVisible(Actor actor, long runId, long requester, ScheduleRunStatus status) {
        if ("STAFF".equals(actor.role()) && requester != actor.id()
                && (!status.visibleToOtherStaff() || !repository.visibleManagerRun(runId, actor.unitId()))) {
            throw forbidden();
        }
    }

    public void unlocked(long semesterId) {
        if (approvals.hasSubmittedVersion(semesterId)) {
            throw state("Học kỳ đang chờ duyệt.");
        }
    }

    public ScheduleRunIdResponse enqueue(Actor actor, long semesterId, Object rawParameters) {
        role(actor, "STAFF", "MANAGER");
        unlocked(semesterId);
        if (repository.active(semesterId)) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Học kỳ đang có một lần chạy xếp lịch.");
        }
        GeneticScheduler.Parameters parameters = GeneticScheduler.parameters(rawParameters);
        genetic.input(actor, semesterId);
        String encoded;
        try {
            encoded = json.writeValueAsString(parameters);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
        long runId = repository.enqueue(semesterId, actor.id(), encoded);
        audit.record(actor, "QUEUE", "schedule_runs", runId);
        return new ScheduleRunIdResponse(runId);
    }

    public ScheduleRunApplyResponse apply(Actor actor, long runId) {
        ScheduleRunSnapshot run = snapshot(actor, runId);
        ScheduleRunStatus status = run.status();
        if (status == ScheduleRunStatus.APPLIED) {
            return new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, null);
        }
        if (!run.applicable()) {
            throw state("Chỉ áp dụng được phương án hoàn tất với 0 vi phạm cứng.");
        }
        long semesterId = run.semesterId();
        unlocked(semesterId);
        Context context = scheduling.context();
        var expected = genetic.targets(context, actor, semesterId);
        Set<Long> expectedIds = new HashSet<>();
        for (SchedulingSection section : expected) {
            expectedIds.add(section.id());
        }
        List<Slot> plan = new ArrayList<>();
        List<Slot> fixed = new ArrayList<>(context.patterns());
        ApiFailure stale = new ApiFailure(409, "VERSION_CONFLICT",
                "Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.", Map.of("runId", runId));
        for (ScheduleRunItem item : repository.rawItems(runId)) {
            SchedulingSection section = context.sections().get(item.sectionId());
            if (section == null) {
                throw stale;
            }
            unit(actor, section.managingUnitId());
            if (!expectedIds.contains(section.id())
                    || !Objects.equals(section.staffId(), item.staffId())
                    || section.periodsPerSession() != item.periodCount()) {
                throw stale;
            }
            SchedulingRoom room = context.rooms().get(item.roomId());
            if (room == null) {
                throw stale;
            }
            scheduling.requireCampus(actor, room.campusId());
            Slot slot = GeneticScheduler.make(section, room.id(),
                    item.dayOfWeek(), item.periodFrom());
            Context test = new Context(context.sections(), context.rooms(), context.periods(),
                    context.availability(), context.holidays(), fixed);
            Result result = scheduling.check(test, slot, actor, false);
            if (!result.valid()) {
                throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.",
                        Map.of("issues", result.issues()));
            }
            plan.add(slot);
            fixed.add(slot);
        }
        for (SchedulingSection section : expected) {
            if (fixed.stream().filter(slot -> slot.section() == section.id()).count()
                    != section.sessionsPerWeek()) {
                throw stale;
            }
        }
        for (Slot slot : plan) {
            repository.insertPattern(runId, actor.id(), slot.section(), slot.staff(), slot.room(),
                    slot.day(), slot.first(), slot.count(), slot.from(), slot.to());
        }
        repository.markApplied(runId);
        audit.record(actor, "APPLY", "schedule_runs", runId);
        return new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, plan.size());
    }

    public void discard(Actor actor, long runId) {
        ScheduleRunSnapshot run = snapshot(actor, runId);
        if ("STAFF".equals(actor.role()) && run.requestedBy() != actor.id()) {
            throw forbidden();
        }
        if (!run.status().discardable()) {
            throw state("Lần chạy không thể bỏ ở trạng thái hiện tại.");
        }
        repository.discard(runId);
        audit.record(actor, "DISCARD", "schedule_runs", runId);
    }

    public Object active(Actor actor, String semesterId) {
        role(actor, "STAFF", "MANAGER");
        return Map.of("active", repository.active(id(semesterId)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object list(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "MANAGER");
        return repository.list(actor, id(query.get("semesterId")), query);
    }

    public Object detail(Actor actor, long runId) {
        Map<String, Object> run = get(actor, runId);
        run.put("progress", repository.progress(runId));
        return run;
    }

    public Object items(Actor actor, long runId) {
        get(actor, runId);
        return repository.items(runId);
    }

    private ApiFailure forbidden() {
        return new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }
}
