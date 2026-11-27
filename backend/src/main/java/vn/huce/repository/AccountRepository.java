package vn.huce.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.huce.Accounts.Actor;

@Repository
public class AccountRepository {
    private final JdbcTemplate jdbc;

    public AccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AccountRecord(Actor actor, String passwordHash, String status) {
    }

    public Optional<AccountRecord> findByUsername(String username) {
        List<AccountRecord> accounts = jdbc.query("""
                SELECT u.id, u.username, u.password_hash, u.status, r.code AS role,
                       s.id AS staff_id, s.unit_id
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                LEFT JOIN staff_profiles s ON s.user_id = u.id AND s.status = 'ACTIVE'
                WHERE u.username = ?
                """, (rs, rowNumber) -> new AccountRecord(
                new Actor(rs.getLong("id"), rs.getString("username"), rs.getString("role"),
                        rs.getObject("staff_id", Long.class), rs.getObject("unit_id", Long.class)),
                rs.getString("password_hash"), rs.getString("status")), username);
        return accounts.stream().findFirst();
    }
}
