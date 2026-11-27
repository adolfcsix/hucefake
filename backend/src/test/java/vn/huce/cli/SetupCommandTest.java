package vn.huce.cli;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.SetupRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetupCommandTest {
    @Test
    void optionsRejectMissingDuplicateAndUnknownFlags() {
        assertThrows(ApiFailure.class, () -> SetupCommand.options(new String[]{"setup", "account", "--role"}));
        assertThrows(ApiFailure.class, () -> SetupCommand.options(new String[]{"setup", "account", "--role", "STAFF", "--role", "STUDENT"}));
        assertThrows(ApiFailure.class, () -> SetupCommand.options(new String[]{"setup", "account", "--password", "not-a-cli-option"}));
        assertEquals(Map.of("role", "STAFF", "unit", "QA"), SetupCommand.options(
                new String[]{"setup", "account", "--role", "STAFF", "--unit", "QA"}));
    }

    @Test
    void passwordLengthUsesCodePointsAndBcryptByteLimit() {
        assertThrows(ApiFailure.class, () -> SetupCommand.validatePassword("a".repeat(9)));
        assertDoesNotThrow(() -> SetupCommand.validatePassword("a".repeat(10)));
        assertDoesNotThrow(() -> SetupCommand.validatePassword("é".repeat(36)));
        assertThrows(ApiFailure.class, () -> SetupCommand.validatePassword("é".repeat(37)));
    }

    @Test
    void missingCatalogListIsEmptyButMalformedListFails() {
        assertEquals(List.of(), SetupCommand.list(Map.of(), "units"));
        assertThrows(ApiFailure.class, () -> SetupCommand.list(Map.of("units", "wrong"), "units"));
        assertThrows(ApiFailure.class, () -> SetupCommand.list(Map.of("units", List.of("wrong")), "units"));
    }

    @Test
    void reversedSemesterDatesDoNotWriteSemester() {
        SetupRepository repository = mock(SetupRepository.class);
        assertThrows(ApiFailure.class, () -> SetupCommand.catalog(repository, Map.of(
                "semesters", List.of(Map.of("yearCode", "2026-2027", "code", "QA.1",
                        "startDate", "2026-12-31", "endDate", "2026-09-01")))));
        verifyNoInteractions(repository);
    }
}
