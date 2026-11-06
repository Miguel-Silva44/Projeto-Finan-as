package br.com.financa.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Offline, explicit import. Never deletes source/target data or imports active access tokens. */
public final class H2PostgresImporter {
  private static final List<String> TABLES =
      List.of(
          "app_user",
          "account",
          "category",
          "recurrence",
          "entry",
          "transfer",
          "card",
          "purchase",
          "invoice",
          "installment",
          "invoice_payment",
          "budget",
          "audit",
          "idempotency");

  private H2PostgresImporter() {}

  public record Result(String sourceHash, String summary, boolean alreadyImported) {}

  private record Table(String name, List<String> columns, String order) {}

  public static void main(String[] args) throws Exception {
    var env = System.getenv();
    String sourceUrl = required(env, "IMPORT_SOURCE_URL"),
        targetUrl = required(env, "IMPORT_TARGET_URL");
    String sourcePassword = env.getOrDefault("IMPORT_SOURCE_PASSWORD", "");
    String targetPassword =
        Files.readString(
                Path.of(required(env, "IMPORT_TARGET_PASSWORD_FILE")), StandardCharsets.UTF_8)
            .strip();
    boolean allowDevelopment = "true".equals(env.get("IMPORT_ALLOW_DEVELOPMENT"));
    if (!targetUrl.startsWith("jdbc:postgresql:"))
      throw new IllegalArgumentException("IMPORT_TARGET_URL deve apontar para PostgreSQL.");
    Flyway.configure()
        .dataSource(
            targetUrl, env.getOrDefault("IMPORT_TARGET_USER", "financas_migrator"), targetPassword)
        .locations("classpath:db/migration", "classpath:db/postgresql")
        .load()
        .migrate();
    try (var source =
            DriverManager.getConnection(
                sourceUrl, env.getOrDefault("IMPORT_SOURCE_USER", "sa"), sourcePassword);
        var target =
            DriverManager.getConnection(
                targetUrl,
                env.getOrDefault("IMPORT_TARGET_USER", "financas_migrator"),
                targetPassword)) {
      Result result = transfer(source, target, allowDevelopment);
      Path report = Path.of(env.getOrDefault("IMPORT_REPORT", "data/import-report.txt"));
      if (report.toAbsolutePath().getParent() != null)
        Files.createDirectories(report.toAbsolutePath().getParent());
      Files.writeString(
          report,
          "Status: "
              + (result.alreadyImported() ? "already-imported" : "completed")
              + "\nSource SHA-256: "
              + result.sourceHash()
              + "\n"
              + result.summary(),
          StandardCharsets.UTF_8);
      System.out.println("Importação reconciliada. Relatório sem credenciais: " + report);
    }
  }

  private static String required(Map<String, String> env, String key) {
    String value = env.get(key);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Defina " + key);
    return value;
  }

