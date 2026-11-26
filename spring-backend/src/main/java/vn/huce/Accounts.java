package vn.huce;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class Accounts {
    record Actor(long id, String username, String role, Long staffId, Long unitId) implements Serializable {}
    private record Account(Actor actor, String hash, String status) {}
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder bcrypt;
    private final String dummy;
    Accounts(JdbcTemplate db, BCryptPasswordEncoder bcrypt) {
        this.db=db; this.bcrypt=bcrypt; this.dummy=bcrypt.encode("invalid-credentials");
    }
    private Account lookup(String username) {
        if (username==null || username.length()>200) return null;
        List<Account> result=db.query("""
          SELECT u.id,u.username,u.password_hash,u.status,r.code AS role,s.id AS staff_id,s.unit_id
          FROM users u JOIN roles r ON r.role_id=u.role_id
          LEFT JOIN staff_profiles s ON s.user_id=u.id AND s.status='ACTIVE'
          WHERE u.username=?
          """, (rs,n)->new Account(new Actor(rs.getLong("id"),rs.getString("username"),rs.getString("role"),
                rs.getObject("staff_id",Long.class),rs.getObject("unit_id",Long.class)),rs.getString("password_hash"),rs.getString("status")),username);
        return result.isEmpty()?null:result.getFirst();
    }
    Actor login(String username,String password,String expectedRole) {
        var account=lookup(username);
        String hash=account==null?dummy:account.hash();
        boolean matched=password!=null && password.getBytes(StandardCharsets.UTF_8).length<=72 && bcrypt.matches(password,hash);
        if (!matched || account==null || !"ACTIVE".equals(account.status()) ||
            (expectedRole!=null && !expectedRole.equals(account.actor().role())))
            throw new ApiFailure(401,"INVALID_CREDENTIALS","Tên đăng nhập hoặc mật khẩu không đúng.");
        return account.actor();
    }
    Actor active(String username) {
        var account=lookup(username);
        return account!=null && "ACTIVE".equals(account.status())?account.actor():null;
    }
}
