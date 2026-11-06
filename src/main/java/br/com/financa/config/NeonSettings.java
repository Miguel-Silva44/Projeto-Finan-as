package br.com.financa.config;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** Small dotenv/JDBC adapter. Never exposes connection strings or credentials in errors. */
public final class NeonSettings {
  private NeonSettings() {}

  public static Map<String, String> read(Path file) throws IOException {
    Map<String, String> result = new LinkedHashMap<>();
    if (!Files.exists(file)) return result;
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      String text = line.replace("\uFEFF", "").strip();
      if (text.startsWith("export ")) text = text.substring(7).strip();
      int separator = text.indexOf('=');
      if (separator < 1 || text.startsWith("#")) continue;
      String key = text.substring(0, separator).strip();
      String value = text.substring(separator + 1).strip();
      if (!key.matches("[A-Z][A-Z0-9_]*")) continue;
      if (value.length() >= 2
          && (value.startsWith("\"") && value.endsWith("\"")
              || value.startsWith("'") && value.endsWith("'")))
        value = value.substring(1, value.length() - 1);
      result.put(key, value);
    }
    return result;
  }

  public static URI uri(String url) {
    try {
      URI uri = URI.create(url == null ? "" : url);
      if (!("postgresql".equals(uri.getScheme()) || "postgres".equals(uri.getScheme()))
          || uri.getHost() == null
          || !uri.getHost().endsWith(".neon.tech")
          || uri.getRawUserInfo() == null
          || !uri.getRawUserInfo().contains(":")
          || uri.getPath() == null
          || !uri.getPath().matches("/[a-zA-Z0-9_-]+")
          || uri.getFragment() != null) throw new IllegalArgumentException();
      return uri;
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("URL Neon inválida; use a variável fornecida pelo CLI.");
    }
  }

  public static String user(URI uri) {
    return decode(uri.getRawUserInfo().split(":", 2)[0]);
  }

  public static String password(URI uri) {
    return decode(uri.getRawUserInfo().split(":", 2)[1]);
  }

  private static String decode(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  public static String jdbcUrl(URI uri) {
    return "jdbc:postgresql://"
        + uri.getHost()
        + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
        + uri.getPath()
        + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=20&socketTimeout=60";
  }

  public static Map<String, Object> jdbc(Function<String, String> value) {
    URI pooled = uri(value.apply("DATABASE_URL")),
        direct = uri(value.apply("DATABASE_URL_UNPOOLED"));
    if (!pooled.getHost().contains("-pooler.")
        || direct.getHost().contains("-pooler.")
        || !pooled.getHost().replace("-pooler.", ".").equals(direct.getHost())
        || !pooled.getPath().equals(direct.getPath()))
      throw new IllegalArgumentException("Configure o par pooled/direct da mesma branch Neon.");
    String runtime = required(value, "NEON_APP_PASSWORD"),
        migration = required(value, "NEON_MIGRATION_PASSWORD");
    return Map.of(
        "spring.datasource.url",
        jdbcUrl(pooled),
        "spring.datasource.username",
        "financas_app",
        "spring.datasource.password",
        runtime,
        "spring.flyway.url",
        jdbcUrl(direct),
        "spring.flyway.user",
        "financas_migrator",
        "spring.flyway.password",
        migration);
  }

  private static String required(Function<String, String> value, String key) {
    String result = value.apply(key);
    if (result == null || result.isBlank())
      throw new IllegalArgumentException(
          "Execute npm run neon:bootstrap para preparar os papéis do banco.");
    return result;
  }
}
