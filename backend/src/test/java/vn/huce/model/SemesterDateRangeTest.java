package vn.huce.model;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemesterDateRangeTest {
    @Test
    void includesBothBoundariesAndRejectsOutsideDates() {
        var start = LocalDate.of(2026, 9, 1);
        var end = LocalDate.of(2026, 12, 31);
        var range = new SemesterDateRange(start, end);
        assertTrue(range.contains(start));
        assertTrue(range.contains(end));
        assertFalse(range.contains(start.minusDays(1)));
        assertFalse(range.contains(end.plusDays(1)));
    }
}
