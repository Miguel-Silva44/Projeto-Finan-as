package br.com.financa;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.financa.model.*;
import br.com.financa.repository.*;
import br.com.financa.service.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:integration;MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.jobs-enabled=false"
    })
@AutoConfigureMockMvc
class FinanceIntegrationTest {
  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired FinanceService finance;
  @Autowired IdentityService identity;
  @Autowired CardService cards;
  @Autowired PlanningService planning;
  @Autowired ReportService reports;
  @Autowired FinanceRepository repository;
  @Autowired ExportService exports;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  String user, email, expense, income, account;

  Map<String, String> form(String... pairs) {
    var result = new LinkedHashMap<String, String>();
    result.put("key", UUID.randomUUID().toString());
    for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
    return result;
  }

  @BeforeEach
  void setup() {
    email = UUID.randomUUID() + "@example.test";
    identity.register(
        Map.of("name", "Pessoa Teste", "email", email, "password", "Senha de teste segura 123!"));
    user = identity.id(email);
    jdbc.update("UPDATE app_user SET verified=TRUE WHERE id=?", user);
    expense =
        finance.list("category", user).stream()
            .filter(row -> row.get("kind").equals("DESPESA"))
            .findFirst()
            .orElseThrow()
            .get("id")
            .toString();
    income =
        finance.list("category", user).stream()
            .filter(row -> row.get("kind").equals("RECEITA"))
            .findFirst()
            .orElseThrow()
            .get("id")
            .toString();
    account = createAccount("Principal", "1000");
  }

  String createAccount(String name, String opening) {
    return finance.saveAccount(
        user,
        form("name", name, "kind", "CORRENTE", "opening", opening, "reference_date", "2026-01-01"));
  }

  Map<String, String> entry(String kind, String amount, String status) {
    return form(
        "description",
        "Teste financeiro",
        "kind",
        kind,
        "amount",
        amount,
        "account_id",
        account,
        "category_id",
        kind.equals("RECEITA") ? income : expense,
        "competence",
        "2026-10-01",
        "due_date",
        "2026-10-28",
        "status",
        status,
        "realized_date",
        "2026-10-28");
  }

  String createCard() {
    return cards.save(
        user,
        form("name", "Cartão Teste", "credit_limit", "5000", "closing_day", "20", "due_day", "10"));
  }

  Map<String, String> purchase(String card) {
    return form(
        "card_id",
        card,
        "category_id",
        expense,
        "description",
        "Compra de teste",
        "purchase_date",
        "2026-10-19",
        "amount",
        "100",
        "installments",
        "3");
  }

