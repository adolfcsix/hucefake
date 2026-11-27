package vn.huce.service;

import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.CatalogRepository;

import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final CatalogService service = new CatalogService(repository);

    @Test
    void studentAndLecturerSeeOnlySemesters() {
        when(repository.semesters()).thenReturn(List.of(Map.of("id", 1L)));
        assertEquals(Map.of("semesters", List.of(Map.of("id", 1L))),
                service.catalog(new Actor(1L, "student", "STUDENT", null, null)));
        assertEquals(Map.of("semesters", List.of(Map.of("id", 1L))),
                service.catalog(new Actor(2L, "lecturer", "LECTURER", 2L, 3L)));
        verify(repository, times(2)).semesters();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void staffCatalogPassesScopeToRepository() {
        Actor actor = new Actor(1L, "staff", "STAFF", 2L, 3L);
        Map<String, Object> result = service.catalog(actor);
        assertEquals(8, result.size());
        verify(repository).campuses(false, 2L);
        verify(repository).subjects(false, 3L);
    }

    @Test
    void managerUsesUnrestrictedCatalogScope() {
        service.catalog(new Actor(1L, "manager", "MANAGER", null, null));
        verify(repository).campuses(true, null);
        verify(repository).subjects(true, null);
    }

    @Test
    void healthPreservesNotReadyError() {
        when(repository.ready()).thenReturn(false, true);
        ApiFailure failure = assertThrows(ApiFailure.class, service::health);
        assertEquals(503, failure.status());
        assertEquals("NOT_READY", failure.code());
        assertEquals(Map.of("status", "ok"), service.health());
    }
}
