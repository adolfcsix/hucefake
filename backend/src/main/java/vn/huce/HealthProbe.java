package vn.huce;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class HealthProbe {
    private HealthProbe(){}
    static int check(){
        try{
            int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health")).timeout(Duration.ofSeconds(2)).GET().build();
            var result=client.send(request,HttpResponse.BodyHandlers.discarding());
            return result.statusCode()==200?0:1;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return 1;}
        catch(Exception e){return 1;}
    }
}
