package vn.huce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.huce.Accounts.Actor;
import vn.huce.config.SecurityConfig;
import vn.huce.dto.AuthModeResponse;
import vn.huce.dto.CsrfResponse;
import vn.huce.dto.DemoLoginRequest;
import vn.huce.dto.LoginRequest;
import vn.huce.exception.ApiFailure;
import vn.huce.security.LoginThrottle;
import vn.huce.service.AccountService;

@RestController
@RequestMapping("/api")
public class AuthController {
    private static final Map<String, String> DEMO_USERS = Map.of(
            "STAFF", "daotao", "MANAGER", "quanly", "LECTURER", "giangvien", "STUDENT", "sinhvien");
    private final AccountService accounts;
    private final HttpSessionSecurityContextRepository contexts;
    private final boolean demo;
    private final LoginThrottle throttle;

    AuthController(AccountService accounts, HttpSessionSecurityContextRepository contexts,
                   LoginThrottle throttle, @Value("${huce.demo}") boolean demo) {
        this.accounts = accounts;
        this.contexts = contexts;
        this.demo = demo;
        this.throttle = throttle;
    }

    @GetMapping("/auth/mode")
    public ResponseEntity<AuthModeResponse> mode() {
        return noStore(new AuthModeResponse(demo));
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return noStore(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Actor> login(@RequestBody LoginRequest body,
                                       HttpServletRequest request, HttpServletResponse response) {
        throttle.check(request.getRemoteAddr());
        Actor actor = accounts.login(body.username(), body.password(), null);
        return establish(actor, request, response);
    }

    @PostMapping("/auth/demo-login")
    public ResponseEntity<Actor> demo(@RequestBody DemoLoginRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        if (!demo) {
            throw new ApiFailure(403, "DEMO_DISABLED", "Chế độ dùng thử không được bật.");
        }
        if (body.role() == null || !DEMO_USERS.containsKey(body.role())) {
            throw new ApiFailure(400, "VALIDATION_ERROR", "Vai trò dùng thử không hợp lệ.");
        }
        throttle.check(request.getRemoteAddr());
        Actor actor = accounts.login(DEMO_USERS.get(body.role()), "Fm05Demo@2026", body.role());
        return establish(actor, request, response);
    }

    @GetMapping("/me")
    public ResponseEntity<Actor> me(Authentication authentication) {
        return noStore((Actor) authentication.getPrincipal());
    }

    private ResponseEntity<Actor> establish(Actor actor, HttpServletRequest request, HttpServletResponse response) {
        request.getSession();
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityConfig.authentication(actor));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return noStore(actor);
    }

    private <T> ResponseEntity<T> noStore(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);
    }
}