  @Test
  void balanceProjectionAndBudgetAreConsistent() {
    finance.saveEntry(user, entry("RECEITA", "3000", "REALIZADO"));
    finance.saveEntry(user, entry("DESPESA", "200", "REALIZADO"));
    finance.saveEntry(user, entry("DESPESA", "100", "PENDENTE"));
    var summary = reports.dashboard(user, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 1));
    assertThat(summary.get("balance")).isEqualTo(380000L);
    assertThat(summary.get("projected")).isEqualTo(370000L);
    planning.budget(user, form("category_id", expense, "month", "2026-10", "amount", "500"));
    var line =
        reports.breakdown(user, YearMonth.of(2026, 10)).stream()
            .filter(row -> row.get("id").equals(expense))
            .findFirst()
            .orElseThrow();
    assertThat(line.get("available")).isEqualTo(20000L);
    assertThat(line.get("percent")).isEqualTo(60L);
  }

  @Test
  void transfersAreAtomicAndExcludedFromResult() {
    String destination = createAccount("Reserva", "0");
    var request =
        form(
            "source_id",
            account,
            "destination_id",
            destination,
            "amount",
            "500",
            "effective_date",
            "2026-10-28");
    finance.transfer(user, request);
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(50000);
    assertThat(finance.balance(user, destination, finance.today(user))).isEqualTo(50000);
    assertThat(reports.dashboard(user, YearMonth.of(2026, 10), finance.today(user)).get("result"))
        .isEqualTo(0L);
    assertThat(finance.transfer(user, request))
        .isEqualTo(finance.list("transfer", user).getFirst().get("id"));
    request.put("amount", "501");
    assertThatThrownBy(() -> finance.transfer(user, request)).isInstanceOf(DomainException.class);
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(50000);
  }

  @Test
  void invalidDestinationLeavesNoTransfer() {
    assertThatThrownBy(
            () ->
                finance.transfer(
                    user,
                    form(
                        "source_id",
                        account,
                        "destination_id",
                        UUID.randomUUID().toString(),
                        "amount",
                        "500",
                        "effective_date",
                        "2026-10-28")))
        .isInstanceOf(DomainException.class);
    assertThat(finance.list("transfer", user)).isEmpty();
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(100000);
  }

  @Test
  void entryTransitionsAndStaleVersion() {
    String id = finance.saveEntry(user, entry("DESPESA", "200", "PENDENTE"));
    finance.entryAction(user, id, "realize", form("version", "0", "effective_date", "2026-10-28"));
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(80000);
    assertThatThrownBy(() -> finance.entryAction(user, id, "undo", form("version", "0")))
        .isInstanceOf(DomainException.class);
    finance.entryAction(user, id, "undo", form("version", "1"));
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(100000);
    finance.entryAction(user, id, "cancel", form("version", "2", "reason", "Correção"));
    assertThatThrownBy(
            () ->
                finance.entryAction(
                    user, id, "realize", form("version", "3", "effective_date", "2026-10-28")))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void cardsDoNotDuplicateExpenseOnPayment() {
    String card = createCard();
    cards.purchase(user, purchase(card));
    var invoice =
        reports.invoices(user).stream()
            .filter(row -> row.get("month").equals("2026-10"))
            .findFirst()
            .orElseThrow();
    assertThat(invoice.get("total")).isEqualTo(3334L);
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(100000);
    cards.closeDue(user);
    invoice = finance.get("invoice", user, invoice.get("id").toString());
    cards.invoiceAction(
        user,
        invoice.get("id").toString(),
        "pay",
        form(
            "version",
            invoice.get("version").toString(),
            "account_id",
            account,
            "effective_date",
            "2026-10-28"));
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(96666);
    assertThat(reports.dashboard(user, YearMonth.of(2026, 10), finance.today(user)).get("expense"))
        .isEqualTo(3334L);
    assertThatThrownBy(
            () ->
                cards.cancelPurchase(
                    user,
                    finance.list("purchase", user).getFirst().get("id").toString(),
                    form("version", "0", "reason", "Correção")))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void concurrentPaymentHasOneEffect() throws Exception {
    String card = createCard();
    var request = purchase(card);
    request.put("installments", "1");
    cards.purchase(user, request);
    cards.closeDue(user);
    var invoice = reports.invoices(user).getFirst();
    String id = invoice.get("id").toString();
    var pay =
        form(
            "version",
            invoice.get("version").toString(),
            "account_id",
            account,
            "effective_date",
            "2026-10-28");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> cards.invoiceAction(user, id, "pay", pay));
      var b = executor.submit(() -> cards.invoiceAction(user, id, "pay", pay));
      assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
    }
    assertThat(
            repository.scalar(
                "SELECT COUNT(*) FROM invoice_payment WHERE user_id=? AND active=TRUE", user))
        .isEqualTo(1);
    assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(90000);
  }

  @Test
  void recurrenceIsIdempotentAndKeepsAnchor() {
    planning.saveRecurrence(
        user,
        form(
            "account_id",
            account,
            "category_id",
            expense,
            "kind",
            "DESPESA",
            "description",
            "Aluguel",
            "amount",
            "100",
            "anchor_day",
            "31",
            "start_date",
            "2026-01-01",
            "end_date",
            "2026-03-31"));
    planning.generate(user);
    var rows = finance.list("entry", user);
    assertThat(rows).hasSize(3);
    assertThat(rows.stream().map(row -> row.get("due_date").toString()))
        .contains("2026-02-28", "2026-03-31");
  }

  @Test
  void otherOwnerReferencesAndExportsAreIsolated() throws Exception {
    String second = UUID.randomUUID() + "@example.test";
    identity.register(
        Map.of("name", "Outro", "email", second, "password", "Outra senha segura 123!"));
    String other = identity.id(second);
    assertThatThrownBy(() -> finance.get("account", other, account))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> finance.saveEntry(other, entry("DESPESA", "10", "REALIZADO")))
        .isInstanceOf(DomainException.class);
    mvc.perform(get("/transactions").param("edit", account).with(user(second)))
        .andExpect(status().isNotFound());
    assertThat(reports.entries(other, Map.of(), false)).isEmpty();
    assertThat(exports.complete(other)).isNotEmpty();
  }

  @Test
  void archiveAndFutureDatesAreProtected() {
    assertThatThrownBy(() -> finance.archive(user, "account", account, form("version", "0")))
        .isInstanceOf(DomainException.class);
    var request = entry("DESPESA", "20", "REALIZADO");
    request.put("realized_date", "2026-10-30");
    assertThatThrownBy(() -> finance.saveEntry(user, request)).isInstanceOf(DomainException.class);
  }

  @Test
  void csvEscapesFormulaAndExportsAllPages() {
    for (int i = 0; i < 35; i++) {
      var request = entry("DESPESA", "1", "PENDENTE");
      request.put("description", i == 0 ? "=HYPERLINK(1)" : "Item " + i);
      finance.saveEntry(user, request);
    }
    assertThat(reports.entries(user, Map.of(), true)).hasSize(31);
    var all = reports.entries(user, Map.of("from", "2026-10-01", "to", "2026-10-01"), false);
    assertThat(all).hasSize(35);
    String csv = new String(exports.csv(all), StandardCharsets.UTF_8);
    assertThat(csv).contains("'=HYPERLINK(1)");
    assertThat(csv.lines().count()).isEqualTo(36);
  }

  @Test
  void financialAndAuditWritesRollbackTogether() {
    jdbc.execute(
        "ALTER TABLE audit ADD CONSTRAINT test_audit_failure CHECK(NOT(user_id='"
            + user
            + "' AND entity='entry' AND action='CRIAR'))");
    try {
      assertThatThrownBy(() -> finance.saveEntry(user, entry("DESPESA", "200", "REALIZADO")))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(finance.list("entry", user)).isEmpty();
      assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(100000);
    } finally {
      jdbc.execute("ALTER TABLE audit DROP CONSTRAINT test_audit_failure");
    }
  }

  @Test
  void allViewsRenderAndCsrfIsRequired() throws Exception {
    for (String path :
        List.of(
            "/dashboard",
            "/transactions",
            "/accounts",
            "/categories",
            "/cards",
            "/budgets",
            "/recurrences",
            "/reports",
            "/settings",
            "/audit"))
      mvc.perform(get(path).with(user(email)))
          .andExpect(status().isOk())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("Finanças")));
    mvc.perform(post("/accounts").with(user(email))).andExpect(status().isForbidden());
    mvc.perform(get("/accounts")).andExpect(status().is3xxRedirection());
  }

  @Test
  void actualLoginRequiresVerificationAndLogoutInvalidatesSession() throws Exception {
    mvc.perform(
            post("/auth/login")
                .with(csrf())
                .param("email", email)
                .param("password", "Senha de teste segura 123!"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/dashboard"));
    jdbc.update("UPDATE app_user SET verified=FALSE WHERE id=?", user);
    mvc.perform(
            post("/auth/login")
                .with(csrf())
                .param("email", email)
                .param("password", "Senha de teste segura 123!"))
        .andExpect(redirectedUrl("/auth/login?error"));
    mvc.perform(post("/auth/logout").with(user(email)).with(csrf()))
        .andExpect(redirectedUrl("/auth/login?logout"));
  }

  @Test
  void editingRecurrenceDoesNotCreateBackdatedOccurrences() {
    var request =
        form(
            "account_id",
            account,
            "category_id",
            expense,
            "kind",
            "DESPESA",
            "description",
            "Mensal",
            "amount",
            "100",
            "anchor_day",
            "1",
            "start_date",
            "2026-10-20",
            "end_date",
            "2026-12-31");
    String id = planning.saveRecurrence(user, request);
    assertThat(finance.list("entry", user)).hasSize(1);
    var edit = new LinkedHashMap<>(request);
    edit.put("id", id);
    edit.put("key", UUID.randomUUID().toString());
    edit.put("version", "0");
    edit.put("start_date", "2026-01-01");
    planning.saveRecurrence(user, edit);
    planning.generate(user);
    assertThat(finance.list("entry", user)).hasSize(1);
  }

  @Test
  void deletionRemovesAllFinancialData() {
    finance.saveEntry(user, entry("DESPESA", "20", "REALIZADO"));
    cards.purchase(user, purchase(createCard()));
    planning.budget(user, form("category_id", expense, "month", "2026-10", "amount", "500"));
    planning.saveRecurrence(
        user,
        form(
            "account_id",
            account,
            "category_id",
            expense,
            "kind",
            "DESPESA",
            "description",
            "Mensal",
            "amount",
            "100",
            "anchor_day",
            "1",
            "start_date",
            "2026-10-01",
            "end_date",
            "2026-12-01"));
    identity.delete(
        email, Map.of("confirmation", "EXCLUIR", "password", "Senha de teste segura 123!"));
    assertThatThrownBy(() -> identity.id(email)).isInstanceOf(DomainException.class);
    users.purge();
    assertThat(repository.scalar("SELECT COUNT(*) FROM account WHERE user_id=?", user)).isZero();
    assertThat(repository.scalar("SELECT COUNT(*) FROM entry WHERE user_id=?", user)).isZero();
    assertThat(repository.scalar("SELECT COUNT(*) FROM installment WHERE user_id=?", user))
        .isZero();
  }

  @Autowired BackupService backups;
  @Autowired IdentityRepository users;

  @Test
  void accessTokensExpireAreSingleUseAndResetRevokesSessions() throws Exception {
    String token = UUID.randomUUID().toString();
    users.token(
        OperationService.hash(token), user, "VERIFY", Instant.parse("2026-10-28T00:00:00Z"));
    assertThatThrownBy(() -> identity.verify(token)).isInstanceOf(DomainException.class);
    users.token(
        OperationService.hash(token), user, "VERIFY", Instant.parse("2026-10-30T00:00:00Z"));
    identity.verify(token);
    assertThatThrownBy(() -> identity.verify(token)).isInstanceOf(DomainException.class);
    var session =
        (org.springframework.mock.web.MockHttpSession)
            mvc.perform(
                    post("/auth/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", "Senha de teste segura 123!"))
                .andReturn()
                .getRequest()
                .getSession(false);
    String reset = UUID.randomUUID().toString();
    users.token(OperationService.hash(reset), user, "RESET", Instant.parse("2026-10-30T00:00:00Z"));
    identity.reset(reset, "Nova senha de teste segura 123!");
    assertThatThrownBy(() -> identity.reset(reset, "Nova senha de teste segura 123!"))
        .isInstanceOf(DomainException.class);
    mvc.perform(get("/accounts").session(session)).andExpect(redirectedUrl("/auth/login?expired"));
  }

  @Test
  void fullBackupPreservesUsersRelationsAndDerivedBalance(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    finance.saveEntry(user, entry("DESPESA", "200", "REALIZADO"));
    String card = createCard();
    cards.purchase(user, purchase(card));
    long balance = finance.balance(user, account, finance.today(user));
    char[] secret = "Senha de backup teste completo 123!".toCharArray();
    var encrypted = backups.create(directory, secret);
    var snapshot = directory.resolve("restored.sql");
    java.nio.file.Files.write(
        snapshot, BackupCrypto.decrypt(java.nio.file.Files.readAllBytes(encrypted), secret));
    try (var connection =
        java.sql.DriverManager.getConnection(
            "jdbc:h2:mem:restore"
                + UUID.randomUUID()
                + ";MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE",
            "sa",
            "")) {
      var target =
          new JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
      target.execute("RUNSCRIPT FROM '" + snapshot.toString().replace("'", "''") + "'");
      assertThat(
              target.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=?", Long.class, user))
          .isEqualTo(1);
      assertThat(
              target.queryForObject(
                  "SELECT COUNT(*) FROM installment p JOIN purchase c ON c.id=p.purchase_id JOIN invoice i ON i.id=p.invoice_id WHERE p.user_id=?",
                  Long.class,
                  user))
          .isEqualTo(3);
      var restoredRepository = new FinanceRepository(target);
      var restoredFinance =
          new FinanceService(
              restoredRepository,
              new OperationService(restoredRepository),
              Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), ZoneOffset.UTC));
      assertThat(restoredFinance.balance(user, account, LocalDate.of(2026, 10, 29)))
          .isEqualTo(balance);
    }
  }
}
