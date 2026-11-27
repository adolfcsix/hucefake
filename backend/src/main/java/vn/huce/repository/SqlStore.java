package vn.huce.repository;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.huce.exception.ApiFailure;
import vn.huce.validation.Inputs;

/** Transitional SQL adapter for existing query projections. New domain repositories
 * should own their queries and return typed records instead of exposing SQL to services.
 */
@Repository
public class SqlStore {
    private final JdbcTemplate jdbc;
    public SqlStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNumber) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            var metadata = rs.getMetaData();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                Object value = rs.getObject(column);
                if (value instanceof java.sql.Array array) {
                    try {
                        value = array.getArray();
                    } finally {
                        array.free();
                    }
                } else if (value instanceof java.sql.Date date) {
                    value = date.toLocalDate().toString();
                } else if (value instanceof Timestamp timestamp) {
                    value = timestamp.toLocalDateTime().toString();
                } else if (value instanceof Time time) {
                    value = time.toLocalTime().toString();
                }
                row.put(metadata.getColumnLabel(column), value);
            }
            return row;
        }, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = rows(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Map<String, Object> need(String sql, Object... args) {
        Map<String, Object> row = one(sql, args);
        if (row == null) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return row;
    }

    public boolean exists(String sql, Object... args) {
        return one(sql, args) != null;
    }

    public Object paged(Map<String, String> query, String sql, List<Object> args, String order) {
        if (!query.containsKey("page") && !query.containsKey("pageSize")) {
            return rows(sql + " ORDER BY " + order, args.toArray());
        }
        int page = (int) Inputs.number(query.getOrDefault("page", "1"), 1, 100000);
        int size = (int) Inputs.number(query.getOrDefault("pageSize", "25"), 1, 100);
        long total = jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") matched",
                Long.class, args.toArray());
        List<Object> bound = new ArrayList<>(args);
        bound.add(size);
        bound.add((long) (page - 1) * size);
        return Map.of("items", rows(sql + " ORDER BY " + order + " LIMIT ? OFFSET ?", bound.toArray()),
                "total", total, "page", page, "pageSize", size);
    }
}
