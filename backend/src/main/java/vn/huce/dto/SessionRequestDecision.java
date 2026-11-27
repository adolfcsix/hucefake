package vn.huce.dto;

import java.util.Map;
import vn.huce.model.SessionChangeAction;
import vn.huce.model.SessionRequestStatus;

import static vn.huce.validation.Inputs.choice;
import static vn.huce.validation.Inputs.text;

/** A returned request has no adjustment action; approved requests must match the original action. */
public record SessionRequestDecision(SessionRequestStatus status, SessionChangeAction action, String reason) {
    public static SessionRequestDecision from(Map<String, Object> body) {
        String reason = text(body.get("reason"), 500);
        String action = choice(body.get("action"), "RETURNED", "CANCEL", "RESCHEDULE");
        return "RETURNED".equals(action)
                ? new SessionRequestDecision(SessionRequestStatus.RETURNED, null, reason)
                : new SessionRequestDecision(SessionRequestStatus.APPROVED, SessionChangeAction.valueOf(action), reason);
    }
}
