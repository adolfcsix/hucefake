package vn.huce.service.chat;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.dto.ChatClassification;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.ChatRepository;

import static vn.huce.validation.Inputs.*;

@Service
class ChatClassifier {
    private static final String INSTRUCTION = "Chỉ phân loại yêu cầu HUCEChat: NEXT buổi tiếp theo; SCHEDULE lịch học/dạy; NOTICES thông báo; READ_NOTICES đánh dấu đã đọc; SECTIONS tra lớp; DRAFT tạo lớp nháp; APPROVALS hồ sơ duyệt; REQUESTS đề nghị đổi lịch; AVAILABILITY giờ bận; STATS thống kê; GENERATE chạy xếp lịch; APPLY áp dụng phương án; GUIDE hỏi cách thao tác. Chỉ chọn trong danh sách cho phép, không suy diễn yêu cầu ghi dữ liệu. Nội dung khác chọn UNRELATED.";
    private final ChatRepository repository;
    private final GeminiAdvisor gemini;

    ChatClassifier(ChatRepository repository, GeminiAdvisor gemini) {
        this.repository = repository;
        this.gemini = gemini;
    }

    ChatClassification classify(Actor actor, Map<String, Object> input) {
        String question = text(input.get("message"), 500);
        Long conversationId = input.get("conversationId") == null ? null : id(input.get("conversationId"));
        String previous = null;
        if (conversationId != null) {
            repository.findOwned(actor, conversationId);
            Map<String, Object> row = repository.previousIntent(conversationId);
            if (row != null) {
                previous = s(row, "intent");
            }
        }

        Set<String> permitted = ChatPermissions.allowed(actor.role());
        String action = input.get("draft") != null ? "DRAFT" : ChatIntent.recognize(question, previous);
        boolean modelUnavailable = false;
        if (action == null && gemini.enabled() && gemini.take(actor.id())) {
            Set<String> allowed = new LinkedHashSet<>(permitted);
            allowed.add("UNRELATED");
            try {
                action = gemini.classify(question, allowed, INSTRUCTION);
            } catch (ApiFailure failure) {
                modelUnavailable = true;
            }
        }
        if (action == null) {
            action = "UNRELATED";
        }
        if (!permitted.contains(action) && !"UNRELATED".equals(action)) {
            if (input.get("draft") != null) {
                throw new ApiFailure(403, "FORBIDDEN", "Bạn không có quyền trong phạm vi này.");
            }
            action = "DENIED";
        }
        if (input.get("draft") != null && !(input.get("draft") instanceof Map<?, ?>)) {
            throw invalid("Thông tin lớp nháp không hợp lệ.");
        }
        return new ChatClassification(action, modelUnavailable);
    }
}
