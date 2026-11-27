package vn.huce.config;

import vn.huce.Accounts;
import vn.huce.service.AccountService;
import vn.huce.security.SessionPrincipalRefreshFilter;

import java.util.List;
import java.io.IOException;
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

@Configuration
public class SecurityConfig {
    @Bean BCryptPasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean HttpSessionSecurityContextRepository contexts(){return new HttpSessionSecurityContextRepository();}
    public static UsernamePasswordAuthenticationToken authentication(Accounts.Actor actor) {
        return UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of());
    }
    @Bean SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository contexts, AccountService accounts) throws Exception {
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-Token");
        var handler = new CsrfTokenRequestAttributeHandler();
        var refresh = new SessionPrincipalRefreshFilter(contexts, accounts);
        return http.securityContext(c->c.securityContextRepository(contexts))
          .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(handler))
          .addFilterAfter(refresh,SecurityContextHolderFilter.class)
          .authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/assets/**","/favicon.ico","/health","/api/csrf","/api/auth/mode","/api/auth/login","/api/auth/demo-login").permitAll().anyRequest().authenticated())
          .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->write(res,401,"UNAUTHORIZED","Vui lòng đăng nhập."))
             .accessDeniedHandler((req,res,x)->write(res,403,x instanceof CsrfException?"CSRF_INVALID":"FORBIDDEN","Yêu cầu không có quyền hoặc phiên biểu mẫu đã hết hạn.")))
          .logout(l->l.logoutUrl("/api/auth/logout").deleteCookies("HUCE_SPRING_SID","SESSION").logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
          .build();
    }
    private static void write(HttpServletResponse res,int status,String code,String message)throws IOException{
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\",\"details\":{}}");
    }
}
