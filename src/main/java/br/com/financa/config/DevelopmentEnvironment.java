package br.com.financa.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

public final class DevelopmentEnvironment {
  private DevelopmentEnvironment() {}

  public static boolean enabled(Environment environment) {
    return environment.acceptsProfiles(Profiles.of("dev & !prod & !production"));
  }
}
