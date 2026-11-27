package vn.huce.model;

import java.time.LocalDate;

public record HolidayDraft(Long campusId, LocalDate startDate, LocalDate endDate, String name) {
}
