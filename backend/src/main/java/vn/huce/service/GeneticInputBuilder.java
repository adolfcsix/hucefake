package vn.huce.service;

import vn.huce.model.SchedulingSection;
import vn.huce.model.SchedulingRoom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.PublishedSession;
import vn.huce.repository.SchedulingRepository;

import static vn.huce.service.SchedulingService.*;
import static vn.huce.validation.Inputs.*;

@Service
class GeneticInputBuilder {
    private final SchedulingService scheduling;
    private final SchedulingRepository repository;

    GeneticInputBuilder(SchedulingService scheduling, SchedulingRepository repository) {
        this.scheduling = scheduling;
        this.repository = repository;
    }

    List<SchedulingSection> targets(Context context, Actor actor, long semesterId) {
        return context.sections().values().stream()
                .filter(section -> section.semesterId() == semesterId
                        && "DRAFT".equals(section.status())
                        && ("MANAGER".equals(actor.role())
                            || Objects.equals(actor.unitId(), section.managingUnitId()))
                        && section.scheduled() < section.sessionsPerWeek())
                .toList();
    }

    static Slot make(SchedulingSection section, long roomId, int dayOfWeek, int periodFrom) {
        return new Slot(0, section.id(), section.staffId(), roomId, dayOfWeek,
                periodFrom, section.periodsPerSession(), section.startDate(),
                section.endDate());
    }

    GeneticScheduler.Input build(Actor actor, long semesterId) {
        Context context = scheduling.context();
        List<SchedulingSection> sections = targets(context, actor, semesterId);
        Set<Long> allowedCampuses = scheduling.campuses(actor);
        if (sections.isEmpty()) {
            throw state("Không có buổi học nháp cần xếp trong phạm vi đã chọn.");
        }

        LocalDate from = sections.stream().map(section -> section.startDate())
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate to = sections.stream().map(section -> section.endDate())
                .max(LocalDate::compareTo).orElseThrow();
        Map<LocalDate, List<PublishedSession>> published = indexByDay(repository.publishedSessions(from, to));
        List<List<Slot>> genes = new ArrayList<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);

        for (SchedulingSection section : sections) {
            if (section.staffId() == null) {
                issues.add(scheduling.issue("BR02", "Chưa có giảng viên chính.", section));
                continue;
            }
            List<Slot> domain = domain(context, section, allowedCampuses, published, deadline);
            if (domain.isEmpty()) {
                issues.add(scheduling.issue("BR07", "Không có phòng và khung tiết phù hợp.", section));
                continue;
            }
            List<Slot> options = List.copyOf(domain);
            for (long index = section.scheduled(); index < section.sessionsPerWeek(); index++) {
                genes.add(options);
            }
        }
        if (!issues.isEmpty()) {
            throw new ApiFailure(400, "VALIDATION_ERROR", "Dữ liệu chưa đủ để xếp lịch tự động.",
                    Map.of("issues", issues));
        }
        return new GeneticScheduler.Input(context, List.copyOf(genes));
    }

    private Map<LocalDate, List<PublishedSession>> indexByDay(List<PublishedSession> published) {
        Map<LocalDate, List<PublishedSession>> result = new HashMap<>();
        for (PublishedSession row : published) {
            LocalDateTime start = row.start();
            LocalDateTime end = row.end();
            for (LocalDate date = start.toLocalDate(); !date.isAfter(end.toLocalDate()); date = date.plusDays(1)) {
                result.computeIfAbsent(date, key -> new ArrayList<>()).add(row);
            }
        }
        return result;
    }

    private List<Slot> domain(Context context, SchedulingSection section, Set<Long> allowedCampuses,
                              Map<LocalDate, List<PublishedSession>> published, long deadline) {
        List<Slot> domain = new ArrayList<>();
        for (SchedulingRoom room : context.rooms().values()) {
            long campusId = room.campusId();
            if (!allowedCampuses.contains(campusId) || !scheduling.fits(room, section)) {
                continue;
            }
            for (int dayOfWeek = 1; dayOfWeek <= 6; dayOfWeek++) {
                for (int period = 1; period <= 16 - section.periodsPerSession(); period++) {
                    if (System.nanoTime() > deadline) {
                        throw state("Vượt giới hạn 60 giây tạo miền xếp lịch.");
                    }
                    Slot slot = make(section, room.id(), dayOfWeek, period);
                    if (feasible(context, section, slot, campusId, published)) {
                        domain.add(slot);
                    }
                }
            }
        }
        return domain;
    }

    private boolean feasible(Context context, SchedulingSection section, Slot slot, long campusId,
                             Map<LocalDate, List<PublishedSession>> published) {
        Set<Long> staff = scheduling.staff(context, slot);
        Set<Long> groups = section.groups().keySet();
        boolean teaching = false;
        for (LocalDate date : occurrences(slot)) {
            Span time = scheduling.interval(context, slot, date);
            if (time == null) {
                return false;
            }
            if (scheduling.holiday(context, campusId, date)) {
                continue;
            }
            teaching = true;
            for (PublishedSession row : published.getOrDefault(date, List.of())) {
                if (time.start().isBefore(row.end())
                        && row.start().isBefore(time.end())
                        && (Objects.equals(row.roomId(), slot.room())
                            || row.sectionId() == slot.section()
                            || !Collections.disjoint(staff, row.staffIds())
                            || !Collections.disjoint(groups, row.groupIds()))) {
                    return false;
                }
            }
        }
        return teaching;
    }
}
