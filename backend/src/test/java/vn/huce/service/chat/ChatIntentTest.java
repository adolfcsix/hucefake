package vn.huce.service.chat;

import vn.huce.model.ChatSemester;
import org.junit.jupiter.api.Test;
import vn.huce.exception.ApiFailure;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ChatIntentTest {
    @Test
    void recognizesVietnameseAndFollowUp() {
        assertEquals("SCHEDULE", ChatIntent.recognize("Lịch học hôm nay", null));
        assertEquals("GUIDE", ChatIntent.recognize("Cách tạo lớp nháp", null));
        assertEquals("SCHEDULE", ChatIntent.recognize("Ngày mai", "SCHEDULE"));
    }

    @Test
    void permissionsRemainScopedByRole() {
        assertTrue(ChatPermissions.allowed("STAFF").contains("DRAFT"));
        assertFalse(ChatPermissions.allowed("STUDENT").contains("GENERATE"));
        assertFalse(ChatPermissions.allowed("MANAGER").contains("DRAFT"));
        assertTrue(ChatPermissions.allowed("LECTURER").contains("AVAILABILITY"));
        assertTrue(ChatPermissions.allowed("UNKNOWN").isEmpty());
    }

    @Test
    void nextSessionWindowUsesSelectedSemesterEnd() {
        LocalDate now = LocalDate.of(2026, 11, 27);
        var semester = new ChatSemester(7L, "HK1", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        var window = ChatDateRangeParser.window("buổi tiếp theo", "NEXT", now, semester);
        assertEquals("2026-11-27", window.from());
        assertEquals("2026-12-31", window.to());
    }

    @Test
    void parsesDatesAndRejectsInvalidRange() {
        LocalDate now = LocalDate.of(2026, 11, 27);
        assertEquals("2026-11-28", ChatDateRangeParser.window("ngày mai", "SCHEDULE", now, null).from());
        assertEquals("2026-11-30", ChatDateRangeParser.window("thứ hai tuần sau", "SCHEDULE", now, null).from());
        assertThrows(ApiFailure.class, () -> ChatDateRangeParser.window("31/02/2026", "SCHEDULE", now, null));
        assertThrows(ApiFailure.class, () -> ChatDateRangeParser.window("01/01/2026 đến 15/03/2026", "SCHEDULE", now, null));
    }
}
