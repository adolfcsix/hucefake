package vn.huce.model;

/** Locked proposal state; action-specific JSON is decoded only at the service boundary. */
public record ProposalSnapshot(long requestedBy, Long semesterId, Long runId,
                               ProposalAction action, String parametersJson, String resultJson,
                               boolean cancelled, boolean expired) {
}
