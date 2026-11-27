package vn.huce.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulePatternSnapshotTest {
    private final ObjectMapper json = new ObjectMapper();

    private SchedulePatternSnapshot snapshot(Long campusId) {
        return new SchedulePatternSnapshot(42L, 7L, 12L, 4L, 2, 1, 3,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1), "MANUAL",
                null, 1L, LocalDateTime.of(2026, 9, 1, 8, 0, 0, 123000000), 2, campusId);
    }

    @Test
    void conflictDetailsKeepAllOriginalColumnsAndIsoStrings() throws Exception {
        var actual = json.readTree(json.writeValueAsString(snapshot(null).conflictDetails()));
        var expected = json.readTree("""
                {"id":42,"section_id":7,"staff_id":12,"room_id":4,"day_of_week":2,
                 "period_from":1,"period_count":3,"effective_from":"2026-09-01",
                 "effective_to":"2026-12-01","source":"MANUAL","run_id":null,
                 "created_by":1,"created_at":"2026-09-01T08:00:00.123","version":2}
                """);
        assertEquals(expected, actual);
        assertFalse(actual.has("campus_id"));
        assertTrue(actual.get("run_id").isNull());
    }

    @Test
    void joinedCampusIsPresentOnlyForJoinedSnapshot() throws Exception {
        var details = json.readTree(json.writeValueAsString(snapshot(5L).conflictDetails()));
        assertEquals(5L, details.get("campus_id").longValue());
        assertEquals(15, details.size());
    }
}
