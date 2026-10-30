package br.com.financa;

import static org.assertj.core.api.Assertions.*;

import br.com.financa.service.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Opt-in workload: mvn -Dtest=PerformanceTest test. */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:performance;MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.jobs-enabled=false"
    })
class PerformanceTest {
  @Autowired IdentityService identity;
  @Autowired FinanceService finance;
  @Autowired ReportService reports;
  @Autowired JdbcTemplate jdbc;

  @Test
  @org.junit.jupiter.api.condition.EnabledIfSystemProperty(
      named = "finance.performance",
      matches = "true")
  void dashboardWith100000EntriesAnd20ConcurrentReaders() throws Exception {
    String email = UUID.randomUUID() + "@example.test";
    identity.register(
        Map.of("name", "Carga", "email", email, "password", "Senha carga segura 123!"));
    String user = identity.id(email);
    LocalDate today = finance.today(user);
    String account =
        finance.saveAccount(
            user,
            Map.of(
                "key",
                UUID.randomUUID().toString(),
                "name",
                "Carga",
                "kind",
                "CORRENTE",
                "opening",
                "0",
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
        "INSERT INTO entry(id,user_id,account_id,category_id,description,kind,amount,competence,due_date,realized_date,status) SELECT RANDOM_UUID(),?,?,?,'Carga','DESPESA',1,?,?,?,'REALIZADO' FROM SYSTEM_RANGE(1,100000)",
        user,
        account,
        category,
        today,
        today,
        today);
    for (int i = 0; i < 3; i++) reports.dashboard(user, YearMonth.from(today), today);
    List<Long> times = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(20)) {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Long>> tasks = new ArrayList<>();
      for (int i = 0; i < 20; i++)
        tasks.add(
            executor.submit(
                () -> {
                  start.await();
                  long begin = System.nanoTime();
                  var result = reports.dashboard(user, YearMonth.from(today), today);
                  assertThat(result.get("expense")).isEqualTo(100000L);
                  return (System.nanoTime() - begin) / 1000000;
                }));
      start.countDown();
      for (var task : tasks) times.add(task.get(60, TimeUnit.SECONDS));
    }
    times.sort(Long::compareTo);
    long p95 = times.get(18);
    Files.createDirectories(Path.of("target"));
    Files.writeString(
        Path.of("target", "performance.txt"),
        "100000 lançamentos; 20 consultas concorrentes; p95="
            + p95
            + "ms; amostras="
            + times
            + "\nJVM="
            + System.getProperty("java.version")
            + "; SO="
            + System.getProperty("os.name")
            + "; processadores="
            + Runtime.getRuntime().availableProcessors());
    assertThat(p95).as("Meta RNF02 de até 1 segundo no ambiente local").isLessThanOrEqualTo(1000);
  }
}
