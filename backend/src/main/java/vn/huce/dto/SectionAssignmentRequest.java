package vn.huce.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validated lecturer selection, preserving the order of submitted assistant IDs. */
public record SectionAssignmentRequest(long staffId, Set<Long> assistantIds) {
    public SectionAssignmentRequest {
        assistantIds = Collections.unmodifiableSet(new LinkedHashSet<>(assistantIds));
    }
}
