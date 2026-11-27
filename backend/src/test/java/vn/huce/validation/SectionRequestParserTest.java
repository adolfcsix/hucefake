package vn.huce.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.exception.ApiFailure;

import static org.junit.jupiter.api.Assertions.*;

class SectionRequestParserTest {
    @Test
    void assignmentAcceptsExistingNumericStringsAndPreservesOrder() {
        var input = Map.<String, Object>of("staffId", "12", "assistantIds", List.of("9", 7, 9L));
        var request = SectionRequestParser.assignment(input);
        assertEquals(12L, request.staffId());
        assertEquals(List.of(9L, 7L), new ArrayList<>(request.assistantIds()));
        assertThrows(UnsupportedOperationException.class, () -> request.assistantIds().add(8L));
        assertEquals(List.of("9", 7, 9L), input.get("assistantIds"));
    }

    @Test
    void omittedAndNullSelectionsRemainEmpty() {
        assertTrue(SectionRequestParser.assignment(Map.of("staffId", 12)).assistantIds().isEmpty());
        assertTrue(SectionRequestParser.groups(Map.of()).groupIds().isEmpty());
        var input = new LinkedHashMap<String, Object>();
        input.put("groupIds", null);
        assertTrue(SectionRequestParser.groups(input).groupIds().isEmpty());
    }

    @Test
    void mainLecturerCannotAlsoBeAssistant() {
        var error = assertThrows(ApiFailure.class, () ->
                SectionRequestParser.assignment(Map.of("staffId", "12", "assistantIds", List.of(12L))));
        assertEquals(400, error.status());
        assertEquals("VALIDATION_ERROR", error.code());
    }

    @Test
    void groupsRejectInvalidShapesIdsAndOversizedRawList() {
        for (Object value : List.of("1,2", List.of(0), List.of(1.5), List.of(true),
                List.of(9007199254740992L), Collections.nCopies(201, 1))) {
            assertEquals(400, assertThrows(ApiFailure.class,
                    () -> SectionRequestParser.groups(Map.of("groupIds", value))).status());
        }
        assertEquals(1, SectionRequestParser.groups(
                Map.of("groupIds", Collections.nCopies(200, 1))).groupIds().size());
    }

    @Test
    void groupsDeduplicateInFirstOccurrenceOrder() {
        var request = SectionRequestParser.groups(Map.of("groupIds", List.of("3", 2, 3L)));
        assertEquals(List.of(3L, 2L), new ArrayList<>(request.groupIds()));
        assertThrows(UnsupportedOperationException.class, () -> request.groupIds().clear());
    }
}
