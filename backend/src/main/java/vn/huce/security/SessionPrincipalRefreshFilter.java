package vn.huce.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.huce.Accounts.Actor;
import vn.huce.config.SecurityConfig;
import vn.huce.service.AccountService;

/** Refreshes an existing session against the current account status and scope. */
public final class SessionPrincipalRefreshFilter extends OncePerRequestFilter {
    private final HttpSessionSecurityContextRepository contexts;
    private final AccountService accounts;

    public SessionPrincipalRefreshFilter(HttpSessionSecurityContextRepository contexts, AccountService accounts) {
        this.contexts = contexts;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Actor prior) {
            Actor actor = accounts.active(prior.username());
            if (actor == null || actor.id() != prior.id()) {
                context.setAuthentication(null);
                contexts.saveContext(context, request, response);
            } else if (!actor.equals(prior)) {
                context.setAuthentication(SecurityConfig.authentication(actor));
                contexts.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
