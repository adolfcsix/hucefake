package vn.huce.service.chat;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;

import static vn.huce.validation.Inputs.role;
import static vn.huce.validation.Inputs.text;

@Service
public class ScheduleAssistantService {
    private final GeminiAdvisor gemini;

    public ScheduleAssistantService(GeminiAdvisor gemini) {
        this.gemini = gemini;
    }

    public boolean enabled() {
        return gemini.enabled();
    }

    public String classify(Actor actor, Map<String, Object> body) {
        role(actor, "STAFF", "MANAGER");
        if (!gemini.enabled()) {
            throw new ApiFailure(503, "ASSISTANT_DISABLED",
                    "Cần cấu hình Gemini trên backend để dùng chế độ này.");
        }
        if (!gemini.take(actor.id())) {
            throw new ApiFailure(429, "RATE_LIMIT", "Vui lòng thử lại sau.");
        }
        return gemini.classify(text(body.get("message"), 500), List.of("GENERATE", "APPLY", "UNRELATED"),
                "Phân loại yêu cầu HUCE. GENERATE chạy xếp lịch tự động, APPLY áp dụng phương án, UNRELATED nội dung khác hoặc chỉ hỏi cách thao tác. Không quyết định học kỳ, người dùng hay lần chạy.");
    }
}
