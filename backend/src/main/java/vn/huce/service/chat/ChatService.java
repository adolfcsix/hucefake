package vn.huce.service.chat;

import vn.huce.model.ChatSemester;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.dto.ChatClassification;
import vn.huce.model.ChatConversationSnapshot;
import vn.huce.model.ChatExchangeReceipt;
import vn.huce.model.ChatExchangeSnapshot;
import vn.huce.repository.ChatRepository;

import static vn.huce.validation.Inputs.*;

@Service
public class ChatService {
    private final ChatRepository repository;
    private final ProposalService proposals;
    private final ChatClassifier classifier;
    private final ChatReplyService replies;

    ChatService(ChatRepository repository, ProposalService proposals, ChatClassifier classifier,
                ChatReplyService replies) {
        this.repository = repository;
        this.proposals = proposals;
        this.classifier = classifier;
        this.replies = replies;
    }

    public List<Map<String, Object>> conversations(Actor actor, String search) {
        return repository.conversations(actor, search.substring(0, Math.min(80, search.length())));
    }

    private ChatConversationSnapshot owned(Actor actor, long conversationId) {
        return repository.findOwned(actor, conversationId);
    }

    public Map<String, Object> conversation(Actor actor, long conversationId) {
        ChatConversationSnapshot conversation = owned(actor, conversationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", conversation.id());
        result.put("title", conversation.title());
        List<ChatExchangeSnapshot> rows = new ArrayList<>(repository.exchanges(conversationId));
        Collections.reverse(rows);
        List<Map<String, Object>> exchanges = new ArrayList<>();
        for (ChatExchangeSnapshot row : rows) {
            Map<String, Object> reply = proposals.decode(row.replyJson());
            if (reply.get("proposal") instanceof Map<?, ?> raw) {
                Map<String, Object> proposal = new LinkedHashMap<>();
                raw.forEach((key, value) -> proposal.put(key.toString(), value));
                proposal.put("outcome", row.outcome());
                proposal.put("result", proposals.decode(row.resultJson()));
                reply.put("proposal", proposal);
            }
            exchanges.add(Map.of("id", row.id(), "question", row.question(),
                    "created_at", row.createdAt(), "reply", reply));
        }
        result.put("exchanges", exchanges);
        return result;
    }

    public ChatClassification classify(Actor actor, Map<String, Object> input) {
        return classifier.classify(actor, input);
    }

    public Object message(Actor actor, Map<String, Object> input, ChatClassification classification) {
        String question = text(input.get("message"), 500);
        Long conversationId = input.get("conversationId") == null ? null : id(input.get("conversationId"));
        if (conversationId != null) {
            owned(actor, conversationId);
        }
        ChatSemester semester = input.get("semesterId") == null ? null
                : repository.semester(id(input.get("semesterId")));
        Map<String, Object> reply = replies.answer(actor, classification.intent(), semester, question, input);
        if (conversationId == null) {
            conversationId = repository.createConversation(actor, question.substring(0, Math.min(80, question.length())));
        }
        reply.put("intent", classification.intent());
        reply.put("semesterId", semester == null ? null : semester.id());
        reply.put("sourceAt", Instant.now().toString());
        reply.put("modelUnavailable", classification.modelUnavailable());
        Object proposalId = reply.get("proposal") instanceof Map<?, ?> proposal ? proposal.get("id") : null;
        ChatExchangeReceipt receipt = repository.insertExchange(conversationId, question, proposals.encode(reply), proposalId);
        Map<String, Object> exchange = new LinkedHashMap<>();
        exchange.put("id", receipt.id());
        exchange.put("created_at", receipt.createdAt());
        exchange.put("question", question);
        exchange.put("reply", reply);
        repository.touch(conversationId);
        return Map.of("conversationId", conversationId, "exchange", exchange);
    }
}
