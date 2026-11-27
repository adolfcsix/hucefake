package vn.huce.model;

import vn.huce.model.AvailabilityEntry.Kind;

/** Persisted availability coordinates used by scheduling constraints and fitness. */
public record StaffAvailability(long staffId, long semesterId, int dayOfWeek,
                                int periodFrom, int periodTo, Kind kind) {
    public boolean overlaps(int first, int last) {
        return periodFrom <= last && periodTo >= first;
    }
}
