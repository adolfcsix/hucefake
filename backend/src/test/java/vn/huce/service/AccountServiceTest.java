package vn.huce.service;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.AccountRepository;
import vn.huce.repository.AccountRepository.AccountRecord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private final AccountRepository repository = mock(AccountRepository.class);
    private final BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
    private AccountService service;

    @BeforeEach
    void setup() {
        when(encoder.encode("invalid-credentials")).thenReturn("dummy");
        service = new AccountService(repository, encoder);
    }

    @Test
    void unknownUserStillChecksDummyHashAndReturnsGenericFailure() {
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());
        assertEquals("INVALID_CREDENTIALS", assertThrows(ApiFailure.class,
                () -> service.login("missing", "password", null)).code());
        verify(encoder).matches("password", "dummy");
    }

    @Test
    void passwordsOver72Utf8BytesAreNotPassedToBcrypt() {
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());
        assertEquals(401, assertThrows(ApiFailure.class,
                () -> service.login("missing", "ộ".repeat(25), null)).status());
        verify(encoder, never()).matches(anyString(), anyString());
    }

    @Test
    void staffWithoutActiveProfileCannotLoginOrRefreshSession() {
        Actor actor = new Actor(1L, "staff", "STAFF", null, null);
        when(repository.findByUsername("staff")).thenReturn(Optional.of(new AccountRecord(actor, "hash", "ACTIVE")));
        when(encoder.matches("password", "hash")).thenReturn(true);
        assertEquals(401, assertThrows(ApiFailure.class,
                () -> service.login("staff", "password", null)).status());
        assertNull(service.active("staff"));
    }

    @Test
    void inactiveAccountCannotLoginOrRefreshSession() {
        Actor actor = new Actor(1L, "student", "STUDENT", null, null);
        when(repository.findByUsername("student")).thenReturn(Optional.of(new AccountRecord(actor, "hash", "INACTIVE")));
        when(encoder.matches("password", "hash")).thenReturn(true);
        assertThrows(ApiFailure.class, () -> service.login("student", "password", null));
        assertNull(service.active("student"));
    }

    @Test
    void expectedDemoRoleMustMatchActualRole() {
        Actor actor = new Actor(1L, "student", "STUDENT", null, null);
        when(repository.findByUsername("student")).thenReturn(Optional.of(new AccountRecord(actor, "hash", "ACTIVE")));
        when(encoder.matches("password", "hash")).thenReturn(true);
        assertEquals(actor, service.login("student", "password", "STUDENT"));
        assertEquals(401, assertThrows(ApiFailure.class,
                () -> service.login("student", "password", "MANAGER")).status());
    }
}
