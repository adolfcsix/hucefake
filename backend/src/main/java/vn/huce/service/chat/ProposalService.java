package vn.huce.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.ProposalAction;
import vn.huce.model.ScheduleRunStatus;
import vn.huce.repository.AuditRepository;
import vn.huce.repository.ChatRepository;
import vn.huce.repository.NotificationRepository;
import vn.huce.repository.ProposalRepository;
import vn.huce.repository.SectionRepository;
import vn.huce.service.GeneticScheduler;
import vn.huce.service.RunService;
import vn.huce.service.SectionService;

import static vn.huce.validation.Inputs.*;

@Service
public class ProposalService {
    private final ProposalRepository repository;
    private final ChatRepository chat;
    private final SectionRepository sectionRepository;
    private final NotificationRepository notifications;
    private final AuditRepository audit;
    private final ObjectMapper json;
    private final RunService runs;
    private final SectionService sections;

    ProposalService(ProposalRepository repository, ChatRepository chat, SectionRepository sectionRepository,
                    NotificationRepository notifications, AuditRepository audit, ObjectMapper json,
                    RunService runs, SectionService sections) {
        this.repository = repository;
        this.chat = chat;
        this.sectionRepository = sectionRepository;
        this.notifications = notifications;
        this.audit = audit;
        this.json = json;
        this.runs = runs;
        this.sections = sections;
    }

    public String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    public Map<String, Object> decode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value.toString(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    private ApiFailure forbidden() {
        return new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
    }

    private void permission(Actor actor, String action) {
        if (!ChatPermissions.allowed(actor.role()).contains(action)) {
            throw forbidden();
        }
    }

    private ProposalAction action(String value) {
        try {
            return ProposalAction.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw invalid("Thao tác không hợp lệ.");
        }
    }

    private record Preview(Object parameters, Long runId, Map<String, Object> details) {
    }

    public Map<String, Object> prepare(Actor actor, String rawAction, Map<String, Object> semester,
                                       Map<String, Object> input) {
        permission(actor, rawAction);
        ProposalAction action = action(rawAction);
        Preview preview = switch (action) {
            case GENERATE -> {
                var parameters = GeneticScheduler.parameters(input.get("parameters"));
                yield new Preview(parameters, null, Map.of("parameters", parameters));
            }
            case APPLY -> previewApply(actor, semester, input);
            case DRAFT -> previewDraft(actor, semester, input);
            case READ_NOTICES -> previewNotifications(actor);
        };
        if (preview == null) {
            return null;
        }
        var result = repository.insert(actor.id(), semester == null ? null : semester.get("id"),
                action.name(), preview.runId(), preview.parameters() == null ? null : encode(preview.parameters()));
        result.put("action", action.name());
        result.put("semester", semester == null ? null : semester.get("code"));
        result.put("preview", preview.details());
        return result;
    }

    private Preview previewApply(Actor actor, Map<String, Object> semester, Map<String, Object> input) {
        var run = runs.get(actor, id(input.get("runId")));
        long runId = n(run, "id");
        if (n(run, "semester_id") != n(semester, "id")) {
            throw forbidden();
        }
        if (!ScheduleRunStatus.COMPLETED.name().equals(run.get("status")) || n(run, "hard_violations") != 0) {
            throw state("Chọn phương án đã hoàn tất với 0 vi phạm cứng.");
        }
        long count = repository.countRunItems(runId);
        return new Preview(null, runId, Map.of("runId", runId, "count", count, "hardViolations", 0));
    }

    private Preview previewDraft(Actor actor, Map<String, Object> semester, Map<String, Object> input) {
        if (!(input.get("draft") instanceof Map<?, ?> draft)) {
            throw invalid("Thông tin lớp nháp không hợp lệ.");
        }
        Map<String, Object> form = new LinkedHashMap<>();
        draft.forEach((key, value) -> form.put(key.toString(), value));
        form.put("semesterId", semester.get("id"));
        var normalized = sections.previewDraft(actor, form);
        var subject = sectionRepository.findSubject(normalized.subjectId());
        // Adapt the typed preview only at the heterogeneous proposal response boundary.
        Map<String, Object> details = json.convertValue(normalized, new TypeReference<Map<String, Object>>() {});
        details.put("subject", subject.code() + " · " + subject.name());
        return new Preview(normalized, null, details);
    }

    private Preview previewNotifications(Actor actor) {
        var summary = notifications.unreadSummary(actor.id());
        if (summary.count() == 0) {
            return null;
        }
        return new Preview(Map.of("lastId", summary.lastId()), null, Map.of("count", summary.count()));
    }

    public Map<String, Object> owned(Actor actor, long proposalId) {
        var row = repository.lock(proposalId);
        if (n(row, "requested_by") != actor.id()) {
            throw forbidden();
        }
        permission(actor, s(row, "action"));
        if (repository.hasDifferentConversationScope(proposalId, actor)) {
            throw forbidden();
        }
        row.put("parameters", decode(row.remove("params_json")));
        row.put("result", decode(row.remove("result_json")));
        return row;
    }

    @SuppressWarnings("unchecked")
    public Object confirm(Actor actor, long proposalId) {
        var proposal = owned(actor, proposalId);
        if (proposal.get("result") != null) {
            return proposal.get("result");
        }
        if (proposal.get("cancelled_at") != null) {
            throw state("Đề xuất đã hủy.");
        }
        if (Boolean.TRUE.equals(proposal.get("expired"))) {
            throw state("Đề xuất đã hết hạn. Hãy tạo đề xuất mới.");
        }
        ProposalAction action = action(s(proposal, "action"));
        var parameters = (Map<String, Object>) proposal.get("parameters");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action.name());
        switch (action) {
            case GENERATE -> result.putAll((Map<String, Object>) runs.enqueue(actor, n(proposal, "semester_id"), parameters));
            case APPLY -> {
                var run = runs.get(actor, n(proposal, "run_id"));
                if (n(run, "semester_id") != n(proposal, "semester_id")) {
                    throw forbidden();
                }
                result.putAll((Map<String, Object>) runs.apply(actor, n(proposal, "run_id")));
                result.put("id", proposal.get("run_id"));
            }
            case DRAFT -> {
                result.put("id", sections.saveDraft(actor, parameters).id());
                result.put("code", parameters.get("code"));
            }
            case READ_NOTICES -> result.put("marked", notifications.markThrough(actor.id(), id(parameters.get("lastId"))));
        }
        repository.confirm(proposalId, encode(result));
        audit.record(actor, "CONFIRM", "ai_proposals", proposalId);
        return result;
    }

    public Object cancel(Actor actor, long proposalId) {
        var row = owned(actor, proposalId);
        if (row.get("result") != null) {
            throw state("Đề xuất đã thực hiện.");
        }
        repository.cancel(proposalId);
        return Map.of("status", "CANCELLED");
    }

    public Map<String, Object> prepareForSemester(Actor actor, String action, Map<String, Object> input) {
        return prepare(actor, action, chat.semester(id(input.get("semesterId"))), input);
    }
}
