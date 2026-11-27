package vn.huce.model;

/** Placement proposed by the solver; revalidated against current data before applying. */
public record ScheduleRunItem(long sectionId, long staffId, long roomId,
                              int dayOfWeek, int periodFrom, int periodCount) {
}
