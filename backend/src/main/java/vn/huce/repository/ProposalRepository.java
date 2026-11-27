package vn.huce.repository;

import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;

@Repository
public class ProposalRepository {
    private final SqlStore store;

    public ProposalRepository(SqlStore store) {
        this.store = store;
    }

    public long countRunItems(long runId) {
        return store.jdbc().queryForObject("SELECT count(*) FROM schedule_run_items WHERE run_id=?", Long.class, runId);
    }

    public Map<String, Object> insert(long actorId, Object semesterId, String action, Long runId, String parametersJson) {
        return store.need("""
                INSERT INTO ai_proposals(requested_by,semester_id,action,run_id,parameters)
                VALUES (?,?,?,?,?::jsonb) RETURNING id,to_char(expires_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS expires_at
                """, actorId, semesterId, action, runId, parametersJson);
    }

    public Map<String, Object> lock(long proposalId) {
        return store.need("""
                SELECT *,parameters::text AS params_json,result::text AS result_json,
                    expires_at<=clock_timestamp() AS expired FROM ai_proposals WHERE id=? FOR UPDATE
                """, proposalId);
    }

    public boolean hasDifferentConversationScope(long proposalId, Actor actor) {
        return store.exists("""
                SELECT 1 FROM chat_exchanges e JOIN chat_conversations c ON c.id=e.conversation_id
                WHERE e.proposal_id=? AND (c.user_id<>? OR c.role<>?
                    OR c.staff_id IS DISTINCT FROM ?::bigint OR c.unit_id IS DISTINCT FROM ?::bigint)
                """, proposalId, actor.id(), actor.role(), actor.staffId(), actor.unitId());
    }

    public void confirm(long proposalId, String resultJson) {
        store.jdbc().update("UPDATE ai_proposals SET result=?::jsonb,confirmed_at=now() WHERE id=?", resultJson, proposalId);
    }

    public void cancel(long proposalId) {
        store.jdbc().update("UPDATE ai_proposals SET cancelled_at=COALESCE(cancelled_at,now()) WHERE id=?", proposalId);
    }
}
