package vn.huce;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LoginThrottle {
    private static final long WINDOW_MS=60_000;
    private static final int MAX_CLIENTS=4096;
    private final Map<String,Bucket> clients=new HashMap<>();
    private final int limit;
    private final LongSupplier clock;
    private record Bucket(long expires,int attempts){}
    @org.springframework.beans.factory.annotation.Autowired
    LoginThrottle(@Value("${huce.login-attempts-per-minute:40}") int limit){this(limit,System::currentTimeMillis);}
    LoginThrottle(int limit,LongSupplier clock){
        if(limit<4||limit>1000)throw new IllegalArgumentException("Invalid login limit");
        this.limit=limit;this.clock=clock;
    }
    synchronized void check(String address){
        long now=clock.getAsLong();
        clients.entrySet().removeIf(e->e.getValue().expires<=now);
        String key=address==null?"unknown":address;
        var bucket=clients.get(key);
        if(bucket==null){
            if(clients.size()>=MAX_CLIENTS)throw limited(60);
            clients.put(key,new Bucket(now+WINDOW_MS,1));return;
        }
        if(bucket.attempts>=limit)throw limited(Math.max(1,(bucket.expires-now+999)/1000));
        clients.put(key,new Bucket(bucket.expires,bucket.attempts+1));
    }
    private ApiFailure limited(long seconds){return new ApiFailure(429,"RATE_LIMIT","Có quá nhiều lần đăng nhập. Vui lòng thử lại sau.",Map.of("retryAfterSeconds",seconds));}
}
