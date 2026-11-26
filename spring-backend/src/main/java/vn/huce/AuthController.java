package vn.huce;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class AuthController {
    record Credentials(String username,String password){}
    record DemoRole(String role){}
    private static final Map<String,String> DEMO_USERS=Map.of("STAFF","daotao","MANAGER","quanly","LECTURER","giangvien","STUDENT","sinhvien");
    private final Accounts accounts;
    private final HttpSessionSecurityContextRepository contexts;
    private final boolean demo;
    AuthController(Accounts accounts,HttpSessionSecurityContextRepository contexts,@Value("${huce.demo}") boolean demo){
        this.accounts=accounts;this.contexts=contexts;this.demo=demo;
    }
    @GetMapping("/auth/mode") ResponseEntity<?> mode(){return noStore(Map.of("demo",demo));}
    @GetMapping("/csrf") ResponseEntity<?> csrf(CsrfToken token){return noStore(Map.of("header",token.getHeaderName(),"token",token.getToken()));}
    @PostMapping("/auth/login") ResponseEntity<?> login(@RequestBody Credentials body,HttpServletRequest req,HttpServletResponse res){
        return establish(accounts.login(body.username(),body.password(),null),req,res);
    }
    @PostMapping("/auth/demo-login") ResponseEntity<?> demo(@RequestBody DemoRole body,HttpServletRequest req,HttpServletResponse res){
        if(!demo)throw new ApiFailure(403,"DEMO_DISABLED","Chế độ dùng thử không được bật.");
        if(body.role()==null || !DEMO_USERS.containsKey(body.role()))throw new ApiFailure(400,"VALIDATION_ERROR","Vai trò dùng thử không hợp lệ.");
        return establish(accounts.login(DEMO_USERS.get(body.role()),"Fm05Demo@2026",body.role()),req,res);
    }
    @GetMapping("/me") ResponseEntity<?> me(Authentication auth){return noStore(auth.getPrincipal());}
    private ResponseEntity<?> establish(Accounts.Actor actor,HttpServletRequest req,HttpServletResponse res){
        req.getSession(); req.changeSessionId();
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityConfig.authentication(actor));
        SecurityContextHolder.setContext(context);contexts.saveContext(context,req,res);
        return noStore(actor);
    }
    private ResponseEntity<?> noStore(Object data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data);}
}
