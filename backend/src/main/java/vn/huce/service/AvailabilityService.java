package vn.huce.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.AvailabilityEntry;
import vn.huce.repository.ApprovalRepository;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.AvailabilityRepository;

import static vn.huce.validation.Inputs.*;

@Service
public class AvailabilityService {
    private final AvailabilityRepository repository;
    private final ApprovalRepository approvals;
    private final AuditRepository audit;
    private final SchedulingService scheduling;

    public AvailabilityService(AvailabilityRepository repository, ApprovalRepository approvals,
                               AuditRepository audit, SchedulingService scheduling) {
        this.repository = repository;
        this.approvals = approvals;
        this.audit = audit;
        this.scheduling = scheduling;
    }

    public List<Map<String, Object>> find(Actor actor, long semesterId) {
        role(actor, "LECTURER");
        if (actor.staffId() == null) {
            throw forbidden();
        }
        return repository.find(actor.staffId(), semesterId);
    }

    // Called inside MutationService's transaction so a conflicting entry rolls back the replacement.
    public void replace(Actor actor, Map<String, Object> input) {
        role(actor, "LECTURER");
        if (actor.staffId() == null) {
            throw forbidden();
        }
        long semesterId = id(input.get("semesterId"));
        approvals.requireSemester(semesterId);
        if (approvals.hasSubmittedVersion(semesterId)) {
            throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");
        }
        List<AvailabilityEntry> entries = parseEntries(input.get("entries"));
        repository.replace(actor.staffId(), semesterId, entries);

        SchedulingService.Context context = scheduling.context();
        for (SchedulingService.Slot slot : context.patterns()) {
            Map<String, Object> section = context.sections().get(slot.section());
            if (scheduling.staff(context, slot).contains(actor.staffId())
                    && n(section, "semester_id") == semesterId && scheduling.busy(context, slot, "BUSY")) {
                throw conflict(Map.of("issues", List.of(scheduling.issue(
                        "BR08", "Đã có lịch giảng trong khoảng giờ bận.", section))));
            }
        }
        List<Map<String, Object>> actual = repository.findConflictingSessions(actor.staffId(), semesterId);
        if (!actual.isEmpty()) {
            throw conflict(Map.of("issues", actual));
        }
        audit.record(actor, "SET_AVAILABILITY", "semesters", semesterId);
    }

    private List<AvailabilityEntry> parseEntries(Object value) {
        if (!(value instanceof List<?> entries) || entries.size() > 100) {
            throw invalid("Tối đa 100 khoảng thời gian.");
        }
        List<AvailabilityEntry> result = new ArrayList<>();
        for (Object raw : entries) {
            if (!(raw instanceof Map<?, ?> entry)) {
                throw invalid("Khoảng thời gian không hợp lệ.");
            }
            long first = number(entry.get("periodFrom"), 1, 15);
            Object reason = entry.get("reason");
            result.add(new AvailabilityEntry(number(entry.get("dayOfWeek"), 1, 7), first,
                    number(entry.get("periodTo"), first, 15),
                    AvailabilityEntry.Kind.valueOf(choice(entry.get("kind"), "BUSY", "AVOID")),
                    reason == null || "".equals(reason) ? null : text(reason, 500)));
        }
        return result;
    }

    private ApiFailure forbidden() {
        return new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }

    private ApiFailure conflict(Object details) {
        return new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", details);
    }
}
