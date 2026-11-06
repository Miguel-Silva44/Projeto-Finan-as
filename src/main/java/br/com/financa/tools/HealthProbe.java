package br.com.financa.tools;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class HealthProbe {
  public static void main(String[] args) throws Exception {
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    var response =
        client.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/health/ready"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
    if (response.statusCode() != 200) System.exit(1);
  }
}
