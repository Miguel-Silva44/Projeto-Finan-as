package br.com.financa;

import static org.assertj.core.api.Assertions.*;

import br.com.financa.model.*;
import br.com.financa.repository.FinanceRepository;
import br.com.financa.service.*;
import br.com.financa.tools.H2PostgresImporter;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
      "app.jobs-enabled=false",
      "spring.flyway.locations=classpath:db/migration,classpath:db/postgresql"
    })
@ActiveProfiles("postgres")
@Import(FinanceIntegrationTest.FixedTime.class)
@AutoConfigureMockMvc
class FinancePostgresIT extends FinanceIntegrationTest {
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              org.testcontainers.utility.DockerImageName.parse(
                      "postgres@sha256:f1ea2a1a4fb7b68d4836be1fdcb8a6466a89b072158bf9d97663600538c294c5")
                  .asCompatibleSubstituteFor("postgres"))
          .withUsername("financas_admin")
          .withPassword("admin-test-only")
          .withDatabaseName("financas")
          .withInitScript("postgres-test-init.sql");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    POSTGRES.start();
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", () -> "financas_app");
    properties.add("spring.datasource.password", () -> "runtime-test-only");
    properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    properties.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    properties.add("spring.flyway.user", () -> "financas_migrator");
    properties.add("spring.flyway.password", () -> "migration-test-only");
  }

  static Connection admin() throws Exception {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "financas_admin", "admin-test-only");
  }

  static String url(String database) {
    return "jdbc:postgresql://"
        + POSTGRES.getHost()
        + ":"
        + POSTGRES.getMappedPort(5432)
        + "/"
        + database;
  }

  static void checked(org.testcontainers.containers.Container.ExecResult result) {
    assertThat(result.getExitCode())
        .withFailMessage("Container command failed: %s", result.getStderr())
        .isZero();
  }

  @Override
  @Test
  void financialAndAuditWritesRollbackTogether() {
    try (var connection = admin();
        var statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE audit ADD CONSTRAINT test_audit_failure CHECK(NOT(user_id='"
              + user
              + "' AND entity='entry' AND action='CRIAR'))");
      try {
        assertThatThrownBy(() -> finance.saveEntry(user, entry("DESPESA", "200", "REALIZADO")))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(finance.list("entry", user)).isEmpty();
        assertThat(finance.balance(user, account, finance.today(user))).isEqualTo(100000);
      } finally {
        statement.execute("ALTER TABLE audit DROP CONSTRAINT test_audit_failure");
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  @Test
  void fullBackupPreservesUsersRelationsAndDerivedBalance(@TempDir Path directory)
      throws Exception {
    finance.saveEntry(user, entry("DESPESA", "200", "REALIZADO"));
    cards.purchase(user, purchase(createCard()));
    long before = finance.balance(user, account, finance.today(user));
    checked(
        POSTGRES.execInContainer(
            "pg_dump",
            "-U",
            "financas_admin",
            "-d",
            "financas",
            "-Fc",
            "-f",
            "/tmp/finance-test.dump"));
    byte[] dump =
        POSTGRES.copyFileFromContainer("/tmp/finance-test.dump", java.io.InputStream::readAllBytes);
    char[] secret = "Integration backup test password 123!".toCharArray();
    byte[] encrypted = BackupCrypto.encrypt(dump, secret);
    assertThatThrownBy(
            () -> BackupCrypto.decrypt(encrypted, "wrong-password-long-enough".toCharArray()))
        .isInstanceOf(Exception.class);
    assertThat(BackupCrypto.decrypt(encrypted, secret)).isEqualTo(dump);
    String restored = "financas_restore_" + UUID.randomUUID().toString().replace("-", "");
    checked(
        POSTGRES.execInContainer(
            "createdb", "-U", "financas_admin", "-O", "financas_migrator", restored));
    checked(
        POSTGRES.execInContainer(
            "pg_restore",
            "-U",
            "financas_admin",
            "--role=financas_migrator",
            "--no-owner",
            "--no-privileges",
            "--single-transaction",
            "--exit-on-error",
            "-d",
            restored,
            "/tmp/finance-test.dump"));
    try (var grantConnection =
        DriverManager.getConnection(url(restored), "financas_admin", "admin-test-only")) {
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          grantConnection,
          new org.springframework.core.io.ClassPathResource(
              "db/postgresql/V7__runtime_grants.sql"));
    }
    try (var connection =
        DriverManager.getConnection(url(restored), "financas_app", "runtime-test-only")) {
      var target = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
      var repository = new FinanceRepository(target);
      var restoredFinance =
          new FinanceService(
              repository,
              new OperationService(repository),
              Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), ZoneOffset.UTC));
      assertThat(restoredFinance.balance(user, account, finance.today(user))).isEqualTo(before);
      assertThat(
              target.queryForObject(
                  "SELECT COUNT(*) FROM installment WHERE user_id=?", Long.class, user))
          .isEqualTo(3);
    }
  }

  @Test
  void runtimeCannotChangeSchemaOrMigrationHistory() {
    assertThatThrownBy(() -> jdbc.execute("CREATE TABLE forbidden(id INT)"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.execute(
                    "UPDATE flyway_schema_history SET description='forbidden' WHERE installed_rank=1"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class))
        .isFalse();
  }

  @Test
  void h2ImportReconcilesEveryRecordAndIsRepeatableOnlyWhenIdentical() throws Exception {
    String target = "financas_restore_" + UUID.randomUUID().toString().replace("-", "");
    checked(
        POSTGRES.execInContainer(
            "createdb", "-U", "financas_admin", "-O", "financas_migrator", target));
    Flyway.configure()
        .dataSource(url(target), "financas_migrator", "migration-test-only")
        .locations("classpath:db/migration", "classpath:db/postgresql")
        .load()
        .migrate();
    String source =
        "jdbc:h2:mem:import"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    try (var h2 = DriverManager.getConnection(source, "sa", "");
        var pg =
            DriverManager.getConnection(url(target), "financas_migrator", "migration-test-only")) {
      Flyway.configure().dataSource(new SingleConnectionDataSource(h2, true)).load().migrate();
      String id = UUID.randomUUID().toString();
      try (var insert =
          h2.prepareStatement(
              "INSERT INTO app_user(id,name,email,password_hash,verified) VALUES(?,?,?,?,TRUE)")) {
        insert.setString(1, id);
        insert.setString(2, "Migration Test");
        insert.setString(3, id + "@example.test");
        insert.setString(4, "existing-hash-preserved");
        insert.executeUpdate();
      }
      var sourceJdbc = new JdbcTemplate(new SingleConnectionDataSource(h2, true));
      String accountId = UUID.randomUUID().toString();
      sourceJdbc.update(
          "INSERT INTO account(id,user_id,name,kind,opening,reference_date) VALUES(?,?,?,'CORRENTE',12345,DATE '2026-01-01')",
          accountId,
          id,
          "Import Test");
      sourceJdbc.update(
          "INSERT INTO access_token(hash,user_id,purpose,expires_at) VALUES(?,?,'VERIFY',CURRENT_TIMESTAMP)",
          "token-not-imported",
          id);
      var result = H2PostgresImporter.transfer(h2, pg, false);
      assertThat(result.alreadyImported()).isFalse();
      assertThat(H2PostgresImporter.transfer(h2, pg, false).alreadyImported()).isTrue();
      var targetJdbc = new JdbcTemplate(new SingleConnectionDataSource(pg, true));
      assertThat(
              targetJdbc.queryForObject(
                  "SELECT password_hash FROM app_user WHERE id=?", String.class, id))
          .isEqualTo("existing-hash-preserved");
      assertThat(
              targetJdbc.queryForObject(
                  "SELECT opening FROM account WHERE id=?", Long.class, accountId))
          .isEqualTo(12345);
      assertThat(targetJdbc.queryForObject("SELECT COUNT(*) FROM access_token", Long.class))
          .isZero();
      targetJdbc.update("UPDATE account SET opening=999 WHERE id=?", accountId);
      pg.commit();
      assertThatThrownBy(() -> H2PostgresImporter.transfer(h2, pg, false))
          .isInstanceOf(IllegalStateException.class);
    }
  }
}
