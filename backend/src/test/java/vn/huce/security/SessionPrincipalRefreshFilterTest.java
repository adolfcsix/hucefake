package vn.huce.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import vn.huce.Accounts.Actor;
import vn.huce.config.SecurityConfig;
import vn.huce.service.AccountService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionPrincipalRefreshFilterTest {
    private final AccountService accounts = mock(AccountService.class);
    private final HttpSessionSecurityContextRepository contexts = mock(HttpSessionSecurityContextRepository.class);
    private final SessionPrincipalRefreshFilter filter = new SessionPrincipalRefreshFilter(contexts, accounts);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final FilterChain chain = mock(FilterChain.class);
    private final Actor prior = new Actor(1L, "staff", "STAFF", 2L, 3L);

    private void authenticate() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityConfig.authentication(prior));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestDoesNotLookupAccount() throws Exception {
        SecurityContextHolder.clearContext();
        filter.doFilter(request, response, chain);
        verifyNoInteractions(accounts, contexts);
        verify(chain).doFilter(request, response);
    }

    @Test
    void disabledAccountLosesAuthentication() throws Exception {
        authenticate();
        when(accounts.active("staff")).thenReturn(null);
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(contexts).saveContext(SecurityContextHolder.getContext(), request, response);
        verify(chain).doFilter(request, response);
    }

    @Test
    void replacedAccountWithSameUsernameCannotInheritSession() throws Exception {
        authenticate();
        when(accounts.active("staff")).thenReturn(new Actor(9L, "staff", "STAFF", 2L, 3L));
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(contexts).saveContext(SecurityContextHolder.getContext(), request, response);
    }

    @Test
    void changedScopeRefreshesPrincipal() throws Exception {
        authenticate();
        Actor changed = new Actor(1L, "staff", "STAFF", 2L, 4L);
        when(accounts.active("staff")).thenReturn(changed);
        filter.doFilter(request, response, chain);
        assertEquals(changed, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(contexts).saveContext(SecurityContextHolder.getContext(), request, response);
    }

    @Test
    void unchangedPrincipalDoesNotRewriteSession() throws Exception {
        authenticate();
        when(accounts.active("staff")).thenReturn(prior);
        filter.doFilter(request, response, chain);
        assertEquals(prior, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verifyNoInteractions(contexts);
        verify(chain).doFilter(request, response);
    }
}
