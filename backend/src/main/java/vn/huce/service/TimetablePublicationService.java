package vn.huce.service;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.dto.TimetablePublicationResponse;
import vn.huce.exception.ApiFailure;
import vn.huce.model.ApprovalRequestSnapshot;
import vn.huce.model.ApprovalStatus;
import vn.huce.model.TimetableVersionSnapshot;
import vn.huce.repository.ApprovalRepository;

import static vn.huce.service.SchedulingService.Context;
import static vn.huce.service.SchedulingService.Result;
import static vn.huce.service.SchedulingService.Slot;
import static vn.huce.service.SchedulingService.Span;
import static vn.huce.service.SchedulingService.occurrences;
import static vn.huce.validation.Inputs.id;
import static vn.huce.validation.Inputs.state;

/** Materializes an approved timetable within the caller's existing write transaction. */
@Service
class TimetablePublicationService {
    private final ApprovalRepository repository;
    private final NotificationService notifications;
    private final SchedulingService scheduling;

    TimetablePublicationService(ApprovalRepository repository, NotificationService notifications,
                                SchedulingService scheduling) {
        this.repository = repository;
        this.notifications = notifications;
        this.scheduling = scheduling;
    }

    TimetablePublicationResponse materialize(Actor actor, ApprovalRequestSnapshot approval,
                                             TimetableVersionSnapshot version, List<?> snapshot) {
        long approvalId = approval.id();
        long versionId = version.id();
        long semesterId = version.semesterId();
        Context context = scheduling.context();
        Set<Long> sections = new LinkedHashSet<>();
        long total = 0;
        Map<Long, Slot> current = new HashMap<>();
        for (Slot pattern : context.patterns()) {
            if (context.sections().get(pattern.section()).semesterId() == semesterId) {
                current.put(pattern.id(), pattern);
            }
        }
        if (snapshot.isEmpty() || snapshot.size() != current.size()) {
            throw state("Phương án đã thay đổi sau khi gửi duyệt.");
        }
        for (Object raw : snapshot) {
            if (!(raw instanceof Map<?, ?> row)) {
                throw state("Hồ sơ không hợp lệ.");
            }
            long patternId = id(row.get("id"));
            Slot slot = current.remove(patternId);
            if (!matchesSnapshot(slot, row)) {
                throw state("Phương án đã thay đổi sau khi gửi duyệt.");
            }
            Result result = scheduling.check(context, slot, actor, false);
            if (!result.valid()) {
                throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.",
                        Map.of("issues", result.issues()));
            }
            long count = 0;
            for (var date : occurrences(slot)) {
                if (scheduling.holiday(context, context.rooms().get(slot.room()).campusId(), date)) {
                    continue;
                }
                Span time = scheduling.interval(context, slot, date);
                repository.insertSession(slot.section(), slot.staff(), slot.room(), slot.first(),
                        slot.count(), time.start(), time.end(), versionId, slot.id());
                count++;
            }
            if (count == 0) {
                throw state("Một lịch định kỳ không sinh được buổi học vì toàn bộ ngày đều nghỉ.");
            }
            total += count;
            sections.add(slot.section());
        }
        if (!current.isEmpty() || sections.size() != version.sectionCount()) {
            throw state("Phương án đã thay đổi sau khi gửi duyệt.");
        }
        for (long sectionId : sections) {
            repository.openSection(sectionId);
            notifications.notifySection(sectionId, "Đã công bố lịch lớp "
                    + context.sections().get(sectionId).code() + ".");
        }
        repository.supersedePublished(semesterId);
        repository.publishVersion(versionId, actor.id());
        repository.markApplied(approvalId);
        repository.changeWorkStatus(approval.workItemId(), ApprovalStatus.APPLIED.name());
        var result = new TimetablePublicationResponse(versionId, total, sections.size());
        return result;
    }

    private boolean matchesSnapshot(Slot slot, Map<?, ?> row) {
        return slot != null && slot.section() == id(row.get("section"))
                && Objects.equals(slot.staff(), id(row.get("staff")))
                && slot.room() == id(row.get("room")) && slot.day() == id(row.get("day"))
                && slot.first() == id(row.get("first")) && slot.count() == id(row.get("count"))
                && slot.from().toString().equals(row.get("from")) && slot.to().toString().equals(row.get("to"));
    }
}
