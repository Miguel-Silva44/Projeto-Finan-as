package br.com.financa.service;

import static br.com.financa.model.FinancialRules.*;
import static br.com.financa.repository.FinanceRepository.*;

import br.com.financa.repository.FinanceRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {
  private final FinanceRepository repository;
  private final FinanceService finance;
  private final OperationService operations;

  public PlanningService(
      FinanceRepository repository, FinanceService finance, OperationService operations) {
    this.repository = repository;
    this.finance = finance;
    this.operations = operations;
  }

  public String saveRecurrence(String user, Map<String, String> form) {
    return operations.run(
        user,
        "recurrence.save",
        form,
        () -> {
          String type = form.get("kind");
          finance.category(user, form.get("category_id"), type);
          LocalDate start = date(form.get("start_date"));
          finance.accountDate(user, form.get("account_id"), start);
          int day = CardService.integer(form, "anchor_day");
          onDay(YearMonth.from(start), day);
          LocalDate end =
              form.getOrDefault("end_date", "").isBlank() ? null : date(form.get("end_date"));
          require(end == null || !end.isBefore(start), "O término deve ser posterior ao início.");
          Map<String, Object> fields = new LinkedHashMap<>();
          fields.put("account_id", form.get("account_id"));
          fields.put("category_id", form.get("category_id"));
          fields.put("kind", type);
          fields.put("description", text(form.get("description"), 200));
          fields.put("amount", cents(form.get("amount"), false));
          fields.put("anchor_day", day);
          fields.put("start_date", start);
          fields.put("end_date", end);
          String id = form.getOrDefault("id", "");
          if (id.isBlank()) {
            id = repository.insert("recurrence", user, fields);
            operations.audit(user, "recurrence", id, "CRIAR", Map.of(), fields, "");
          } else {
            var before = finance.get("recurrence", user, id);
            require(Boolean.TRUE.equals(before.get("active")), "Recorrência encerrada.");
            fields.put("generation_floor", finance.today(user));
            repository.update("recurrence", user, id, CardService.integer(form, "version"), fields);
            operations.audit(user, "recurrence", id, "EDITAR", before, fields, "");
          }
          generateInternal(user);
          return id;
        });
  }

  public String stop(String user, String id, Map<String, String> form) {
    return operations.run(
        user,
        "recurrence.stop." + id,
        form,
        () -> {
          var before = finance.get("recurrence", user, id);
          repository.update(
              "recurrence",
              user,
              id,
              CardService.integer(form, "version"),
              Map.of("active", false));
          operations.audit(user, "recurrence", id, "ENCERRAR", before, Map.of("active", false), "");
          return id;
        });
  }

  @Transactional
  public void generate(String user) {
    repository.lockUser(user);
    generateInternal(user);
  }

  private void generateInternal(String user) {
    YearMonth horizon = YearMonth.from(finance.today(user)).plusMonths(1);
    for (var rule :
        repository.query("SELECT * FROM recurrence WHERE user_id=? AND active=TRUE", user)) {
      LocalDate start = date(str(rule, "start_date"));
      if (rule.get("generation_floor") != null
          && date(str(rule, "generation_floor")).isAfter(start))
        start = date(str(rule, "generation_floor"));
      LocalDate end =
          rule.get("end_date") == null ? horizon.atEndOfMonth() : date(str(rule, "end_date"));
      for (YearMonth cursor = YearMonth.from(start);
          !cursor.isAfter(horizon) && !cursor.atDay(1).isAfter(end);
          cursor = cursor.plusMonths(1)) {
        LocalDate due = onDay(cursor, ((Number) rule.get("anchor_day")).intValue());
        if (due.isBefore(start) || due.isAfter(end)) continue;
        if (repository.scalar(
                "SELECT COUNT(*) FROM entry WHERE recurrence_id=? AND occurrence_month=?",
                rule.get("id"),
                cursor.toString())
            > 0) continue;
        var fields =
            Map.<String, Object>of(
                "account_id",
                rule.get("account_id"),
                "category_id",
                rule.get("category_id"),
                "kind",
                rule.get("kind"),
                "description",
                rule.get("description"),
                "amount",
                rule.get("amount"),
                "competence",
                due,
                "due_date",
                due,
                "status",
                "PENDENTE",
                "recurrence_id",
                rule.get("id"),
                "occurrence_month",
                cursor.toString());
        String id = repository.insert("entry", user, fields);
        operations.audit(user, "entry", id, "GERAR_RECORRENCIA", Map.of(), fields, "");
      }
    }
  }

  public String budget(String user, Map<String, String> form) {
    return operations.run(
        user,
        "budget.save",
        form,
        () -> {
          finance.category(user, form.get("category_id"), "DESPESA");
          String month = month(form.get("month")).toString();
          long amount = cents(form.get("amount"), false);
          var rows =
              repository.query(
                  "SELECT * FROM budget WHERE user_id=? AND category_id=? AND month=?",
                  user,
                  form.get("category_id"),
                  month);
          Map<String, Object> fields =
              Map.of("category_id", form.get("category_id"), "month", month, "amount", amount);
          if (rows.isEmpty()) {
            require(form.getOrDefault("version", "").isBlank(), "O orçamento mudou. Recarregue.");
            String id = repository.insert("budget", user, fields);
            operations.audit(user, "budget", id, "CRIAR", Map.of(), fields, "");
            return id;
          }
          var before = rows.getFirst();
          repository.update(
              "budget", user, str(before, "id"), CardService.integer(form, "version"), fields);
          operations.audit(user, "budget", str(before, "id"), "EDITAR", before, fields, "");
          return str(before, "id");
        });
  }

  public String copyBudget(String user, Map<String, String> form) {
    return operations.run(
        user,
        "budget.copy",
        form,
        () -> {
          YearMonth target = month(form.get("month"));
          for (var previous :
              repository.query(
                  "SELECT b.* FROM budget b JOIN category c ON c.id=b.category_id WHERE b.user_id=? AND b.month=? AND c.archived=FALSE",
                  user,
                  target.minusMonths(1).toString())) {
            if (repository.scalar(
                    "SELECT COUNT(*) FROM budget WHERE user_id=? AND category_id=? AND month=?",
                    user,
                    previous.get("category_id"),
                    target.toString())
                == 0) {
              var fields =
                  Map.<String, Object>of(
                      "category_id",
                      previous.get("category_id"),
                      "month",
                      target.toString(),
                      "amount",
                      previous.get("amount"));
              String id = repository.insert("budget", user, fields);
              operations.audit(user, "budget", id, "COPIAR", Map.of(), fields, "");
            }
          }
          return target.toString();
        });
  }
}
