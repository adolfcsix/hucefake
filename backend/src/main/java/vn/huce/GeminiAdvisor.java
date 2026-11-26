package vn.huce;

import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class GeminiAdvisor {
    private final String key,model;
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<Long,long[]> quota=new HashMap<>();
    GeminiAdvisor(ObjectMapper json,@Value("${GEMINI_API_KEY:}") String key,@Value("${GEMINI_MODEL:}") String model){this.json=json;this.key=key.trim();this.model=model.trim();}
    boolean enabled(){return !key.isEmpty()&&!model.isEmpty();}
    synchronized boolean take(long actor){
        long now=System.currentTimeMillis();quota.entrySet().removeIf(e->e.getValue()[0]<=now);
        var entry=quota.get(actor);if(entry==null){if(quota.size()>=1500)return false;entry=new long[]{now+60000,0};quota.put(actor,entry);}
        if(entry[1]>=8)return false;entry[1]++;return true;
    }
    String classify(String question,Collection<String> permitted,String instruction){
        if(!enabled())throw new ApiFailure(503,"ASSISTANT_DISABLED","Cần cấu hình Gemini trên backend để dùng chế độ này.");
        if(!model.matches("gemini-[a-z0-9.-]+"))throw new ApiFailure(503,"ASSISTANT_CONFIGURATION","GEMINI_MODEL trên backend không hợp lệ.");
        try{
            var body=Map.of("systemInstruction",Map.of("parts",List.of(Map.of("text",instruction))),
              "contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",question)))),
              "generationConfig",Map.of("responseMimeType","application/json","responseJsonSchema",Map.of("type","object","properties",Map.of("action",Map.of("type","string","enum",permitted)),"required",List.of("action"))));
            var req=HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent")).timeout(Duration.ofSeconds(12))
              .header("Content-Type","application/json").header("x-goog-api-key",key).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var res=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(res.statusCode()<200||res.statusCode()>=300)throw new IllegalStateException();
            var parts=json.readTree(res.body()).path("candidates").path(0).path("content").path("parts");
            var out=new StringBuilder();for(var p:parts)if(!p.path("thought").asBoolean(false))out.append(p.path("text").asText(""));
            String action=json.readTree(out.toString()).path("action").asText();
            if(!permitted.contains(action))throw new IllegalStateException();
            return action;
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ApiFailure(502,"ASSISTANT_UNAVAILABLE","Gemini chưa trả về đề xuất hợp lệ.");}
    }
}
