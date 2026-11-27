package vn.huce;

import java.io.Serializable;

/**
 * Session principal retained at its original binary name for compatibility
 * with existing Spring Session JDBC records. Authentication lives in AccountService.
 */
public final class Accounts {
    private Accounts() {
    }

    public record Actor(long id, String username, String role, Long staffId, Long unitId)
            implements Serializable {
    }
}
