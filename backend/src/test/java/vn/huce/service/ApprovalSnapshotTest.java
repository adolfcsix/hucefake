package vn.huce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.huce.model.ApprovalSnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovalSnapshotTest {
    @Test
    void typedSnapshotPreservesStoredJsonContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ApprovalSnapshot snapshot = new ApprovalSnapshot(1L, 2L, 3L, 4L,
                1, 2, 3, "2026-09-01", "2026-12-31", 5, "HP01", "Toán", "P101");
        String legacy = """
                {"id":1,"section":2,"staff":3,"room":4,"day":1,"first":2,"count":3,
                 "from":"2026-09-01","to":"2026-12-31","version":5,
                 "sectionCode":"HP01","subjectName":"Toán","roomCode":"P101"}
                """;
        assertEquals(mapper.readTree(legacy), mapper.readTree(mapper.writeValueAsString(snapshot)));
        assertEquals(snapshot, mapper.readValue(legacy, ApprovalSnapshot.class));
    }
}
