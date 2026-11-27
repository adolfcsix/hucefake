package vn.huce.repository;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.huce.exception.ApiFailure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetupRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SetupRepository repository = new SetupRepository(new SqlStore(jdbc));

    @Test
    void missingReferencePreservesValidationMessage() {
        when(jdbc.queryForList("SELECT id FROM organizational_units WHERE code=?", Long.class, "QA"))
                .thenReturn(List.of());
        ApiFailure error = assertThrows(ApiFailure.class, () -> repository.unitId("QA", "đơn vị QA"));
        assertEquals(400, error.status());
        assertEquals("Không tìm thấy đơn vị QA.", error.getMessage());
    }

    @Test
    void existingReferenceReturnsTypedIdentifier() {
        when(jdbc.queryForList("SELECT id FROM campuses WHERE code=? AND status='ACTIVE'", Long.class, "CS1"))
                .thenReturn(List.of(17L));
        assertEquals(17L, repository.activeCampusId("CS1", "cơ sở"));
    }

    @Test
    void userInsertWithoutMatchingRolePreservesNotFound() {
        when(jdbc.queryForList(startsWith("INSERT INTO users"), eq(Long.class),
                eq("qa"), eq("qa@example.test"), eq("hash"), eq("UNKNOWN"))).thenReturn(List.of());
        assertEquals(404, assertThrows(ApiFailure.class,
                () -> repository.createUser("qa", "qa@example.test", "hash", "UNKNOWN")).status());
    }

    @Test
    void staffInsertPreservesNullableUnit() {
        when(jdbc.queryForList(startsWith("INSERT INTO staff_profiles"), eq(Long.class),
                eq(9L), eq("GV1"), isNull())).thenReturn(List.of(21L));
        assertEquals(21L, repository.createStaff(9L, "GV1", null));
    }
}
