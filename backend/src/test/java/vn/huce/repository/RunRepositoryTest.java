package vn.huce.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RunRepositoryTest {
    private static final String UPDATE_PROGRESS =
            "UPDATE schedule_runs SET generations_run=? WHERE id=? AND status='RUNNING' AND generations_run<=?";
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RunRepository repository = new RunRepository(new SqlStore(jdbc));

    @Test
    void terminalOrMissingRunCannotReceiveLateProgress() {
        when(jdbc.update(UPDATE_PROGRESS, 10, 7L, 10)).thenReturn(0);

        repository.recordProgress(7L, 10, 1, 2.5);

        verify(jdbc).update(UPDATE_PROGRESS, 10, 7L, 10);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void acceptedProgressWritesHistoryAfterGuardedUpdate() {
        when(jdbc.update(UPDATE_PROGRESS, 10, 7L, 10)).thenReturn(1);

        repository.recordProgress(7L, 10, 1, 2.5);

        var order = inOrder(jdbc);
        order.verify(jdbc).update(UPDATE_PROGRESS, 10, 7L, 10);
        order.verify(jdbc).update(startsWith("INSERT INTO schedule_run_progress"), eq(7L),
                eq(10), eq(1), eq(2.5));
        order.verifyNoMoreInteractions();
    }

    @Test
    void staleGenerationDoesNotWriteHistory() {
        when(jdbc.update(UPDATE_PROGRESS, 5, 7L, 5)).thenReturn(0);

        repository.recordProgress(7L, 5, 0, 1.0);

        verify(jdbc).update(UPDATE_PROGRESS, 5, 7L, 5);
        verifyNoMoreInteractions(jdbc);
    }
}
