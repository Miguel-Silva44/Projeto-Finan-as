package br.com.financa.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** Converts Neon CLI URLs into JDBC settings without putting owner credentials in the app pool. */
public final class NeonEnvironment implements EnvironmentPostProcessor, Ordered {
  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication app) {
    if (!environment.matchesProfiles("neon")) return;
    try {
      Path file = Path.of(environment.getProperty("NEON_ENV_FILE", ".env.local"));
      Map<String, String> local = NeonSettings.read(file);
      java.util.function.Function<String, String> value =
          key -> environment.getProperty(key, local.get(key));
      Map<String, Object> settings = NeonSettings.jdbc(value);
      environment.getPropertySources().addFirst(new MapPropertySource("neonJdbc", settings));
    } catch (IOException exception) {
      throw new IllegalStateException("Não foi possível ler a configuração Neon local.");
    }
  }
}
