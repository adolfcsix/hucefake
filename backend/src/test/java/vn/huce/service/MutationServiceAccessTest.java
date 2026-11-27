package vn.huce.service;

import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;
import vn.huce.repository.SectionRepository;
import vn.huce.repository.SchedulePatternRepository;
import vn.huce.repository.AuditRepository;

import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MutationServiceAccessTest {
    @Test
    void studentCannotBypassControllerAndMutateCatalogOrSections() {
        SqlStore store = mock(SqlStore.class);
        SchedulingService scheduling = mock(SchedulingService.class);
        SectionService sections = mock(SectionService.class);
        Actor student = new Actor(1L, "student", "STUDENT", null, null);
        CatalogMutationService catalog = new CatalogMutationService(store, scheduling);
        SectionRepository repository = mock(SectionRepository.class);
        SchedulePatternRepository patterns = mock(SchedulePatternRepository.class);
        AuditRepository audit = mock(AuditRepository.class);
        SectionMutationService section = new SectionMutationService(repository, patterns, audit, sections, scheduling);
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> catalog.room(student, Map.of(), null)).status());
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> catalog.createPeriod(student, Map.of())).status());
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> section.importSections(student, Map.of())).status());
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> section.deletePattern(student, 1L, "0")).status());
        verifyNoInteractions(store, scheduling, sections, repository, patterns, audit);
    }
}
