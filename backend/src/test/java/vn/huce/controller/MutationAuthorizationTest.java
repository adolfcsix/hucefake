package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.core.Authentication;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.service.ApprovalService;
import vn.huce.service.CatalogMutationService;
import vn.huce.service.MutationService;
import vn.huce.service.RunService;
import vn.huce.service.SectionMutationService;
import vn.huce.service.SectionService;
import vn.huce.service.SessionMutationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A denied role must never reach the transaction or the business service. */
class MutationAuthorizationTest {
    private final MutationService writes = mock(MutationService.class);
    private final CatalogMutationService catalog = mock(CatalogMutationService.class);
    private final SectionService sections = mock(SectionService.class);
    private final SectionMutationService sectionChanges = mock(SectionMutationService.class);
    private final SessionMutationService sessions = mock(SessionMutationService.class);
    private final ApprovalService approvals = mock(ApprovalService.class);
    private final RunService runs = mock(RunService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final Map<String, Object> body = Map.of();
    private final CatalogMutationController catalogController = new CatalogMutationController(writes, catalog);
    private final SectionController sectionController = new SectionController(writes, sections, sectionChanges);
    private final SessionMutationController sessionController = new SessionMutationController(writes, sessions);
    private final ApprovalController approvalController = new ApprovalController(writes, approvals);
    private final RunController runController = new RunController(writes, runs);

    private Authentication principal(String role) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Actor(1L, "test", role, 2L, 3L));
        return auth;
    }

    private void denied(Executable... calls) {
        for (Executable call : calls) {
            ApiFailure error = assertThrows(ApiFailure.class, call);
            assertEquals(403, error.status());
            assertEquals("FORBIDDEN", error.code());
        }
        verifyNoInteractions(writes, catalog, sections, sectionChanges, sessions, approvals, runs, request);
    }

    @Test
    void nonStaffCannotMutateCatalog() {
        for (String role : List.of("STUDENT", "LECTURER", "MANAGER")) {
            Authentication auth = principal(role);
            denied(() -> catalogController.createRoom(auth, request, body),
                    () -> catalogController.updateRoom(auth, request, 1L, body),
                    () -> catalogController.createHoliday(auth, request, body),
                    () -> catalogController.updateHoliday(auth, request, 1L, body),
                    () -> catalogController.createPeriod(auth, request, body));
        }
    }

    @Test
    void nonStaffCannotMutateSectionsOrPatterns() {
        for (String role : List.of("STUDENT", "LECTURER", "MANAGER")) {
            Authentication auth = principal(role);
            denied(() -> sectionController.create(auth, request, body),
                    () -> sectionController.imports(auth, request, body),
                    () -> sectionController.update(auth, request, 1L, body),
                    () -> sectionController.cancel(auth, request, 1L, "0"),
                    () -> sectionController.assign(auth, request, 1L, body),
                    () -> sectionController.groups(auth, request, 1L, body),
                    () -> sectionController.createPattern(auth, request, body),
                    () -> sectionController.updatePattern(auth, request, 1L, body),
                    () -> sectionController.deletePattern(auth, request, 1L, "0"));
        }
    }

    @Test
    void nonStaffCannotChangePublishedSessionsOrDecideRequests() {
        for (String role : List.of("STUDENT", "LECTURER", "MANAGER")) {
            Authentication auth = principal(role);
            denied(() -> sessionController.reschedule(auth, request, body),
                    () -> sessionController.patch(auth, request, 1L, body),
                    () -> sessionController.exception(auth, request, 1L, body),
                    () -> sessionController.decide(auth, request, 1L, body));
        }
    }

    @Test
    void nonLecturerCannotSubmitAvailabilityOrSessionRequests() {
        for (String role : List.of("STUDENT", "STAFF", "MANAGER")) {
            Authentication auth = principal(role);
            denied(() -> sessionController.setAvailability(auth, request, body),
                    () -> sessionController.request(auth, request, 1L, body));
        }
    }

    @Test
    void approvalSubmissionAndPublicationHaveSeparateRoles() {
        for (String role : List.of("STUDENT", "LECTURER", "MANAGER")) {
            Authentication auth = principal(role);
            denied(() -> approvalController.create(auth, request, body),
                    () -> approvalController.submit(auth, request, 1L, body));
        }
        for (String role : List.of("STUDENT", "LECTURER", "STAFF")) {
            Authentication auth = principal(role);
            denied(() -> approvalController.decide(auth, request, 1L, body),
                    () -> approvalController.apply(auth, request, 1L, body),
                    () -> approvalController.publish(auth, request, 1L, body));
        }
    }

    @Test
    void studentAndLecturerCannotOperateGeneticScheduling() {
        for (String role : List.of("STUDENT", "LECTURER")) {
            Authentication auth = principal(role);
            denied(() -> runController.enqueue(auth, request, body),
                    () -> runController.apply(auth, request, 1L, body),
                    () -> runController.discard(auth, request, 1L, body));
        }
    }
}
