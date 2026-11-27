package vn.huce.validation;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;

import static org.junit.jupiter.api.Assertions.*;

class InputsTest {
    @Test
    void numericInputPreservesExactIntegerAndSafeIdContract() {
        assertEquals(12L, Inputs.number(" 12.0 ", 1, 20));
        assertEquals(9007199254740991L, Inputs.id("9007199254740991"));
        for (Object input : new Object[]{1.5, "9007199254740992", "NaN", true, 0}) {
            assertEquals(400, assertThrows(ApiFailure.class, () -> Inputs.id(input)).status());
        }
    }

    @Test
    void datesRequireExactFormatAndValidCalendarDay() {
        assertEquals("2024-02-29", Inputs.date("2024-02-29"));
        for (String input : new String[]{"2026-02-29", "2026-2-01", "2026-11-31", " 2026-11-27"}) {
            assertEquals("VALIDATION_ERROR", assertThrows(ApiFailure.class, () -> Inputs.date(input)).code());
        }
    }

    @Test
    void textTrimsButChoiceIsExact() {
        assertEquals("HUCE", Inputs.text(" HUCE ", 4));
        assertThrows(ApiFailure.class, () -> Inputs.text("  ", 4));
        assertThrows(ApiFailure.class, () -> Inputs.choice(" STAFF ", "STAFF"));
    }

    @Test
    void staffScopeRequiresNumericMatchingUnit() {
        Actor staff = new Actor(1L, "staff", "STAFF", 2L, 3L);
        assertDoesNotThrow(() -> Inputs.unit(staff, 3));
        assertEquals(403, assertThrows(ApiFailure.class, () -> Inputs.unit(staff, "3")).status());
        assertEquals(403, assertThrows(ApiFailure.class, () -> Inputs.unit(staff, 4L)).status());
        assertEquals(403, assertThrows(ApiFailure.class, () -> Inputs.role(staff, "MANAGER")).status());
    }

    @Test
    void staleVersionRetainsCurrentRecordInConflictDetails() {
        Map<String, Object> row = Map.of("id", 5L, "version", 3);
        assertDoesNotThrow(() -> Inputs.version("3", row));
        ApiFailure error = assertThrows(ApiFailure.class, () -> Inputs.version("2", row));
        assertEquals(409, error.status());
        assertEquals("VERSION_CONFLICT", error.code());
        assertEquals(row, error.details());
    }
}
