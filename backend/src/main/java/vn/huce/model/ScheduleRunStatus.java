package vn.huce.model;

/** Mirrors the schedule_runs status constraint in PostgreSQL. */
public enum ScheduleRunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    APPLIED,
    DISCARDED;

    public boolean visibleToOtherStaff() {
        return this == COMPLETED || this == APPLIED;
    }

    public boolean discardable() {
        return this == QUEUED || this == COMPLETED || this == FAILED;
    }
}