  public static Result transfer(Connection source, Connection target, boolean allowDevelopment)
      throws Exception {
    if (!source.getMetaData().getDatabaseProductName().equals("H2")
        || !target.getMetaData().getDatabaseProductName().equals("PostgreSQL"))
      throw new IllegalArgumentException("Origem H2 e destino PostgreSQL são obrigatórios.");
    source.setReadOnly(true);
    source.setAutoCommit(false);
    source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    target.setAutoCommit(false);
    target.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    try {
      try (var lock = target.createStatement()) {
        lock.execute("SELECT pg_advisory_xact_lock(493105207)");
      }
      boolean currentSource = false;
      try (var statement = source.createStatement();
          var versions =
              statement.executeQuery("SELECT version,success,type FROM flyway_schema_history")) {
        while (versions.next()) {
          if (versions.getBoolean(2)
              && versions.getString(1) == null
              && Set.of("SCHEMA", "TABLE").contains(versions.getString(3))) continue;
          if (!versions.getBoolean(2)
              || versions.getString(1) == null
              || Integer.parseInt(versions.getString(1)) > 4)
            throw new IllegalStateException(
                "A origem deve estar íntegra na cadeia H2 V1-V4. Registro de histórico: versão="
                    + versions.getString(1)
                    + ", tipo="
                    + versions.getString(3)
                    + ", sucesso="
                    + versions.getBoolean(2));
          if ("4".equals(versions.getString(1))) currentSource = true;
        }
      }
      if (!currentSource)
        throw new IllegalStateException("Atualize a origem H2 até V4 antes de importar.");
      if (!allowDevelopment
          && count(source, "SELECT COUNT(*) FROM app_user WHERE development_only=TRUE") > 0)
        throw new IllegalStateException(
            "Origem contém contas de desenvolvimento. Use destino dev explícito ou uma origem sem essas contas.");
      List<Table> tables = new ArrayList<>();
      for (String name : TABLES) {
        List<String> columns = new ArrayList<>();
        try (var statement = source.createStatement();
            var rows = statement.executeQuery("SELECT * FROM " + name + " WHERE 1=0")) {
          var metadata = rows.getMetaData();
          for (int i = 1; i <= metadata.getColumnCount(); i++) {
            String column = metadata.getColumnLabel(i).toLowerCase(Locale.ROOT);
            if (!column.matches("[a-z_]+")) throw new IllegalStateException("Coluna não suportada");
            columns.add(column);
          }
        }
        tables.add(
            new Table(
                name,
                List.copyOf(columns),
                name.equals("idempotency") ? "user_id,operation_key" : "id"));
      }
      String before = fingerprint(source, tables);
      try (var statement =
          target.prepareStatement(
              "SELECT source_hash FROM data_import_manifest WHERE source_hash=?")) {
        statement.setString(1, before);
        try (var rows = statement.executeQuery()) {
          if (rows.next()) {
            if (!before.equals(fingerprint(target, tables)))
              throw new IllegalStateException(
                  "A importação anterior foi concluída, mas o destino já mudou. Não repita a carga.");
            target.rollback();
            source.rollback();
            return new Result(
                before, "Importação anterior idêntica; nenhuma escrita realizada.\n", true);
          }
        }
      }
      for (String table : TABLES)
        if (count(target, "SELECT COUNT(*) FROM " + table) > 0)
          throw new IllegalStateException(
              "Destino contém dados; a importação só aceita banco de negócio vazio.");
      if (count(target, "SELECT COUNT(*) FROM access_token") > 0
          || count(target, "SELECT COUNT(*) FROM data_import_manifest") > 0)
        throw new IllegalStateException("Destino não está vazio.");
      StringBuilder summary = new StringBuilder();
      for (Table table : tables) {
        long copied = copy(source, target, table);
        long actual = count(target, "SELECT COUNT(*) FROM " + table.name());
        if (actual != copied)
          throw new IllegalStateException("Contagem divergente: " + table.name());
        summary.append(table.name()).append(": ").append(copied).append(" registros\n");
      }
      if (!before.equals(fingerprint(target, tables)))
        throw new IllegalStateException("Reconciliação de conteúdo falhou. Importação revertida.");
      // Password hashes, IDs, money, dates, audit and idempotency are identical. Access tokens are
      // intentionally invalidated.
      try (var statement =
          target.prepareStatement(
              "INSERT INTO data_import_manifest(source_hash,content_summary) VALUES(?,?)")) {
        statement.setString(1, before);
        statement.setString(2, summary.toString());
        statement.executeUpdate();
      }
      target.commit();
      source.rollback();
      return new Result(before, summary.toString(), false);
    } catch (Exception e) {
      target.rollback();
      source.rollback();
      throw e;
    }
  }

  private static Object value(ResultSet rows, int column) throws Exception {
    Object value = rows.getObject(column);
    return value instanceof Clob clob
        ? clob.getSubString(1, Math.toIntExact(clob.length()))
        : value;
  }

  private static long copy(Connection source, Connection target, Table table) throws Exception {
    String fields = String.join(",", table.columns());
    long total = 0;
    try (var query = source.createStatement();
        var rows =
            query.executeQuery(
                "SELECT " + fields + " FROM " + table.name() + " ORDER BY " + table.order());
        var insert =
            target.prepareStatement(
                "INSERT INTO "
                    + table.name()
                    + " ("
                    + fields
                    + ") VALUES ("
                    + String.join(",", Collections.nCopies(table.columns().size(), "?"))
                    + ")")) {
      int batch = 0;
      while (rows.next()) {
        for (int i = 1; i <= table.columns().size(); i++) insert.setObject(i, value(rows, i));
        insert.addBatch();
        total++;
        batch++;
        if (batch == 500) {
          insert.executeBatch();
          batch = 0;
        }
      }
      if (batch > 0) insert.executeBatch();
    }
    return total;
  }

  private static long count(Connection connection, String sql) throws Exception {
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(sql)) {
      rows.next();
      return rows.getLong(1);
    }
  }

  private static String fingerprint(Connection connection, List<Table> tables) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (Table table : tables) {
      update(digest, table.name());
      for (String column : table.columns()) update(digest, column);
      try (var statement = connection.createStatement();
          var rows =
              statement.executeQuery(
                  "SELECT "
                      + String.join(",", table.columns())
                      + " FROM "
                      + table.name()
                      + " ORDER BY "
                      + table.order())) {
        while (rows.next()) {
          digest.update((byte) 1);
          for (int i = 1; i <= table.columns().size(); i++) {
            Object value = value(rows, i);
            if (value == null) {
              digest.update((byte) 0);
              continue;
            }
            digest.update((byte) 1);
            String text =
                value instanceof OffsetDateTime time
                    ? time.toInstant().toString()
                    : value instanceof Timestamp time
                        ? time.toInstant().toString()
                        : value.toString();
            update(digest, text);
          }
        }
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void update(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
