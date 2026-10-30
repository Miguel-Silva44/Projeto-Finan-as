package br.com.financa.service;

import static br.com.financa.model.FinancialRules.require;

import br.com.financa.repository.FinanceRepository;
import br.com.financa.repository.IdentityRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("dev & !prod & !production")
public class DevelopmentAccountService {
  private final IdentityRepository users;
  private final FinanceRepository finance;
  private final PasswordEncoder passwords;

  public DevelopmentAccountService(
      IdentityRepository users, FinanceRepository finance, PasswordEncoder passwords) {
    this.users = users;
    this.finance = finance;
    this.passwords = passwords;
  }

  @Transactional
  public void ensure(String value, String password) {
    String email = value.strip().toLowerCase(Locale.ROOT);
    require(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "E-mail de desenvolvimento inválido.");
    require(
        password.length() >= 12 && password.length() <= 100,
        "A senha dev deve ter de 12 a 100 caracteres.");
    var existing = users.includingDeleted(email);
    if (existing.isPresent()) {
      require(
          Boolean.TRUE.equals(existing.get().get("development_only")),
          "O e-mail dev já pertence a uma conta comum. Configure outro DEV_LOGIN_EMAIL.");
      // A restart must not reset a changed password or reactivate a deleted account.
      return;
    }
    String id = UUID.randomUUID().toString();
    users.insert(id, "Desenvolvedor", email, passwords.encode(password));
    users.markDevelopment(id);
    for (String name :
        List.of("Moradia", "Alimentação", "Transporte", "Saúde", "Lazer", "Outros gastos"))
      finance.insert("category", id, Map.of("name", name, "kind", "DESPESA"));
    for (String name : List.of("Salário", "Outras receitas"))
      finance.insert("category", id, Map.of("name", name, "kind", "RECEITA"));
  }
}
