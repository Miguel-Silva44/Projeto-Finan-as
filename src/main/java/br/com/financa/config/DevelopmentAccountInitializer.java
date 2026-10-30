package br.com.financa.config;

import br.com.financa.service.DevelopmentAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod & !production")
public class DevelopmentAccountInitializer implements ApplicationRunner {
  private final DevelopmentAccountService accounts;
  private final String email;
  private final String password;

  public DevelopmentAccountInitializer(
      DevelopmentAccountService accounts,
      @Value("${app.dev.email}") String email,
      @Value("${app.dev.password}") String password) {
    this.accounts = accounts;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    accounts.ensure(email, password);
  }
}
