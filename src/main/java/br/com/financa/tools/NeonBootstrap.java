package br.com.financa.tools;

import br.com.financa.config.NeonSettings;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;

/** Explicit one-time provisioning; never resets passwords or imports local financial data. */
public final class NeonBootstrap {
  public static void main(String[] args) throws Exception {
    Path file = Path.of(System.getenv().getOrDefault("NEON_ENV_FILE", ".env.local"));
    Map<String, String> values = NeonSettings.read(file);
    URI direct = NeonSettings.uri(values.get("DATABASE_URL_UNPOOLED"));
    try (Connection connection =
        DriverManager.getConnection(
            NeonSettings.jdbcUrl(direct),
            NeonSettings.user(direct),
            NeonSettings.password(direct))) {
      prepare(connection, file, values, "financas_migrator", "NEON_MIGRATION_PASSWORD");
      prepare(connection, file, values, "financas_app", "NEON_APP_PASSWORD");
      String owner = NeonSettings.user(direct);
      if (!owner.matches("[a-zA-Z0-9_]+"))
        throw new IllegalArgumentException("Papel administrador inválido.");
      try (Statement statement = connection.createStatement()) {
        statement.execute("GRANT financas_migrator TO \"" + owner + "\"");
        statement.execute("ALTER SCHEMA public OWNER TO financas_migrator");
        statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
        statement.execute("GRANT USAGE ON SCHEMA public TO financas_app");
      }
    }
    // Prove both credentials without printing them. Existing roles are never silently rotated.
    for (String role : List.of("financas_app", "financas_migrator")) {
      String key = role.equals("financas_app") ? "NEON_APP_PASSWORD" : "NEON_MIGRATION_PASSWORD";
      try (Connection check =
          DriverManager.getConnection(NeonSettings.jdbcUrl(direct), role, values.get(key))) {
        if (!check.isValid(10))
          throw new IllegalStateException("Falha de conexão com papel configurado.");
      }
    }
    System.out.println(
        "Neon bootstrap OK: TLS verificado; papéis runtime/migração conectam; senhas preservadas no arquivo local ignorado.");
  }

  private static void prepare(
      Connection connection, Path file, Map<String, String> values, String role, String key)
      throws Exception {
    boolean exists;
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname=?")) {
      statement.setString(1, role);
      try (ResultSet result = statement.executeQuery()) {
        exists = result.next();
      }
    }
    if (exists) {
      if (!values.containsKey(key))
        throw new IllegalStateException(
            "Papel existente sem credencial local; recupere a senha antes de continuar.");
      return;
    }
    String password = values.get(key);
    if (password == null) {
      byte[] bytes = new byte[48];
      new SecureRandom().nextBytes(bytes);
      password = Base64.getEncoder().encodeToString(bytes);
      Files.writeString(
          file,
          "\n" + key + "=" + password + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.APPEND);
      values.put(key, password);
    }
    if (!password.matches("[A-Za-z0-9+/=]{32,}"))
      throw new IllegalArgumentException(
          "Senha de papel inválida; use o segredo gerado pelo bootstrap.");
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE "
              + role
              + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '"
              + password
              + "'");
    }
  }
}
