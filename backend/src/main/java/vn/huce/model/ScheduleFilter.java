package vn.huce.model;

import java.time.LocalDate;

public record ScheduleFilter(long semesterId, LocalDate from, LocalDate to, View view, Long targetId) {
    public enum View {
        PERSONAL, ALL, ROOM, STAFF, GROUP, SECTION
    }
}
