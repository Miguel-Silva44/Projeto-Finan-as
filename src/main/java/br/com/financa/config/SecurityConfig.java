package br.com.financa.config;

import br.com.financa.repository.IdentityRepository;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  @Bean
  SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }

  @Bean
  HttpSessionEventPublisher sessionEvents() {
    return new HttpSessionEventPublisher();
  }

  @Bean
  UserDetailsService users(
      IdentityRepository repository, org.springframework.core.env.Environment environment) {
    return username ->
        repository
            .email(username.strip().toLowerCase(Locale.ROOT))
            .map(
                row -> {
                  boolean development = Boolean.TRUE.equals(row.get("development_only"));
                  return User.withUsername(row.get("email").toString())
                      .password(row.get("password_hash").toString())
                      .roles(development ? new String[] {"USER", "DEV"} : new String[] {"USER"})
                      .disabled(
                          !Boolean.TRUE.equals(row.get("verified"))
                              || development && !DevelopmentEnvironment.enabled(environment))
                      .build();
                })
            .orElseThrow(() -> new UsernameNotFoundException("Acesso inválido"));
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      SessionRegistry registry,
      @org.springframework.beans.factory.annotation.Value("${app.security.max-sessions:5}")
          int maximumSessions)
      throws Exception {
    if (maximumSessions < 1 || maximumSessions > 100)
      throw new IllegalArgumentException("Limite de sessões inválido.");
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/auth/**",
                        "/css/**",
                        "/js/**",
                        "/error",
                        "/health/ready",
                        "/manifest.webmanifest",
                        "/sw.js",
                        "/offline.html",
                        "/icons/**",
                        "/privacy",
                        "/support",
                        "/account-deletion",
                        "/.well-known/assetlinks.json")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/auth/login")
                    .loginProcessingUrl("/auth/login")
                    .usernameParameter("email")
                    .defaultSuccessUrl("/dashboard", true)
                    .failureUrl("/auth/login?error"))
        .logout(
            logout ->
                logout
                    .logoutUrl("/auth/logout")
                    .logoutSuccessUrl("/auth/login?logout")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID"))
        .sessionManagement(
            session ->
                session
                    .maximumSessions(maximumSessions)
                    .sessionRegistry(registry)
                    .expiredUrl("/auth/login?expired"))
        .headers(
            headers ->
                headers.contentSecurityPolicy(
                    csp ->
                        csp.policyDirectives(
                            "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; manifest-src 'self'; worker-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")))
        .build();
  }
}
