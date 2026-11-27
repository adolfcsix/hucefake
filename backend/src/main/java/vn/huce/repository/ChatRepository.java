package vn.huce.repository;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;

@Repository
public class ChatRepository {
    private static final String OWNER = "user_id=? AND role=? AND staff_id IS NOT DISTINCT FROM ?::bigint AND unit_id IS NOT DISTINCT FROM ?::bigint";
    private final SqlStore store;

    public ChatRepository(SqlStore store) {
        this.store = store;
    }

    private Object[] scope(Actor actor, Object extra) {
        return new Object[]{actor.id(), actor.role(), actor.staffId(), actor.unitId(), extra};
    }

    public List<Map<String, Object>> conversations(Actor actor, String search) {
        return store.rows("""
                SELECT id,title,to_char(updated_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS updated_at
                FROM chat_conversations WHERE
                """ + OWNER + " AND strpos(lower(title),lower(?))>0 ORDER BY updated_at DESC,id DESC LIMIT 20",
                scope(actor, search));
    }

    public Map<String, Object> findOwned(Actor actor, long conversationId) {
        return store.need("SELECT id,title FROM chat_conversations WHERE " + OWNER + " AND id=?",
                scope(actor, conversationId));
    }

    public List<Map<String, Object>> exchanges(long conversationId) {
        return store.rows("""
                SELECT e.id,e.question,e.reply::text AS reply,to_char(e.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
                    p.result::text AS result,p.cancelled_at,p.expires_at<=now() AS expired
                FROM chat_exchanges e LEFT JOIN ai_proposals p ON e.proposal_id=p.id
                WHERE e.conversation_id=? ORDER BY e.id DESC LIMIT 60
                """, conversationId);
    }

    public Map<String, Object> previousIntent(long conversationId) {
        return store.one("SELECT reply->>'intent' AS intent FROM chat_exchanges WHERE conversation_id=? ORDER BY id DESC LIMIT 1",
                conversationId);
    }

    public Map<String, Object> semester(long semesterId) {
        return store.need("SELECT id,code,start_date,end_date FROM semesters WHERE id=?", semesterId);
    }

    public long createConversation(Actor actor, String title) {
        return ((Number) store.need("""
                INSERT INTO chat_conversations(user_id,role,staff_id,unit_id,title) VALUES (?,?,?,?,?) RETURNING id
                """, actor.id(), actor.role(), actor.staffId(), actor.unitId(), title).get("id")).longValue();
    }

    public Map<String, Object> insertExchange(long conversationId, String question, String replyJson, Object proposalId) {
        return store.need("""
                INSERT INTO chat_exchanges(conversation_id,question,reply,proposal_id) VALUES (?,?,?::jsonb,?)
                RETURNING id,to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at
                """, conversationId, question, replyJson, proposalId);
    }

    public void touch(long conversationId) {
        store.jdbc().update("UPDATE chat_conversations SET updated_at=now() WHERE id=?", conversationId);
    }
}
