package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.service.chat.ChatService;
import vn.huce.service.chat.ProposalService;
import vn.huce.service.chat.ScheduleAssistantService;
import vn.huce.service.MutationService;
import vn.huce.dto.ChatClassification;

import static vn.huce.validation.Inputs.role;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final ChatService chat;
    private final ProposalService proposals;
    private final MutationService writes;
    private final ScheduleAssistantService assistant;

    ChatController(ChatService chat, ProposalService proposals, MutationService writes,
                   ScheduleAssistantService assistant) {
        this.chat = chat;
        this.proposals = proposals;
        this.writes = writes;
        this.assistant = assistant;
    }

    private Actor actor(Authentication authentication) {
        return (Actor) authentication.getPrincipal();
    }

    @GetMapping("/chat/status")
    public Map<String, Boolean> status() {
        return Map.of("gemini", assistant.enabled());
    }

    @GetMapping("/chat/conversations")
    public Object conversations(Authentication authentication, @RequestParam(defaultValue = "") String q) {
        return chat.conversations(actor(authentication), q);
    }

    @GetMapping("/chat/conversations/{id}")
    public Object conversation(Authentication authentication, @PathVariable long id) {
        return chat.conversation(actor(authentication), id);
    }

    @PostMapping("/chat/messages")
    public ResponseEntity<?> message(Authentication authentication, HttpServletRequest request,
                                     @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        ChatClassification classification = chat.classify(actor, body);
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> chat.message(actor, body, classification)));
    }

    @PostMapping("/chat/proposals/{id}/confirm")
    public Object confirm(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                          @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        return writes.write(actor, request, body == null ? Map.of() : body, () -> proposals.confirm(actor, id));
    }

    @PostMapping("/chat/proposals/{id}/cancel")
    public Object cancel(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                         @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        return writes.write(actor, request, body == null ? Map.of() : body, () -> proposals.cancel(actor, id));
    }

    @GetMapping("/schedule-assistant")
    public Map<String, Boolean> assistant(Authentication authentication) {
        role(actor(authentication), "STAFF", "MANAGER");
        return Map.of("enabled", assistant.enabled());
    }

    @PostMapping("/schedule-assistant/proposals")
    public ResponseEntity<?> assistantProposal(Authentication authentication, HttpServletRequest request,
                                               @RequestBody Map<String, Object> body) {
        Actor actor = actor(authentication);
        String action = assistant.classify(actor, body);
        if ("UNRELATED".equals(action)) {
            return ResponseEntity.status(201).body(Map.of("action", action,
                    "message", "Nhập yêu cầu chạy xếp lịch hoặc áp dụng phương án đã chọn."));
        }
        return ResponseEntity.status(201).body(writes.write(actor, request, body,
                () -> proposals.prepareForSemester(actor, action, body)));
    }

    @PostMapping("/schedule-assistant/proposals/{id}/confirm")
    public Object assistantConfirm(Authentication authentication, HttpServletRequest request, @PathVariable long id,
                                   @RequestBody(required = false) Map<String, Object> body) {
        Actor actor = actor(authentication);
        role(actor, "STAFF", "MANAGER");
        return writes.write(actor, request, body == null ? Map.of() : body, () -> proposals.confirm(actor, id));
    }
}
