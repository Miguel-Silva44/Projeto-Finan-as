package br.com.financa.config;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.financa.repository.IdentityRepository;
import br.com.financa.service.DevelopmentAccountService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:devAccess;MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.jobs-enabled=false",
      "app.dev.email=dev@financas.local",
      "app.dev.password=DevFinanceiro123!"
    })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DevelopmentAccessTest {
  @Autowired MockMvc mvc;
  @Autowired IdentityRepository users;
  @Autowired UserDetailsService details;
  @Autowired DevelopmentAccountService accounts;
  @Autowired PasswordEncoder passwords;

  @Test
  void devLoginOpensEveryFeatureWithoutEmailVerification() throws Exception {
    var user = details.loadUserByUsername("dev@financas.local");
    assertThat(user.isEnabled()).isTrue();
    assertThat(user.getAuthorities())
        .extracting(Object::toString)
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_DEV");
    var result =
        mvc.perform(
                post("/auth/login")
                    .with(csrf())
                    .param("email", "dev@financas.local")
                    .param("password", "DevFinanceiro123!"))
            .andExpect(redirectedUrl("/dashboard"))
            .andReturn();
    var session = (MockHttpSession) result.getRequest().getSession(false);
    for (String route :
        List.of(
            "dashboard",
            "transactions",
            "accounts",
            "categories",
            "cards",
            "budgets",
            "recurrences",
            "reports",
            "settings",
            "audit",
            "me/export")) mvc.perform(get("/" + route).session(session)).andExpect(status().isOk());
  }

  @Test
  void devCredentialsAreDisabledOutsideExclusiveDevProfile() {
    for (String[] profiles :
        new String[][] {{}, {"prod"}, {"production"}, {"dev", "prod"}, {"dev", "production"}}) {
      MockEnvironment environment = new MockEnvironment();
      environment.setActiveProfiles(profiles);
      assertThat(
              new SecurityConfig()
                  .users(users, environment)
                  .loadUserByUsername("dev@financas.local")
                  .isEnabled())
          .isFalse();
    }
  }

  @Test
  void initializerDoesNotExistWithoutDevOrWhenProductionIsPresent() {
    for (String profile : List.of("default", "prod", "production", "dev,prod", "dev,production")) {
      new ApplicationContextRunner()
          .withPropertyValues("spring.profiles.active=" + profile)
          .withUserConfiguration(
              DevelopmentAccountInitializer.class, DevelopmentAccountService.class)
          .run(
              context -> {
                assertThat(context).doesNotHaveBean(DevelopmentAccountInitializer.class);
                assertThat(context).doesNotHaveBean(DevelopmentAccountService.class);
              });
    }
  }

  @Test
  void repeatedStartupPreservesAccountAndChangedPassword() {
    String before = users.email("dev@financas.local").orElseThrow().get("password_hash").toString();
    accounts.ensure("dev@financas.local", "Outra senha segura 123!");
    var after = users.email("dev@financas.local").orElseThrow();
    assertThat(after.get("password_hash")).isEqualTo(before);
    assertThat(passwords.matches("DevFinanceiro123!", after.get("password_hash").toString()))
        .isTrue();
    assertThat(after.get("verified")).isEqualTo(true);
  }
}
