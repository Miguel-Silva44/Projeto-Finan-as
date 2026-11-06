package br.com.financa;

import static org.assertj.core.api.Assertions.*;

import br.com.financa.service.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@EnabledIfSystemProperty(named = "finance.postgres-performance", matches = "true")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.jobs-enabled=false", "app.security.max-sessions=20"})
@ActiveProfiles("postgres")
class PostgresLoadIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    FinancePostgresIT.database(registry);
  }

  @Value("${local.server.port}")
  int port;

  @Autowired IdentityService identity;
  @Autowired FinanceService finance;
  @Autowired JdbcTemplate jdbc;
  @Autowired ReportService reports;

  @Test
  void dashboardOverHttpWith100000EntriesAnd20AuthenticatedSessions() throws Exception {
    String email = UUID.randomUUID() + "@example.test", password = "Load test password secure 123!";
    identity.register(Map.of("name", "HTTP Load", "email", email, "password", password));
    String user = identity.id(email);
    jdbc.update("UPDATE app_user SET verified=TRUE WHERE id=?", user);
    LocalDate today = finance.today(user);
    String account =
        finance.saveAccount(
            user,
            Map.of(
                "key",
                UUID.randomUUID().toString(),
                "name",
                "Load",
                "kind",
                "CORRENTE",
                "opening",
                "2000",
                "reference_date",
                today.toString()));
    String category =
        finance.list("category", user).stream()
            .filter(row -> row.get("kind").equals("DESPESA"))
            .findFirst()
            .orElseThrow()
            .get("id")
            .toString();
    jdbc.update(
        "INSERT INTO entry(id,user_id,account_id,category_id,description,kind,amount,competence,due_date,realized_date,status) SELECT CAST(gen_random_uuid() AS VARCHAR(36)),?,?,?,'HTTP Load','DESPESA',1,?,?,?,'REALIZADO' FROM generate_series(1,100000)",
        user,
        account,
        category,
        today,
        today,
        today);
    assertThat(reports.dashboard(user, YearMonth.from(today), today).get("expense"))
        .isEqualTo(100000L);
    URI login = URI.create("http://127.0.0.1:" + port + "/auth/login"),
        dashboard = URI.create("http://127.0.0.1:" + port + "/dashboard");
    List<HttpClient> clients = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      var client =
          HttpClient.newBuilder()
              .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
      String html =
          client
              .send(
                  HttpRequest.newBuilder(login).GET().build(), HttpResponse.BodyHandlers.ofString())
              .body();
      var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
      assertThat(token.find()).isTrue();
      String body =
          "email="
              + URLEncoder.encode(email, StandardCharsets.UTF_8)
              + "&password="
              + URLEncoder.encode(password, StandardCharsets.UTF_8)
              + "&_csrf="
              + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8);
      var response =
          client.send(
              HttpRequest.newBuilder(login)
                  .header("Content-Type", "application/x-www-form-urlencoded")
                  .POST(HttpRequest.BodyPublishers.ofString(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.uri().getPath()).isEqualTo("/dashboard");
      clients.add(client);
    }
    for (int i = 0; i < 3; i++)
      clients
          .getFirst()
          .send(
              HttpRequest.newBuilder(dashboard).GET().build(),
              HttpResponse.BodyHandlers.discarding());
    List<Long> times = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(20)) {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Long>> tasks = new ArrayList<>();
      for (HttpClient client : clients)
        tasks.add(
            executor.submit(
                () -> {
                  start.await();
                  long begin = System.nanoTime();
                  var response =
                      client.send(
                          HttpRequest.newBuilder(dashboard)
                              .timeout(Duration.ofSeconds(30))
                              .GET()
                              .build(),
                          HttpResponse.BodyHandlers.ofString());
                  assertThat(response.statusCode()).isEqualTo(200);
                  assertThat(response.uri().getPath()).isEqualTo("/dashboard");
                  assertThat(response.body()).contains("Visão geral");
                  return (System.nanoTime() - begin) / 1000000;
                }));
      start.countDown();
      for (var task : tasks) times.add(task.get(40, TimeUnit.SECONDS));
    }
    times.sort(Long::compareTo);
    long p95 = times.get(18);
    Files.createDirectories(Path.of("target"));
    Files.writeString(
        Path.of("target", "postgres-http-performance.txt"),
        "PostgreSQL 18.6, 100000 lançamentos, 20 sessões HTTP reais, P95="
            + p95
            + "ms; amostras="
            + times
            + "\nMeta=1000ms; atendida="
            + (p95 <= 1000));
    assertThat(p95).as("RNF02: dashboard HTTP PostgreSQL").isLessThanOrEqualTo(1000L);
    for (HttpClient client : clients) client.close();
  }
}
