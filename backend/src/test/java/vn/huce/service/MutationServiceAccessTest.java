package vn.huce.service;

import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SqlStore;

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
        SectionMutationService section = new SectionMutationService(store, sections, scheduling);
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> catalog.room(student, Map.of(), null)).status());
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> catalog.createPeriod(student, Map.of())).status());
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> section.importSections(student, Map.of())).status());
        assertEquals(403, assertThrows(ApiFailure.class,
                () -> section.deletePattern(student, 1L, "0")).status());
        verifyNoInteractions(store, scheduling, sections);
    }
}
