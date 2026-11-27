package vn.huce.service.chat;

import vn.huce.model.ChatSemester;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.CatalogRepository;
import vn.huce.service.ApprovalService;
import vn.huce.service.SectionService;
import vn.huce.service.SessionMutationService;
import vn.huce.service.AvailabilityService;
import vn.huce.service.SessionReadService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatReplyServiceTest {
    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final ProposalService proposals = mock(ProposalService.class);
    private final SectionService sections = mock(SectionService.class);
    private final ApprovalService approvals = mock(ApprovalService.class);
    private final SessionReadService reads = mock(SessionReadService.class);
    private final SessionMutationService sessions = mock(SessionMutationService.class);
    private final AvailabilityService availability = mock(AvailabilityService.class);
    private final ChatReplyService service = new ChatReplyService(catalog, proposals, sections, approvals, reads, sessions, availability);
    private final Actor student = new Actor(1L, "student", "STUDENT", null, null);

    @Test
    void unauthorizedActionCannotReachDomainService() {
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> service.answer(student, "APPLY", null, "áp dụng", Map.of())).status());
        verifyNoInteractions(catalog, proposals, sections, approvals, reads, sessions, availability);
    }

    @Test
    void draftPromptRetainsIsoDateStringsAndSemesterIdentifier() {
        Actor staff = new Actor(2L, "staff", "STAFF", 3L, 4L);
        var semester = new ChatSemester(7L, "HK1", java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2026, 12, 31));
        when(catalog.subjects(false, 4L)).thenReturn(List.of());
        var result = service.answer(staff, "DRAFT", semester, "Tạo lớp", Map.of());
        assertEquals(Map.of("semesterId", 7L, "from", "2026-09-01", "to", "2026-12-31",
                "subjects", List.of()), result.get("draft"));
        verifyNoInteractions(proposals, sections, approvals, reads, sessions, availability);
    }

    @Test
    void lecturerAvailabilityReadsOnlyFromItsOwningService() {
        Actor lecturer = new Actor(2L, "lecturer", "LECTURER", 3L, 4L);
        var semester = new ChatSemester(7L, "HK1", java.time.LocalDate.of(2026, 9, 1),
                java.time.LocalDate.of(2026, 12, 31));
        when(availability.find(lecturer, 7L)).thenReturn(List.of());
        assertEquals("availability", service.answer(lecturer, "AVAILABILITY", semester,
                "Giờ bận", Map.of()).get("target"));
        verify(availability).find(lecturer, 7L);
        verifyNoInteractions(sessions, proposals, sections, approvals, reads, catalog);
    }

    @Test
    void notificationsRetainResponseContractWithoutSemester() {
        when(reads.notifications(student, Map.of("page", "1", "pageSize", "8")))
                .thenReturn(Map.of("total", 0L, "items", List.of()));
        assertEquals(Map.of("message", "Bạn chưa có thông báo.", "target", "notifications",
                "items", List.of(), "total", 0L), service.answer(student, "NOTICES", null, "thông báo", Map.of()));
    }

    @Test
    void markingNotificationsRequiresProposalRatherThanImmediateWrite() {
        var proposal = Map.<String, Object>of("id", 3L);
        when(proposals.prepare(student, "READ_NOTICES", null, Map.of())).thenReturn(proposal);
        var result = service.answer(student, "READ_NOTICES", null, "đánh dấu đã đọc", Map.of());
        assertEquals(proposal, result.get("proposal"));
        verify(proposals).prepare(student, "READ_NOTICES", null, Map.of());
        verifyNoMoreInteractions(proposals);
        verifyNoInteractions(reads, catalog, sections, approvals, sessions);
    }
}
