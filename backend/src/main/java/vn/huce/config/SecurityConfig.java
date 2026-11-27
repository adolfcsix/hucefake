package vn.huce.config;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import vn.huce.Accounts.Actor;
import vn.huce.security.SessionPrincipalRefreshFilter;
import vn.huce.service.AccountService;

@Configuration
public class SecurityConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpSessionSecurityContextRepository contexts() {
        return new HttpSessionSecurityContextRepository();
    }

    public static UsernamePasswordAuthenticationToken authentication(Actor actor) {
        return UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository contexts,
                                 AccountService accounts) throws Exception {
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-Token");
        var handler = new CsrfTokenRequestAttributeHandler();
        var refresh = new SessionPrincipalRefreshFilter(contexts, accounts);

        return http
                .securityContext(config -> config.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
                .addFilterAfter(refresh, SecurityContextHolderFilter.class)
                .authorizeHttpRequests(config -> config.requestMatchers(
                        "/", "/index.html", "/assets/**", "/favicon.ico", "/health",
                        "/api/csrf", "/api/auth/mode", "/api/auth/login", "/api/auth/demo-login")
                        .permitAll().anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, error) ->
                                write(response, 401, "UNAUTHORIZED", "Vui lòng đăng nhập."))
                        .accessDeniedHandler((request, response, error) ->
                                write(response, 403, error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN",
                                        "Yêu cầu không có quyền hoặc phiên biểu mẫu đã hết hạn.")))
                .logout(config -> config.logoutUrl("/api/auth/logout")
                        .deleteCookies("HUCE_SPRING_SID", "SESSION")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .build();
    }

    private static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":{}}");
    }
}
