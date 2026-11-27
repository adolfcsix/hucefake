package vn.huce.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.dto.SectionAssignmentRequest;
import vn.huce.dto.SectionIdResponse;
import vn.huce.dto.SectionImportResponse;
import vn.huce.dto.SectionTargetGroupsRequest;
import vn.huce.exception.ApiFailure;
import vn.huce.model.PatternDraft;
import vn.huce.model.SectionDraft;
import vn.huce.model.SectionSemester;
import vn.huce.model.SectionSubject;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SchedulePatternRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.validation.SectionRequestParser;

import static vn.huce.validation.Inputs.*;

@Service
public class SectionMutationService {
    private final SectionRepository repository;
    private final SchedulePatternRepository patterns;
    private final AuditRepository audit;
    private final SectionService sections;
    private final SchedulingService scheduling;

    public SectionMutationService(SectionRepository repository, SchedulePatternRepository patterns,
                                  AuditRepository audit, SectionService sections, SchedulingService scheduling) {
        this.repository = repository;
        this.patterns = patterns;
        this.audit = audit;
        this.sections = sections;
        this.scheduling = scheduling;
    }

    // Write operations participate in MutationService's transaction and advisory lock.
    public SectionImportResponse importSections(Actor actor, Map<String, Object> input) {
        role(actor, "STAFF");
        SectionSemester semester = repository.findSemester(id(input.get("semesterId")));
        sections.unlocked(semester.id());
        if (!(input.get("rows") instanceof List<?> rows) || rows.isEmpty() || rows.size() > 200) {
            throw invalid("Mỗi tệp cần có từ 1 đến 200 lớp.");
        }
        int index = 0;
        for (Object item : rows) {
            index++;
            try {
                if (!(item instanceof Map<?, ?> raw)) {
                    throw invalid("Dòng dữ liệu không hợp lệ.");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) raw;
                if (!Objects.equals(row.get("semesterCode"), semester.code())) {
                    throw invalid("Mã học kỳ trong tệp khác học kỳ đang chọn.");
                }
                String code = text(row.get("subjectCode"), 40);
                SectionSubject subject = repository.findActiveSubjectByCode(actor.unitId(), code);
                if (subject == null) {
                    throw invalid("Mã học phần chưa có hoặc nằm ngoài phạm vi khoa.");
                }
                sections.create(actor, sections.values(semester, subject, row));
            } catch (ApiFailure failure) {
                throw new ApiFailure(failure.status(), failure.code(),
                        "Dòng dữ liệu " + index + " · " + failure.getMessage(), failure.details());
            }
        }
        return new SectionImportResponse(rows.size());
    }

    public SectionIdResponse update(Actor actor, long sectionId, Map<String, Object> input) {
        role(actor, "STAFF");
        Map<String, Object> current = sections.editable(actor, sectionId);
        version(input.get("version"), current);
        SectionDraft draft = sections.form(actor, input);
        if (n(current, "semester_id") != draft.semesterId()) {
            throw invalid("Không thể chuyển học kỳ của lớp.");
        }
        if (n(current, "scheduled") > draft.sessionsPerWeek()
                || repository.hasIncompatiblePatterns(sectionId, draft)) {
            throw state("Hãy gỡ lịch định kỳ bị ảnh hưởng trước khi sửa lớp.");
        }
        repository.updateDraft(sectionId, draft);
        audit.record(actor, "UPDATE", "course_sections", sectionId);
        return new SectionIdResponse(sectionId);
    }

    public Object cancel(Actor actor, long sectionId, String expectedVersion) {
        role(actor, "STAFF");
        version(expectedVersion, sections.editable(actor, sectionId));
        repository.cancelDraft(sectionId);
        audit.record(actor, "CANCEL", "course_sections", sectionId);
        return null;
    }

    public Object assign(Actor actor, long sectionId, Map<String, Object> input) {
        role(actor, "STAFF");
        Map<String, Object> section = sections.editable(actor, sectionId);
        checkOptionalVersion(input, section);
        SectionAssignmentRequest request = SectionRequestParser.assignment(input);
        applyAssignment(actor, sectionId, section, request);
        return null;
    }

