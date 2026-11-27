package vn.huce.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.huce.repository.RuntimeModeRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeModeTest {
    private final RuntimeModeRepository repository = mock(RuntimeModeRepository.class);
    private final BCryptPasswordEncoder bcrypt = mock(BCryptPasswordEncoder.class);
    private final DefaultApplicationArguments args = new DefaultApplicationArguments();

    @Test
    void differentSavedModeIsRejectedBeforeSeedOrWrite() {
        when(repository.savedMode()).thenReturn(List.of(true));
        assertThrows(IllegalStateException.class, () -> new RuntimeMode(repository, bcrypt, false).run(args));
        verify(repository).lock();
        verify(repository).savedMode();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(bcrypt);
    }

    @Test
    void privateModeRejectsExistingDemoMarker() {
        when(repository.savedMode()).thenReturn(List.of());
        when(repository.hasDemoSeed()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> new RuntimeMode(repository, bcrypt, false).run(args));
        verify(repository, never()).saveMode(anyBoolean());
        verifyNoInteractions(bcrypt);
    }

    @Test
    void demoModeRejectsExistingData() {
        when(repository.savedMode()).thenReturn(List.of());
        when(repository.hasExistingData()).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> new RuntimeMode(repository, bcrypt, true).run(args));
        verify(repository, never()).loadDemoCatalog();
        verify(repository, never()).saveMode(anyBoolean());
        verifyNoInteractions(bcrypt);
    }

    @Test
    void freshPrivateModeNeverSeedsDemo() {
        when(repository.savedMode()).thenReturn(List.of());
        new RuntimeMode(repository, bcrypt, false).run(args);
        var order = inOrder(repository);
        order.verify(repository).lock();
        order.verify(repository).savedMode();
        order.verify(repository).hasDemoSeed();
        order.verify(repository).saveMode(false);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(bcrypt);
    }

    @Test
    void freshDemoLoadsCatalogBeforeSeedingAndSavingMode() {
        when(repository.savedMode()).thenReturn(List.of());
        when(bcrypt.encode("Fm05Demo@2026")).thenReturn("hash");
        new RuntimeMode(repository, bcrypt, true).run(args);
        var order = inOrder(repository, bcrypt);
        order.verify(repository).lock();
        order.verify(repository).savedMode();
        order.verify(repository).hasExistingData();
        order.verify(repository).loadDemoCatalog();
        order.verify(bcrypt).encode("Fm05Demo@2026");
        order.verify(repository).seedDemoAccountsAndSchedules("hash");
        order.verify(repository).saveMode(true);
    }

    @Test
    void alreadyInitializedDemoDoesNotSeedAgain() {
        when(repository.savedMode()).thenReturn(List.of(true));
        new RuntimeMode(repository, bcrypt, true).run(args);
        verify(repository).lock();
        verify(repository).savedMode();
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(bcrypt);
    }
}
