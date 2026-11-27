package vn.huce.model;

import java.time.LocalDate;

/** Validated input for creating or updating a draft course section. */
public record SectionDraft(
        long semesterId,
        long subjectId,
        String code,
        long capacity,
        long sessionsPerWeek,
        long periodsPerSession,
        String requiredRoomType,
        LocalDate startDate,
        LocalDate endDate
) {
}
