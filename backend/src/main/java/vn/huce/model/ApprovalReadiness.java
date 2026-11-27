package vn.huce.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Internal approval assessment. Maps are produced only at the existing API boundary. */
public record ApprovalReadiness(long semesterId, int draftCount, boolean eligible, boolean ready,
                                List<BlockedSection> blockedSections) {
    public Map<String, Object> toResponse() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("semesterId", semesterId);
        response.put("draftCount", draftCount);
        response.put("blockedSections", blockedSections.stream().map(BlockedSection::toResponse).toList());
        response.put("eligible", eligible);
        response.put("ready", ready);
        return response;
    }

    public record BlockedSection(long id, String code, String subjectName, boolean editable,
                                 int scheduled, long required, List<Map<String, Object>> missing,
                                 List<PatternConflict> conflicts) {
        Map<String, Object> toResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", id);
            response.put("code", code);
            response.put("subjectName", subjectName);
            response.put("editable", editable);
            response.put("scheduled", scheduled);
            response.put("required", required);
            response.put("missing", missing);
            response.put("conflicts", conflicts.stream().map(PatternConflict::toResponse).toList());
            return response;
        }
    }

    public record PatternConflict(long id, int dayOfWeek, int periodFrom, String roomCode,
                                  List<Map<String, Object>> issues) {
        Map<String, Object> toResponse() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", id);
            response.put("dayOfWeek", dayOfWeek);
            response.put("periodFrom", periodFrom);
            response.put("roomCode", roomCode);
            response.put("issues", issues);
            return response;
        }
    }
}
