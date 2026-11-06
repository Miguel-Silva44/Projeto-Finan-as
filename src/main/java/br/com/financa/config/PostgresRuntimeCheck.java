package br.com.financa.config;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres | prod | production")
public class PostgresRuntimeCheck implements ApplicationRunner {
  private final DataSource source;
  private final Environment environment;

  public PostgresRuntimeCheck(DataSource source, Environment environment) {
    this.source = source;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments arguments) throws Exception {
    try (Connection connection = source.getConnection()) {
      if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()))
        throw new IllegalStateException(
            "O perfil postgres/produção exige PostgreSQL; fallback H2 não é permitido.");
      try (var statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  "SELECT rolsuper,rolcreatedb,rolcreaterole FROM pg_roles WHERE rolname=current_user")) {
        if (!result.next() || result.getBoolean(1) || result.getBoolean(2) || result.getBoolean(3))
          throw new IllegalStateException(
              "A aplicação deve usar um papel runtime sem privilégios administrativos.");
      }
    }
    if (environment.matchesProfiles("prod | production")) {
      if (environment.matchesProfiles("dev"))
        throw new IllegalStateException("Não combine dev com produção.");
      if (!environment.getRequiredProperty("app.base-url").startsWith("https://"))
        throw new IllegalStateException("Produção exige BASE_URL HTTPS.");
      if (!"smtp".equals(environment.getRequiredProperty("app.mail-mode")))
        throw new IllegalStateException("Produção exige SMTP.");
    }
  }
}
