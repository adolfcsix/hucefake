package vn.huce.service.chat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiQuotaTest {
    @Test
    void quotaAllowsEightRequestsPerAccountAndResetsAtWindowBoundary() {
        AtomicLong now = new AtomicLong(1000);
        GeminiQuota quota = new GeminiQuota(now::get);
        for (int i = 0; i < 8; i++) {
            assertTrue(quota.take(1L));
        }
        assertFalse(quota.take(1L));
        assertTrue(quota.take(2L));
        now.set(60_999);
        assertFalse(quota.take(1L));
        now.set(61_000);
        assertTrue(quota.take(1L));
    }

    @Test
    void activeAccountsAreBoundedAndExpiredBucketsAreRemoved() {
        AtomicLong now = new AtomicLong(1000);
        GeminiQuota quota = new GeminiQuota(now::get);
        for (long id = 1; id <= 1500; id++) {
            assertTrue(quota.take(id));
        }
        assertFalse(quota.take(1501L));
        assertTrue(quota.take(1L));
        now.addAndGet(60_000);
        assertTrue(quota.take(1501L));
    }
}
