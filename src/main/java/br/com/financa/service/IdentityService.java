package br.com.financa.service;

import static br.com.financa.model.FinancialRules.*;
import static br.com.financa.repository.FinanceRepository.str;

import br.com.financa.model.DomainException;
import br.com.financa.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
  private final IdentityRepository repository;
  private final FinanceRepository finance;
  private final PasswordEncoder passwords;
  private final MailService mail;
  private final Clock clock;
  private final String base;
  private final SessionRegistry sessions;

  public IdentityService(
      IdentityRepository repository,
      FinanceRepository finance,
      PasswordEncoder passwords,
      MailService mail,
      Clock clock,
      @Value("${app.base-url}") String base,
      SessionRegistry sessions) {
    this.repository = repository;
    this.finance = finance;
    this.passwords = passwords;
    this.mail = mail;
    this.clock = clock;
    this.base = base;
    this.sessions = sessions;
  }

  public String id(String email) {
    return repository
        .email(email)
        .map(row -> str(row, "id"))
        .orElseThrow(() -> new DomainException("A sessão expirou. Entre novamente."));
  }

  public Map<String, Object> profile(String email) {
    return repository.user(id(email));
  }

  private String email(String value) {
    String email = text(value, 200).toLowerCase(Locale.ROOT);
    require(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Informe um e-mail válido.");
    return email;
  }

  private void password(String password) {
    require(
        password != null && password.length() >= 12 && password.length() <= 100,
        "A senha deve ter de 12 a 100 caracteres.");
  }

  @Transactional
  public void register(Map<String, String> form) {
    String email = email(form.get("email"));
    password(form.get("password"));
    String name = text(form.get("name"), 100);
    var existing = repository.email(email);
    if (existing.isPresent()) {
      if (!Boolean.TRUE.equals(existing.get().get("verified")))
        issue(str(existing.get(), "id"), email, "VERIFY");
      return;
    }
    String id = UUID.randomUUID().toString();
    repository.insert(id, name, email, passwords.encode(form.get("password")));
    for (String nameCategory :
        List.of("Moradia", "Alimentação", "Transporte", "Saúde", "Lazer", "Outros gastos"))
      finance.insert("category", id, Map.of("name", nameCategory, "kind", "DESPESA"));
    for (String nameCategory : List.of("Salário", "Outras receitas"))
      finance.insert("category", id, Map.of("name", nameCategory, "kind", "RECEITA"));
    issue(id, email, "VERIFY");
  }

  private void issue(String id, String email, String purpose) {
    String token = UUID.randomUUID() + UUID.randomUUID().toString();
    repository.token(
        OperationService.hash(token),
        id,
        purpose,
        clock.instant().plusSeconds(purpose.equals("VERIFY") ? 86400 : 1800));
    mail.send(
        email,
        purpose.equals("VERIFY")
            ? "Confirme seu acesso ao Controle Financeiro"
            : "Redefina sua senha",
        base
            + (purpose.equals("VERIFY") ? "/auth/verify?token=" : "/auth/reset?token=")
            + token
            + "\n\nSe você não solicitou, ignore esta mensagem.");
  }

  @Transactional
  public void recover(String value) {
    String email = email(value);
    repository.email(email).ifPresent(row -> issue(str(row, "id"), email, "RESET"));
  }

  @Transactional
  public void verify(String token) {
    var row =
        repository
            .consume(OperationService.hash(token), "VERIFY", clock.instant())
            .orElseThrow(() -> new DomainException("Link inválido, utilizado ou expirado."));
    repository.verify(str(row, "user_id"));
  }

  @Transactional
  public void reset(String token, String password) {
    password(password);
    var row =
        repository
            .consume(OperationService.hash(token), "RESET", clock.instant())
            .orElseThrow(() -> new DomainException("Link inválido, utilizado ou expirado."));
    String id = str(row, "user_id");
    repository.password(id, passwords.encode(password));
    expire(str(repository.user(id), "email"));
  }

  @Transactional
  public void update(String email, Map<String, String> form) {
    String id = id(email);
    String zone = form.get("timezone");
    try {
      ZoneId.of(zone);
    } catch (Exception e) {
      throw new DomainException("Fuso horário inválido.");
    }
    repository.profile(id, text(form.get("name"), 100), zone);
  }

  @Transactional
  public void changePassword(String email, Map<String, String> form) {
    var user = repository.email(email).orElseThrow();
    require(
        passwords.matches(form.getOrDefault("current_password", ""), str(user, "password_hash")),
        "Senha atual incorreta.");
    password(form.get("password"));
    repository.password(str(user, "id"), passwords.encode(form.get("password")));
    expire(email);
  }

  @Transactional
  public void delete(String email, Map<String, String> form) {
    var user = repository.email(email).orElseThrow();
    require(
        "EXCLUIR".equals(form.get("confirmation"))
            && passwords.matches(form.getOrDefault("password", ""), str(user, "password_hash")),
        "Confirme EXCLUIR e informe a senha correta.");
    repository.requestDeletion(str(user, "id"));
    expire(email);
  }

  private void expire(String email) {
    for (Object principal : sessions.getAllPrincipals())
      if (principal instanceof org.springframework.security.core.userdetails.UserDetails details
          && details.getUsername().equals(email))
        sessions.getAllSessions(principal, false).forEach(session -> session.expireNow());
  }
}
