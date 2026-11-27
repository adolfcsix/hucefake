package vn.huce.repository;

import vn.huce.model.ChatSemester;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.ChatConversationSnapshot;
import vn.huce.model.ChatExchangeReceipt;
import vn.huce.model.ChatExchangeSnapshot;

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

    public ChatConversationSnapshot findOwned(Actor actor, long conversationId) {
        return require(store.jdbc().query("SELECT id,title FROM chat_conversations WHERE " + OWNER + " AND id=?",
                (rs, row) -> new ChatConversationSnapshot(rs.getLong("id"), rs.getString("title")),
                scope(actor, conversationId)));
    }

    private static <T> T require(List<T> rows) {
        if (rows.isEmpty()) {
            throw new ApiFailure(404, "NOT_FOUND", "Không tìm thấy dữ liệu.");
        }
        return rows.getFirst();
    }

    public List<ChatExchangeSnapshot> exchanges(long conversationId) {
        return store.jdbc().query("""
                SELECT e.id,e.question,e.reply::text AS reply,to_char(e.created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at,
                    p.result::text AS result,p.cancelled_at IS NOT NULL AS cancelled,p.expires_at<=now() AS expired
                FROM chat_exchanges e LEFT JOIN ai_proposals p ON e.proposal_id=p.id
                WHERE e.conversation_id=? ORDER BY e.id DESC LIMIT 60
                """, (rs, row) -> new ChatExchangeSnapshot(rs.getLong("id"), rs.getString("question"),
                rs.getString("created_at"), rs.getString("reply"), rs.getString("result"),
                rs.getBoolean("cancelled"), rs.getBoolean("expired")), conversationId);
    }

    public String previousIntent(long conversationId) {
        List<String> rows = store.jdbc().queryForList(
                "SELECT reply->>'intent' AS intent FROM chat_exchanges WHERE conversation_id=? ORDER BY id DESC LIMIT 1",
                String.class, conversationId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public ChatSemester semester(long semesterId) {
        return require(store.jdbc().query("SELECT id,code,start_date,end_date FROM semesters WHERE id=?",
                (rs, row) -> new ChatSemester(rs.getLong("id"), rs.getString("code"),
                        rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)),
                semesterId));
    }

    public long createConversation(Actor actor, String title) {
        return require(store.jdbc().query("""
                INSERT INTO chat_conversations(user_id,role,staff_id,unit_id,title) VALUES (?,?,?,?,?) RETURNING id
                """, (rs, row) -> rs.getLong("id"), actor.id(), actor.role(), actor.staffId(), actor.unitId(), title));
    }

    public ChatExchangeReceipt insertExchange(long conversationId, String question, String replyJson, Object proposalId) {
        return require(store.jdbc().query("""
                INSERT INTO chat_exchanges(conversation_id,question,reply,proposal_id) VALUES (?,?,?::jsonb,?)
                RETURNING id,to_char(created_at AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') AS created_at
                """, (rs, row) -> new ChatExchangeReceipt(rs.getLong("id"), rs.getString("created_at")),
                conversationId, question, replyJson, proposalId));
    }

    public void touch(long conversationId) {
        store.jdbc().update("UPDATE chat_conversations SET updated_at=now() WHERE id=?", conversationId);
    }
}
