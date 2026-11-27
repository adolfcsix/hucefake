package vn.huce.model;

/** Internal run state used for authorization and mutation, separate from API response projections. */
public record ScheduleRunSnapshot(long id, long semesterId, long requestedBy,
                                  ScheduleRunStatus status, Integer hardViolations) {
    public boolean applicable() {
        return status == ScheduleRunStatus.COMPLETED && Integer.valueOf(0).equals(hardViolations);
    }
}
