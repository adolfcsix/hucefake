package vn.huce.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.huce.model.ScheduleRunStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleRunResponseTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void queuedRunPreservesIdContract() throws Exception {
        assertEquals(json.readTree("{\"id\":7}"),
                json.readTree(json.writeValueAsString(new ScheduleRunIdResponse(7L))));
    }

    @Test
    void appliedRunPreservesPatternCountIncludingZero() throws Exception {
        assertEquals(json.readTree("{\"status\":\"APPLIED\",\"patterns\":0}"),
                json.readTree(json.writeValueAsString(new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, 0))));
    }

    @Test
    void repeatedApplyOmitsPatternCount() throws Exception {
        assertEquals(json.readTree("{\"status\":\"APPLIED\"}"),
                json.readTree(json.writeValueAsString(new ScheduleRunApplyResponse(ScheduleRunStatus.APPLIED, null))));
    }
}
