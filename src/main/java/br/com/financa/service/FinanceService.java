package br.com.financa.service;

import static br.com.financa.model.FinancialRules.*;
import static br.com.financa.repository.FinanceRepository.*;

import br.com.financa.model.DomainException;
import br.com.financa.repository.FinanceRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FinanceService {
  private final FinanceRepository repository;
  private final OperationService operations;
  private final Clock clock;

  public FinanceService(FinanceRepository repository, OperationService operations, Clock clock) {
    this.repository = repository;
    this.operations = operations;
    this.clock = clock;
  }

  public LocalDate today(String user) {
    var row = repository.query("SELECT timezone FROM app_user WHERE id=?", user).getFirst();
    return LocalDate.now(clock.withZone(ZoneId.of(str(row, "timezone"))));
  }

  public List<Map<String, Object>> list(String table, String user) {
    return repository.all(table, user);
  }

  public Map<String, Object> get(String table, String user, String id) {
    return repository.owned(table, user, id);
  }

  public Map<String, Object> active(String table, String user, String id) {
    var row = get(table, user, id);
    require(!Boolean.TRUE.equals(row.get("archived")), "O cadastro está arquivado.");
    return row;
  }

  public void category(String user, String id, String kind) {
    require(
        kind.equals(str(active("category", user, id), "kind")),
        "Categoria incompatível com o tipo de lançamento.");
  }

  public void accountDate(String user, String id, LocalDate effective) {
    var account = active("account", user, id);
    require(
        !effective.isBefore(date(str(account, "reference_date"))),
        "A data não pode anteceder o saldo inicial da conta.");
  }

  public void effective(String user, String account, LocalDate effective) {
    require(!effective.isAfter(today(user)), "A realização não pode estar no futuro.");
    accountDate(user, account, effective);
  }

  private int expected(Map<String, String> form) {
    try {
      return Integer.parseInt(form.get("version"));
    } catch (RuntimeException e) {
      throw new DomainException("Versão inválida. Recarregue a página.");
    }
  }

  private void kind(String kind) {
    require(Set.of("RECEITA", "DESPESA").contains(kind), "Tipo inválido.");
  }

  private String create(String table, String user, Map<String, Object> data) {
    String id = repository.insert(table, user, data);
    operations.audit(user, table, id, "CRIAR", Map.of(), data, "");
    return id;
  }

  private String update(
      String table,
      String user,
      String id,
      Map<String, String> form,
      Map<String, Object> data,
      String action) {
    var before = get(table, user, id);
    repository.update(table, user, id, expected(form), data);
    operations.audit(user, table, id, action, before, data, form.get("reason"));
    return id;
  }

  public String saveAccount(String user, Map<String, String> form) {
    return operations.run(
        user,
        "account.save",
        form,
        () -> {
          String type = form.get("kind");
          require(
              Set.of("CORRENTE", "POUPANCA", "CARTEIRA").contains(type), "Tipo de conta inválido.");
          LocalDate reference = date(form.get("reference_date"));
          require(!reference.isAfter(today(user)), "O saldo inicial não pode estar no futuro.");
          var data =
              Map.<String, Object>of(
                  "name",
                  text(form.get("name"), 100),
                  "kind",
                  type,
                  "opening",
                  cents(form.get("opening"), true),
                  "reference_date",
                  reference);
          if (form.getOrDefault("id", "").isBlank()) return create("account", user, data);
          String id = form.get("id");
          var before = active("account", user, id);
          require("yes".equals(form.get("confirm")), "Confirme a alteração do saldo inicial.");
          long earlier =
              repository.scalar(
                      "SELECT COUNT(*) FROM entry WHERE account_id=? AND (due_date<? OR realized_date<?) AND status<>'CANCELADO'",
                      id,
                      reference,
                      reference)
                  + repository.scalar(
                      "SELECT COUNT(*) FROM transfer WHERE (source_id=? OR destination_id=?) AND effective_date<? AND status='REALIZADO'",
                      id,
                      id,
                      reference)
                  + repository.scalar(
                      "SELECT COUNT(*) FROM invoice_payment WHERE account_id=? AND effective_date<? AND active=TRUE",
                      id,
                      reference)
                  + repository.scalar(
                      "SELECT COUNT(*) FROM recurrence WHERE account_id=? AND active=TRUE AND start_date<?",
                      id,
                      reference);
          require(earlier == 0, "Existem movimentações anteriores à nova data inicial.");
          return update("account", user, id, form, data, "EDITAR");
        });
  }

  public String saveCategory(String user, Map<String, String> form) {
    return operations.run(
        user,
        "category.save",
        form,
        () -> {
          kind(form.get("kind"));
          var data =
              Map.<String, Object>of("name", text(form.get("name"), 100), "kind", form.get("kind"));
          if (form.getOrDefault("id", "").isBlank()) return create("category", user, data);
          var before = active("category", user, form.get("id"));
          if (!str(before, "kind").equals(form.get("kind"))) {
            long used =
                repository.scalar("SELECT COUNT(*) FROM entry WHERE category_id=?", form.get("id"))
                    + repository.scalar(
                        "SELECT COUNT(*) FROM purchase WHERE category_id=?", form.get("id"))
                    + repository.scalar(
                        "SELECT COUNT(*) FROM recurrence WHERE category_id=?", form.get("id"))
                    + repository.scalar(
                        "SELECT COUNT(*) FROM budget WHERE category_id=?", form.get("id"));
            require(used == 0, "Não é possível trocar o tipo de uma categoria utilizada.");
          }
          return update("category", user, form.get("id"), form, data, "EDITAR");
        });
  }

  public String saveEntry(String user, Map<String, String> form) {
    return operations.run(
        user,
        "entry.save",
        form,
        () -> {
          String type = form.get("kind"), account = form.get("account_id");
          kind(type);
          category(user, form.get("category_id"), type);
          LocalDate due = date(form.get("due_date"));
          accountDate(user, account, due);
          String status = form.getOrDefault("status", "PENDENTE");
          require(Set.of("PENDENTE", "REALIZADO").contains(status), "Estado inválido.");
          Map<String, Object> data = new LinkedHashMap<>();
          data.put("account_id", account);
          data.put("category_id", form.get("category_id"));
          data.put("kind", type);
          data.put("description", text(form.get("description"), 200));
          data.put("amount", cents(form.get("amount"), false));
          data.put("competence", date(form.get("competence")));
          data.put("due_date", due);
          data.put("status", status);
          LocalDate realized = status.equals("REALIZADO") ? date(form.get("realized_date")) : null;
          if (realized != null) effective(user, account, realized);
          data.put("realized_date", realized);
          if (form.getOrDefault("id", "").isBlank()) return create("entry", user, data);
          var before = get("entry", user, form.get("id"));
          require(
              !str(before, "status").equals("CANCELADO"),
              "Lançamento cancelado não pode ser alterado.");
          if (str(before, "status").equals("REALIZADO"))
            require(
                "yes".equals(form.get("confirm")), "Confirme a alteração do lançamento realizado.");
          return update("entry", user, form.get("id"), form, data, "EDITAR");
        });
  }

  public String entryAction(String user, String id, String action, Map<String, String> form) {
    return operations.run(
        user,
        "entry." + id + "." + action,
        form,
        () -> {
          var entry = get("entry", user, id);
          String current = str(entry, "status");
          require(!current.equals("CANCELADO"), "O lançamento já está cancelado.");
          Map<String, Object> data = new LinkedHashMap<>();
          switch (action) {
            case "realize" -> {
              require(current.equals("PENDENTE"), "O lançamento já foi realizado.");
              LocalDate day = date(form.get("effective_date"));
              effective(user, str(entry, "account_id"), day);
              data.put("status", "REALIZADO");
              data.put("realized_date", day);
            }
            case "undo" -> {
              require(current.equals("REALIZADO"), "O lançamento ainda está pendente.");
              data.put("status", "PENDENTE");
              data.put("realized_date", null);
            }
            case "cancel" -> {
              text(form.get("reason"), 300);
              data.put("status", "CANCELADO");
              data.put("realized_date", null);
            }
            default -> throw new DomainException("Ação inválida.");
          }
          return update("entry", user, id, form, data, action.toUpperCase(Locale.ROOT));
        });
  }

  public String transfer(String user, Map<String, String> form) {
    return operations.run(
        user,
        "transfer.create",
        form,
        () -> {
          String source = form.get("source_id"), destination = form.get("destination_id");
          require(!source.equals(destination), "Escolha contas diferentes.");
          LocalDate day = date(form.get("effective_date"));
          effective(user, source, day);
          effective(user, destination, day);
          return create(
              "transfer",
              user,
              Map.of(
                  "source_id",
                  source,
                  "destination_id",
                  destination,
                  "amount",
                  cents(form.get("amount"), false),
                  "effective_date",
                  day));
        });
  }

  public String cancelTransfer(String user, String id, Map<String, String> form) {
    return operations.run(
        user,
        "transfer.cancel." + id,
        form,
        () -> {
          text(form.get("reason"), 300);
          require(
              str(get("transfer", user, id), "status").equals("REALIZADO"),
              "Transferência já cancelada.");
          return update("transfer", user, id, form, Map.of("status", "CANCELADO"), "CANCELAR");
        });
  }

  public long balance(String user, String id, LocalDate day) {
    var account = get("account", user, id);
    if (day.isBefore(date(str(account, "reference_date")))) return 0;
    return number(account, "opening")
        + repository.scalar(
            "SELECT COALESCE(SUM(CASE WHEN kind='RECEITA' THEN amount ELSE -amount END),0) FROM entry WHERE user_id=? AND account_id=? AND status='REALIZADO' AND realized_date<=?",
            user,
            id,
            day)
        + repository.scalar(
            "SELECT COALESCE(SUM(CASE WHEN destination_id=? THEN amount ELSE -amount END),0) FROM transfer WHERE user_id=? AND (source_id=? OR destination_id=?) AND status='REALIZADO' AND effective_date<=?",
            id,
            user,
            id,
            id,
            day)
        - repository.scalar(
            "SELECT COALESCE(SUM(amount),0) FROM invoice_payment WHERE user_id=? AND account_id=? AND active=TRUE AND effective_date<=?",
            user,
            id,
            day);
  }

  public String archive(String user, String table, String id, Map<String, String> form) {
    require(Set.of("account", "category", "card").contains(table), "Cadastro inválido.");
    return operations.run(
        user,
        table + ".archive." + id,
        form,
        () -> {
          active(table, user, id);
          if (table.equals("account")) {
            require(
                balance(user, id, today(user)) == 0,
                "A conta precisa ter saldo zero para ser arquivada.");
            require(
                repository.scalar(
                            "SELECT COUNT(*) FROM entry WHERE account_id=? AND status='PENDENTE'",
                            id)
                        == 0
                    && repository.scalar(
                            "SELECT COUNT(*) FROM recurrence WHERE account_id=? AND active=TRUE",
                            id)
                        == 0,
                "Há pendências ou recorrências nesta conta.");
          }
          if (table.equals("category"))
            require(
                repository.scalar(
                        "SELECT COUNT(*) FROM recurrence WHERE category_id=? AND active=TRUE", id)
                    == 0,
                "Encerre as recorrências desta categoria primeiro.");
          if (table.equals("card"))
            require(
                repository.scalar(
                        "SELECT COUNT(*) FROM invoice WHERE card_id=? AND status<>'PAGA'", id)
                    == 0,
                "O cartão possui faturas em aberto.");
          return update(table, user, id, form, Map.of("archived", true), "ARQUIVAR");
        });
  }
}
