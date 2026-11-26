package vn.huce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HuceApplication {
    public static void main(String[] args) {
        if(args.length>0&&"setup".equals(args[0])){System.exit(SetupCommand.run(args));return;}
        if(args.length==1&&"healthcheck".equals(args[0])){System.exit(HealthProbe.check());return;}
        SpringApplication.run(HuceApplication.class, args);
    }
}
