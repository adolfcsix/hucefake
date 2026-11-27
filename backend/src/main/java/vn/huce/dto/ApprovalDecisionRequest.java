package vn.huce.dto;

import java.util.Map;
import vn.huce.model.ApprovalStatus;

import static vn.huce.validation.Inputs.choice;
import static vn.huce.validation.Inputs.text;

/** Validated decision; the original HTTP body is retained for idempotency. */
public record ApprovalDecisionRequest(ApprovalStatus decision, String reason) {
    public static ApprovalStatus parseDecision(Map<String, Object> body) {
        return ApprovalStatus.valueOf(choice(body.get("decision"), "APPROVED", "RETURNED"));
    }

    public static ApprovalDecisionRequest from(Map<String, Object> body, ApprovalStatus decision) {
        return new ApprovalDecisionRequest(decision,
                decision == ApprovalStatus.RETURNED ? text(body.get("reason"), 500) : null);
    }
}