    private void applyAssignment(Actor actor, long sectionId, Map<String, Object> section,
                                 SectionAssignmentRequest request) {
        long mainLecturer = request.staffId();
        Map<Long, String> desired = new LinkedHashMap<>();
        desired.put(mainLecturer, "MAIN");
        for (long assistant : request.assistantIds()) {
            desired.put(assistant, "ASSISTANT");
        }
        for (long staffId : desired.keySet()) {
            if (!repository.isActiveLecturer(staffId)) {
                throw invalid("Tài khoản giảng viên phải đang hoạt động.");
            }
        }
        for (SectionRepository.Assignment assignment : repository.activeAssignments(sectionId)) {
            if (Objects.equals(desired.get(assignment.staffId()), assignment.role())) {
                desired.remove(assignment.staffId());
            } else {
                repository.closeAssignment(assignment.id());
            }
        }
        LocalDate startDate = LocalDate.parse(s(section, "start_date"));
        for (Map.Entry<Long, String> entry : desired.entrySet()) {
            repository.addAssignment(sectionId, entry.getKey(), entry.getValue(), startDate, actor.id());
        }
        if (!Objects.equals(SchedulingService.nullableId(section.get("staff_id")), mainLecturer)) {
            repository.replacePatternLecturer(sectionId, mainLecturer);
        }
        scheduling.recheck(sectionId, actor);
        repository.touch(sectionId);
        audit.record(actor, "ASSIGN", "course_sections", sectionId);
    }

    public Object groups(Actor actor, long sectionId, Map<String, Object> input) {
        role(actor, "STAFF");
        checkOptionalVersion(input, sections.editable(actor, sectionId));
        SectionTargetGroupsRequest request = SectionRequestParser.groups(input);
        repository.replaceTargetGroups(sectionId, request.groupIds());
        scheduling.recheck(sectionId, actor);
        repository.touch(sectionId);
        audit.record(actor, "SET_TARGET_GROUPS", "course_sections", sectionId);
        return null;
    }

    public Object deletePattern(Actor actor, long patternId, String expectedVersion) {
        role(actor, "STAFF");
        Map<String, Object> pattern = patterns.findWithCampus(patternId);
        sections.editable(actor, n(pattern, "section_id"));
        scheduling.requireCampus(actor, n(pattern, "campus_id"));
        version(expectedVersion, pattern);
        patterns.delete(patternId);
        audit.record(actor, "DELETE", "section_schedule_patterns", patternId);
        return null;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object conflicts(Actor actor, Map<String, Object> input) {
        role(actor, "STAFF", "MANAGER");
        Map<String, Object> section = repository.findSection(id(input.get("sectionId")));
        unit(actor, section.get("managing_unit_id"));
        if (!"DRAFT".equals(section.get("status"))) {
            throw state("Chỉ kiểm tra phương án cho lớp nháp.");
        }
        SchedulingService.Context context = scheduling.context();
        Long patternId = input.get("patternId") == null ? null : id(input.get("patternId"));
        SchedulingService.Slot slot = scheduling.candidate(actor, context, input, patternId, section);
        return scheduling.check(context, slot, actor, true);
    }

    public Object savePattern(Actor actor, Map<String, Object> input, Long patternId) {
        role(actor, "STAFF");
        Map<String, Object> section = sections.editable(actor, id(input.get("sectionId")));
        SchedulingService.Context context = scheduling.context();
        SchedulingService.Slot slot = scheduling.candidate(actor, context, input, patternId, section);
        if (patternId != null) {
            version(input.get("version"), patterns.find(patternId));
        }
        if (patternId == null && n(section, "scheduled") >= n(section, "sessions_per_week")) {
            throw state("Lớp đã xếp đủ số buổi mỗi tuần.");
        }
        SchedulingService.Result result = scheduling.check(context, slot, actor, true);
        if (!result.valid()) {
            throw new ApiFailure(409, "SCHEDULE_CONFLICT", "Lịch dự kiến có xung đột.", result);
        }
        PatternDraft draft = new PatternDraft(slot.section(), slot.staff(), slot.room(), slot.day(),
                slot.first(), slot.count(), slot.from(), slot.to());
        Map<String, Object> row = patternId == null ? patterns.insert(draft, actor.id())
                : patterns.update(patternId, draft);
        audit.record(actor, "SAVE", "section_schedule_patterns", n(row, "id"));
        return row;
    }

    private void checkOptionalVersion(Map<String, Object> input, Map<String, Object> row) {
        if (input.get("version") != null) {
            version(input.get("version"), row);
        }
    }

}
