package vn.huce.model;

import java.time.LocalDate;

/** Semester data required by course-section creation and import. */
public record SectionSemester(long id, String code, String status,
                              LocalDate startDate, LocalDate endDate) {
}
