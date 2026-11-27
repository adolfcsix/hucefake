package vn.huce.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.repository.AccountRepository;
import vn.huce.repository.AccountRepository.AccountRecord;

@Service
public class AccountService {
    private static final Set<String> STAFF_ROLES = Set.of("STAFF", "LECTURER");
    private final AccountRepository accounts;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AccountService(AccountRepository accounts, BCryptPasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("invalid-credentials");
    }

    public Actor login(String username, String password, String expectedRole) {
        AccountRecord account = lookup(username);
        String hash = account == null ? dummyHash : account.passwordHash();
        boolean matched = password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(password, hash);

        if (!matched || !usable(account)
                || (expectedRole != null && !expectedRole.equals(account.actor().role()))) {
            throw new ApiFailure(401, "INVALID_CREDENTIALS",
                    "Tên đăng nhập hoặc mật khẩu không đúng.");
        }
        return account.actor();
    }

    public Actor active(String username) {
        AccountRecord account = lookup(username);
        return usable(account) ? account.actor() : null;
    }

    private AccountRecord lookup(String username) {
        if (username == null || username.length() > 200) {
            return null;
        }
        return accounts.findByUsername(username).orElse(null);
    }

    private boolean usable(AccountRecord account) {
        if (account == null || !"ACTIVE".equals(account.status())) {
            return false;
        }
        Actor actor = account.actor();
        return !STAFF_ROLES.contains(actor.role())
                || (actor.staffId() != null && actor.unitId() != null);
    }
}
