package vn.huce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import vn.huce.model.ScheduleRunStatus;

/** The pattern count is absent when the run was already applied. */
public record ScheduleRunApplyResponse(ScheduleRunStatus status,
                                       @JsonInclude(JsonInclude.Include.NON_NULL) Integer patterns) {
}
