package br.com.financa.tools;

import br.com.financa.config.NeonSettings;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Read-only verification plus a denied, zero-row metadata write followed by rollback. */
public final class NeonVerify {
  public static void main(String[] args) throws Exception {
    var values =
        NeonSettings.read(Path.of(System.getenv().getOrDefault("NEON_ENV_FILE", ".env.local")));
    Map<String, Object> settings =
        NeonSettings.jdbc(key -> System.getenv().getOrDefault(key, values.get(key)));
    try (Connection connection =
            DriverManager.getConnection(
                settings.get("spring.datasource.url").toString(),
                "financas_app",
                settings.get("spring.datasource.password").toString());
        Statement statement = connection.createStatement()) {
      try (ResultSet role =
          statement.executeQuery(
              "SELECT current_user,rolsuper,rolcreatedb,rolcreaterole,has_schema_privilege(current_user,'public','CREATE') FROM pg_roles WHERE rolname=current_user")) {
        if (!role.next()
            || !role.getString(1).equals("financas_app")
            || role.getBoolean(2)
            || role.getBoolean(3)
            || role.getBoolean(4)
            || role.getBoolean(5))
          throw new IllegalStateException("Privilégios runtime inesperados.");
      }
      try (ResultSet history =
          statement.executeQuery(
              "SELECT count(*) FROM flyway_schema_history WHERE success=TRUE AND version IS NOT NULL")) {
        if (!history.next() || history.getInt(1) != 7)
          throw new IllegalStateException("Migrações incompletas.");
      }
      connection.setAutoCommit(false);
      try {
        statement.executeUpdate("UPDATE flyway_schema_history SET success=success WHERE FALSE");
        throw new IllegalStateException("Histórico Flyway está indevidamente gravável.");
      } catch (SQLException denied) {
        if (!"42501".equals(denied.getSQLState())) throw denied;
      } finally {
        connection.rollback();
      }
      System.out.println(
          "Neon verify OK: pooled JDBC/TLS; financas_app sem privilégios administrativos; 7 migrações; escrita no histórico bloqueada (42501).");
    }
  }
}
