package vn.huce.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SectionResponseTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sectionIdResponsePreservesExistingJson() throws Exception {
        assertEquals(json.readTree("{\"id\":9}"), json.readTree(json.writeValueAsString(new SectionIdResponse(9L))));
    }

    @Test
    void importResponsePreservesExistingJson() throws Exception {
        assertEquals(json.readTree("{\"imported\":2}"), json.readTree(json.writeValueAsString(new SectionImportResponse(2))));
    }

    @Test
    void patternIdResponsePreservesExistingJson() throws Exception {
        assertEquals(json.readTree("{\"id\":42}"),
                json.readTree(json.writeValueAsString(new SchedulePatternIdResponse(42L))));
    }
}
