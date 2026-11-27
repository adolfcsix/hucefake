package vn.huce.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.ApprovalReadiness;
import vn.huce.model.ApprovalReadiness.BlockedSection;
import vn.huce.model.ApprovalReadiness.PatternConflict;
import vn.huce.model.ApprovalSnapshot;
import vn.huce.model.SchedulingRoom;
import vn.huce.model.SchedulingSection;
import vn.huce.repository.ApprovalRepository;

import static vn.huce.service.SchedulingService.Context;
import static vn.huce.service.SchedulingService.Result;
import static vn.huce.service.SchedulingService.Slot;

/** Assesses draft readiness and builds the snapshot used for submission, without writes. */
@Service
class ApprovalAssessmentService {
    private final ApprovalRepository repository;
    private final SchedulingService scheduling;

    ApprovalAssessmentService(ApprovalRepository repository, SchedulingService scheduling) {
        this.repository = repository;
        this.scheduling = scheduling;
    }

    record Assessment(ApprovalReadiness readiness, List<ApprovalSnapshot> snapshot) {
    }

    Assessment assess(Actor actor, long semesterId) {
        Context context = scheduling.context();
        var drafts = context.sections().values().stream()
                .filter(section -> section.semesterId() == semesterId
                        && "DRAFT".equals(section.status())).toList();
        boolean eligible = drafts.stream().anyMatch(section ->
                Objects.equals(actor.unitId(), section.managingUnitId()));
        List<BlockedSection> blocked = new ArrayList<>();
        List<ApprovalSnapshot> snapshot = new ArrayList<>();
        if (drafts.isEmpty() || !eligible) {
            return new Assessment(new ApprovalReadiness(semesterId, drafts.size(), eligible, false, blocked), snapshot);
        }
        for (SchedulingSection section : drafts) {
            List<Slot> slots = context.patterns().stream()
                    .filter(pattern -> pattern.section() == section.id()).toList();
            List<Map<String, Object>> missing = new ArrayList<>();
            List<PatternConflict> conflicts = new ArrayList<>();
            if (section.staffId() == null) {
                missing.add(Map.of("rule", "BR02", "message", "Chưa có giảng viên chính."));
            }
            if (slots.size() != section.sessionsPerWeek()) {
                missing.add(Map.of("rule", "BR10", "message", "Số buổi đã xếp khác số buổi yêu cầu."));
            }
            for (Slot slot : slots) {
                Result result = scheduling.check(context, slot, actor, false);
                SchedulingRoom room = context.rooms().get(slot.room());
                if (!result.valid()) {
                    conflicts.add(new PatternConflict(slot.id(), slot.day(), slot.first(),
                            room == null ? null : room.code(), result.issues()));
                } else {
                    snapshot.add(new ApprovalSnapshot(slot.id(), slot.section(), slot.staff(), slot.room(),
                            slot.day(), slot.first(), slot.count(), slot.from().toString(), slot.to().toString(),
                            repository.patternVersion(slot.id()), section.code(),
                            section.subjectName(), room.code()));
                }
            }
            if (!missing.isEmpty() || !conflicts.isEmpty()) {
                blocked.add(new BlockedSection(section.id(), section.code(),
                        section.subjectName(),
                        Objects.equals(actor.unitId(), section.managingUnitId()),
                        slots.size(), section.sessionsPerWeek(), missing, conflicts));
            }
        }
        return new Assessment(new ApprovalReadiness(semesterId, drafts.size(), eligible, blocked.isEmpty(), blocked),
                snapshot);
    }

    void rejectIncomplete(ApprovalReadiness ready) {
        List<Map<String, Object>> issues = new ArrayList<>();
        for (BlockedSection section : ready.blockedSections()) {
            for (PatternConflict slot : section.conflicts()) {
                for (Map<String, Object> rawIssue : slot.issues()) {
                    Map<String, Object> issue = new LinkedHashMap<>(rawIssue);
                    issue.putIfAbsent("patternId", slot.id());
                    issue.put("candidateSectionCode", section.code());
                    issues.add(issue);
                }
            }
        }
        Map<String, Object> details = ready.toResponse();
        if (!issues.isEmpty()) {
            details.put("issues", issues);
            throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", details);
        }
        throw new ApiFailure(409, "INVALID_STATE",
                "Cần hoàn tất phân công và xếp đủ các buổi trước khi gửi duyệt.", details);
    }
}
