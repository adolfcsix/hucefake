package vn.huce;

import java.util.List;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
    @Bean BCryptPasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
    @Bean HttpSessionSecurityContextRepository contexts(){return new HttpSessionSecurityContextRepository();}
    static UsernamePasswordAuthenticationToken authentication(Accounts.Actor actor) {
        return UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of());
    }
    @Bean SecurityFilterChain security(HttpSecurity http, HttpSessionSecurityContextRepository contexts, Accounts accounts) throws Exception {
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-Token");
        var handler = new CsrfTokenRequestAttributeHandler();
        var refresh = new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
                  throws ServletException,IOException {
                var context=SecurityContextHolder.getContext();
                var auth=context.getAuthentication();
                if(auth!=null && auth.getPrincipal() instanceof Accounts.Actor prior){
                    var actor=accounts.active(prior.username());
                    if(actor==null || actor.id()!=prior.id()){
                        context.setAuthentication(null);
                        contexts.saveContext(context,req,res);
                    } else if(!actor.equals(prior)){
                        context.setAuthentication(authentication(actor));
                        contexts.saveContext(context,req,res);
                    }
                }
                chain.doFilter(req,res);
            }
        };
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
