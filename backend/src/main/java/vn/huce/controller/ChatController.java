package vn.huce.controller;

import vn.huce.Accounts;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import vn.huce.service.ChatService;
import vn.huce.service.GeminiAdvisor;
import vn.huce.service.MutationService;
import vn.huce.service.ProposalService;

import static vn.huce.validation.Inputs.*;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final SqlStore db;private final ChatService chat;private final ProposalService proposals;private final MutationService writes;private final GeminiAdvisor gemini;
    ChatController(SqlStore db,ChatService chat,ProposalService proposals,MutationService writes,GeminiAdvisor gemini){this.db=db;this.chat=chat;this.proposals=proposals;this.writes=writes;this.gemini=gemini;}
    private Accounts.Actor actor(Authentication a){return (Accounts.Actor)a.getPrincipal();}
    @GetMapping("/chat/status") Object status(){return Map.of("gemini",gemini.enabled());}
    @GetMapping("/chat/conversations") Object conversations(Authentication a,@RequestParam(defaultValue="") String q){return chat.conversations(actor(a),q);}
    @GetMapping("/chat/conversations/{id}") Object conversation(Authentication a,@PathVariable long id){return chat.conversation(actor(a),id);}
    @PostMapping("/chat/messages") ResponseEntity<?> message(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);var classification=chat.classify(a,f);return ResponseEntity.status(201).body(writes.write(a,req,f,()->chat.message(a,f,classification)));
    }
    @PostMapping("/chat/proposals/{id}/confirm") Object confirm(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth);return writes.write(a,req,f==null?Map.of():f,()->proposals.confirm(a,id));
    }
    @PostMapping("/chat/proposals/{id}/cancel") Object cancel(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth);return writes.write(a,req,f==null?Map.of():f,()->proposals.cancel(a,id));
    }
    @GetMapping("/schedule-assistant") Object assistant(Authentication auth){role(actor(auth),"STAFF","MANAGER");return Map.of("enabled",gemini.enabled());}
    @PostMapping("/schedule-assistant/proposals") ResponseEntity<?> assistantProposal(Authentication auth,HttpServletRequest req,@RequestBody Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF","MANAGER");
        if(!gemini.enabled())throw new ApiFailure(503,"ASSISTANT_DISABLED","Cần cấu hình Gemini trên backend để dùng chế độ này.");
        if(!gemini.take(a.id()))throw new ApiFailure(429,"RATE_LIMIT","Vui lòng thử lại sau.");
        String action=gemini.classify(text(f.get("message"),500),List.of("GENERATE","APPLY","UNRELATED"),"Phân loại yêu cầu HUCE. GENERATE chạy xếp lịch tự động, APPLY áp dụng phương án, UNRELATED nội dung khác hoặc chỉ hỏi cách thao tác. Không quyết định học kỳ, người dùng hay lần chạy.");
        if("UNRELATED".equals(action))return ResponseEntity.status(201).body(Map.of("action",action,"message","Nhập yêu cầu chạy xếp lịch hoặc áp dụng phương án đã chọn."));
        return ResponseEntity.status(201).body(writes.write(a,req,f,()->proposals.prepare(a,action,db.need("SELECT id,code FROM semesters WHERE id=?",id(f.get("semesterId"))),f)));
    }
    @PostMapping("/schedule-assistant/proposals/{id}/confirm") Object assistantConfirm(Authentication auth,HttpServletRequest req,@PathVariable long id,@RequestBody(required=false) Map<String,Object> f){
        var a=actor(auth);role(a,"STAFF","MANAGER");return writes.write(a,req,f==null?Map.of():f,()->proposals.confirm(a,id));
    }
}
