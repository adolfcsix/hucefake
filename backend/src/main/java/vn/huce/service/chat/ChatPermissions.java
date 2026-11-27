package vn.huce.service.chat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ChatPermissions {
    private ChatPermissions() {
    }

    static Set<String> allowed(String role) {
        Set<String> actions = new LinkedHashSet<>(List.of("NEXT", "SCHEDULE", "NOTICES",
                "READ_NOTICES", "GUIDE"));
        switch (role) {
            case "STAFF" -> actions.addAll(List.of("SECTIONS", "APPROVALS", "REQUESTS",
                    "GENERATE", "APPLY", "DRAFT"));
            case "MANAGER" -> actions.addAll(List.of("SECTIONS", "APPROVALS", "STATS",
                    "GENERATE", "APPLY"));
            case "LECTURER" -> actions.addAll(List.of("REQUESTS", "AVAILABILITY"));
            case "STUDENT" -> {
                // Student uses only common read and notification actions.
            }
            default -> actions.clear();
        }
        return actions;
    }
}
