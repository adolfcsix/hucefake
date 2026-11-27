package vn.huce.repository;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.huce.exception.ApiFailure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlStoreTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private static class TestStore extends SqlStore {
        private List<Map<String, Object>> result = List.of();
        private String lastSql;
        private Object[] lastArgs;

        TestStore(JdbcTemplate jdbc) {
            super(jdbc);
        }

        @Override
        public List<Map<String, Object>> rows(String sql, Object... args) {
            lastSql = sql;
            lastArgs = args;
            return result;
        }
    }

    private final TestStore store = new TestStore(jdbc);

    @Test
    void missingRowRetainsNotFoundContract() {
        assertNull(store.one("query"));
        assertFalse(store.exists("query"));
        ApiFailure error = assertThrows(ApiFailure.class, () -> store.need("query"));
        assertEquals(404, error.status());
        assertEquals("NOT_FOUND", error.code());
    }

    @Test
    void unpagedQueriesRetainListResponse() {
        store.result = List.of(Map.of("id", 1L));
        assertEquals(store.result, store.paged(Map.of(), "SELECT id FROM sample WHERE id=?", List.of(1L), "id"));
        assertEquals("SELECT id FROM sample WHERE id=? ORDER BY id", store.lastSql);
        assertArrayEquals(new Object[]{1L}, store.lastArgs);
        verifyNoInteractions(jdbc);
    }

    @Test
    void pagedQueriesPreserveCountLimitOffsetAndResponseFields() {
        String sql = "SELECT id FROM sample WHERE unit_id=?";
        store.result = List.of(Map.of("id", 4L));
        when(jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") matched", Long.class, 1L)).thenReturn(7L);
        assertEquals(Map.of("items", store.result, "total", 7L, "page", 2, "pageSize", 3),
                store.paged(Map.of("page", "2", "pageSize", "3"), sql, List.of(1L), "id"));
        assertEquals(sql + " ORDER BY id LIMIT ? OFFSET ?", store.lastSql);
        assertArrayEquals(new Object[]{1L, 3, 3L}, store.lastArgs);
    }

    @Test
    void invalidPageSizeIsRejectedBeforeQuerying() {
        assertEquals(400, assertThrows(ApiFailure.class, () -> store.paged(
                Map.of("pageSize", "101"), "SELECT id FROM sample", List.of(), "id")).status());
        verifyNoInteractions(jdbc);
    }
}
