package vn.huce;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {
    @Test void limitPerAddressAndReset(){
        var clock=new AtomicLong(1000);
        var limiter=new LoginThrottle(4,clock::get);
        for(int i=0;i<4;i++)limiter.check("client-a");
        var error=assertThrows(ApiFailure.class,()->limiter.check("client-a"));
        assertEquals(429,error.status);assertEquals("RATE_LIMIT",error.code);
        limiter.check("client-b");
        clock.addAndGet(59999);
        assertThrows(ApiFailure.class,()->limiter.check("client-a"));
        clock.incrementAndGet();
        limiter.check("client-a");
    }
    @Test void validatesConfiguration(){assertThrows(IllegalArgumentException.class,()->new LoginThrottle(0,System::currentTimeMillis));}
}
