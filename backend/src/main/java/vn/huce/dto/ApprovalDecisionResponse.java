package vn.huce.dto;

import vn.huce.model.ApprovalStatus;

/** Result of an approval decision. */
public record ApprovalDecisionResponse(ApprovalStatus status) {
}
