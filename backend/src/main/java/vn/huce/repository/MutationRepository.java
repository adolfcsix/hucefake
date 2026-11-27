package vn.huce.repository;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.huce.model.IdempotencyRecord;

@Repository
public class MutationRepository {
    private final JdbcTemplate jdbc;

    public MutationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Must be acquired within the write or worker transaction. */
    public void lock() {
        jdbc.execute("SELECT pg_advisory_xact_lock(5060505)");
    }

    public void deleteExpired() {
        jdbc.update("DELETE FROM fm05_idempotency_keys WHERE expires_at<=now()");
    }

    public IdempotencyRecord find(long actorId, String key) {
        List<IdempotencyRecord> rows = jdbc.query("""
                SELECT fingerprint,response::text AS response
                FROM fm05_idempotency_keys WHERE actor_id=? AND key=?
                """, (result, rowNumber) -> new IdempotencyRecord(
                result.getString("fingerprint"), result.getString("response")), actorId, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void save(long actorId, String key, String fingerprint, String responseJson) {
        jdbc.update("""
                INSERT INTO fm05_idempotency_keys(actor_id,key,fingerprint,response)
                VALUES (?,?,?,?::jsonb)
                """, actorId, key, fingerprint, responseJson);
    }
}
