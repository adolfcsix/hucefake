package vn.huce.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.huce.model.ApprovalStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovalResponseTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void draftPreservesId() throws Exception {
        assertEquals(json.readTree("{\"id\":7}"),
                json.readTree(json.writeValueAsString(new TimetableVersionIdResponse(7L))));
    }

    @Test
    void submissionPreservesApprovalId() throws Exception {
        assertEquals(json.readTree("{\"approvalId\":9}"),
                json.readTree(json.writeValueAsString(new ApprovalSubmissionResponse(9L))));
    }

    @Test
    void publicationPreservesExistingJson() throws Exception {
        assertEquals(json.readTree("{\"versionId\":4,\"sessions\":12,\"sections\":2}"),
                json.readTree(json.writeValueAsString(new TimetablePublicationResponse(4L, 12L, 2))));
    }

    @Test
    void decisionsPreserveStatusNames() throws Exception {
        for (ApprovalStatus status : new ApprovalStatus[]{ApprovalStatus.APPROVED, ApprovalStatus.RETURNED}) {
            assertEquals(json.readTree("{\"status\":\"" + status.name() + "\"}"),
                    json.readTree(json.writeValueAsString(new ApprovalDecisionResponse(status))));
        }
    }
}
