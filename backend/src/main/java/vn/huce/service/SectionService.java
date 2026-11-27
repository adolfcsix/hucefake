package vn.huce.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.dto.SectionDraftPreview;
import vn.huce.dto.SectionIdResponse;
import vn.huce.model.SectionSemester;
import vn.huce.model.SectionSubject;
import vn.huce.model.SectionDraft;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.SectionRepository;

import static vn.huce.validation.Inputs.*;

@Service
public class SectionService {
    private final SectionRepository sections;
    private final AuditRepository audit;

    public SectionService(SectionRepository sections, AuditRepository audit) {
        this.sections = sections;
        this.audit = audit;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object sections(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "MANAGER");
        String status = query.getOrDefault("status", "");
        String subject = query.getOrDefault("subjectId", "");
        Long unitId = "STAFF".equals(actor.role())
                ? (actor.unitId() == null ? -1L : actor.unitId()) : null;
        String search = "%" + query.getOrDefault("search", "") + "%";
        return sections.findSections(query, id(query.get("semesterId")), unitId,
                status.isEmpty() ? null : status, subject.isEmpty() ? null : id(subject), search);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Object patterns(Actor actor, Map<String, String> query) {
        role(actor, "STAFF", "MANAGER");
        return sections.findPatterns(query, id(query.get("semesterId")));
    }

    public void unlocked(long semesterId) {
        if (sections.hasSubmittedTimetable(semesterId)) {
            throw state("Học kỳ đang chờ duyệt. Hãy xử lý hồ sơ trước khi chỉnh lịch.");
        }
    }

    public Map<String, Object> editable(Actor actor, long sectionId) {
        Map<String, Object> section = sections.findSection(sectionId);
        unit(actor, section.get("managing_unit_id"));
        if (!"DRAFT".equals(section.get("status"))) {
            throw state("Chỉ chỉnh lớp nháp.");
        }
        unlocked(n(section, "semester_id"));
        return section;
    }

    public SectionDraft values(SectionSemester semester, SectionSubject subject,
                               Map<String, Object> input) {
        if (!"ACTIVE".equals(semester.status()) || !"ACTIVE".equals(subject.status())) {
            throw invalid("Học kỳ và học phần phải đang hoạt động.");
        }
        LocalDate startDate = LocalDate.parse(date(input.get("startDate")));
        LocalDate endDate = LocalDate.parse(date(input.get("endDate")));
        if (startDate.isAfter(endDate) || startDate.isBefore(semester.startDate())
                || endDate.isAfter(semester.endDate())) {
            throw invalid("Khoảng ngày phải nằm trong học kỳ.");
        }
        return new SectionDraft(semester.id(), subject.id(),
                text(input.get("code"), 40).toUpperCase(Locale.ROOT),
                number(input.get("capacity"), 1, 10000),
                number(input.get("sessionsPerWeek"), 1, 6),
                number(input.get("periodsPerSession"), 1, 6),
                choice(input.get("requiredRoomType"), "LECTURE", "COMPUTER_LAB", "LAB", "STUDIO"),
                startDate, endDate);
    }

    public SectionDraft form(Actor actor, Map<String, Object> input) {
        SectionSemester semester = sections.findSemester(id(input.get("semesterId")));
        SectionSubject subject = sections.findSubject(id(input.get("subjectId")));
        unit(actor, subject.managingUnitId());
        return values(semester, subject, input);
    }

    public SectionDraftPreview previewDraft(Actor actor, Map<String, Object> input) {
        role(actor, "STAFF");
        SectionDraft draft = form(actor, input);
        checkNewDraft(draft);
        return SectionDraftPreview.from(draft);
    }

    public SectionIdResponse saveDraft(Actor actor, Map<String, Object> input) {
        role(actor, "STAFF");
        return create(actor, form(actor, input));
    }

    public SectionIdResponse create(Actor actor, SectionDraft draft) {
        role(actor, "STAFF");
        checkNewDraft(draft);
        long sectionId = sections.insertDraft(draft, actor.id());
        audit.record(actor, "CREATE", "course_sections", sectionId);
        return new SectionIdResponse(sectionId);
    }

    private void checkNewDraft(SectionDraft draft) {
        unlocked(draft.semesterId());
        if (sections.codeExists(draft.semesterId(), draft.code())) {
            throw new ApiFailure(409, "DUPLICATE_REQUEST", "Mã lớp đã có trong học kỳ này.",
                    Map.of("field", "code"));
        }
    }
}
