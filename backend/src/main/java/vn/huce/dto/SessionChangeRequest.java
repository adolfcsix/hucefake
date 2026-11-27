package vn.huce.dto;

import java.util.Map;
import vn.huce.model.SessionChangeAction;

import static vn.huce.validation.Inputs.choice;
import static vn.huce.validation.Inputs.text;

/** Validated common fields of a session adjustment or lecturer request. */
public record SessionChangeRequest(SessionChangeAction action, String reason) {
    public static SessionChangeRequest adjustment(Map<String, Object> body) {
        return new SessionChangeRequest(SessionChangeAction.valueOf(
                choice(body.get("action"), "CANCEL", "RESCHEDULE", "ROOM_CHANGE")),
                text(body.get("reason"), 500));
    }

    public static SessionChangeRequest lecturer(Map<String, Object> body) {
        return new SessionChangeRequest(SessionChangeAction.valueOf(
                choice(body.get("action"), "CANCEL", "RESCHEDULE")),
                text(body.get("reason"), 500));
    }
}
