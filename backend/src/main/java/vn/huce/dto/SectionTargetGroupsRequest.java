package vn.huce.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validated and deduplicated target-group selection. */
public record SectionTargetGroupsRequest(Set<Long> groupIds) {
    public SectionTargetGroupsRequest {
        groupIds = Collections.unmodifiableSet(new LinkedHashSet<>(groupIds));
    }
}
