package vn.huce.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Actor actor, String action, String entityType, long entityId) {
        jdbc.update("INSERT INTO audit_events(actor_id,action,entity_type,entity_id) VALUES (?,?,?,?)",
                actor.id(), action, entityType, entityId);
    }
}
