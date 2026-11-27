package vn.huce.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatExchangeSnapshotTest {
    @Test
    void proposalOutcomePreservesPriorityAndPendingState() {
        assertEquals("CONFIRMED", new ChatExchangeSnapshot(1L, "Q", "T", "{}", "{}", true, true).outcome());
        assertEquals("CANCELLED", new ChatExchangeSnapshot(1L, "Q", "T", "{}", null, true, true).outcome());
        assertEquals("EXPIRED", new ChatExchangeSnapshot(1L, "Q", "T", "{}", null, false, true).outcome());
        assertEquals("PENDING", new ChatExchangeSnapshot(1L, "Q", "T", "{}", null, false, false).outcome());
    }
}
