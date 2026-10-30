package br.com.financa.repository;

import br.com.financa.model.DomainException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FinanceRepository {
  private static final Set<String> TABLES =
      Set.of(
          "account",
          "category",
          "entry",
          "transfer",
          "card",
          "invoice",
          "purchase",
          "installment",
          "invoice_payment",
          "recurrence",
          "budget",
          "audit");
  private final JdbcTemplate jdbc;

  public FinanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static Map<String, Object> row(ResultSet rs, int index) throws SQLException {
    Map<String, Object> data = new LinkedHashMap<>();
    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
      data.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
    return data;
  }

  private void table(String table) {
    if (!TABLES.contains(table)) throw new IllegalArgumentException("Tabela inválida");
  }

  public List<Map<String, Object>> all(String table, String user) {
    table(table);
    return jdbc.query(
        "SELECT * FROM " + table + " WHERE user_id=? ORDER BY id", FinanceRepository::row, user);
  }

  public Map<String, Object> owned(String table, String user, String id) {
    table(table);
    return jdbc
        .query(
            "SELECT * FROM " + table + " WHERE id=? AND user_id=?",
            FinanceRepository::row,
            id,
            user)
        .stream()
        .findFirst()
        .orElseThrow(() -> new DomainException("NOT_FOUND", "Registro não encontrado."));
  }

  public String insert(String table, String user, Map<String, Object> fields) {
    table(table);
    String id = UUID.randomUUID().toString();
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("id", id);
    values.put("user_id", user);
    values.putAll(fields);
    validateColumns(values);
    jdbc.update(
        "INSERT INTO "
            + table
            + " ("
            + String.join(",", values.keySet())
            + ") VALUES ("
            + String.join(",", Collections.nCopies(values.size(), "?"))
            + ")",
        values.values().toArray());
    return id;
  }

  private void validateColumns(Map<String, Object> fields) {
    if (fields.isEmpty() || fields.keySet().stream().anyMatch(key -> !key.matches("[a-z_]+")))
      throw new IllegalArgumentException("Campos inválidos");
  }

  public void update(
      String table, String user, String id, int version, Map<String, Object> fields) {
    table(table);
    validateColumns(fields);
    List<Object> args = new ArrayList<>(fields.values());
    args.add(id);
    args.add(user);
    args.add(version);
    int count =
        jdbc.update(
            "UPDATE "
                + table
                + " SET "
                + String.join(",", fields.keySet().stream().map(k -> k + "=?").toList())
                + ",version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND version=?",
            args.toArray());
    if (count != 1)
      throw new DomainException(
          "CONFLICT", "Este registro mudou. Recarregue a página e tente novamente.");
  }

  public List<Map<String, Object>> query(String sql, Object... args) {
    return jdbc.query(sql, FinanceRepository::row, args);
  }

  public int execute(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public void lockUser(String user) {
    var rows = query("SELECT id FROM app_user WHERE id=? AND deleted_at IS NULL FOR UPDATE", user);
    if (rows.isEmpty()) throw new DomainException("NOT_FOUND", "Usuário indisponível.");
  }

  public long scalar(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  public static String str(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString();
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  public static int version(Map<String, Object> row) {
    return ((Number) row.get("version")).intValue();
  }
}
