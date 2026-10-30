package br.com.financa.service;

import static br.com.financa.model.FinancialRules.*;
import static br.com.financa.repository.FinanceRepository.*;

import br.com.financa.model.DomainException;
import br.com.financa.repository.FinanceRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {
  private final FinanceRepository repository;
  private final FinanceService finance;
  private final OperationService operations;

  public CardService(
      FinanceRepository repository, FinanceService finance, OperationService operations) {
    this.repository = repository;
    this.finance = finance;
    this.operations = operations;
  }

  public String save(String user, Map<String, String> form) {
    return operations.run(
        user,
        "card.save",
        form,
        () -> {
          int closing = integer(form, "closing_day"), due = integer(form, "due_day");
          onDay(YearMonth.now(), closing);
          onDay(YearMonth.now(), due);
          var fields =
              Map.<String, Object>of(
                  "name",
                  text(form.get("name"), 100),
                  "credit_limit",
                  cents(form.get("credit_limit"), false),
                  "closing_day",
                  closing,
                  "due_day",
                  due);
          String id = form.getOrDefault("id", "");
          if (id.isBlank()) {
            id = repository.insert("card", user, fields);
            operations.audit(user, "card", id, "CRIAR", Map.of(), fields, "");
          } else {
            var before = finance.active("card", user, id);
            repository.update("card", user, id, integer(form, "version"), fields);
            operations.audit(user, "card", id, "EDITAR", before, fields, "");
          }
          return id;
        });
  }

  public static int integer(Map<String, String> form, String field) {
    try {
      return Integer.parseInt(form.get(field));
    } catch (RuntimeException e) {
      throw new DomainException("Preencha o número corretamente: " + field);
    }
  }

  public record Preview(int number, long amount, String month, LocalDate due) {}

  public long outstanding(String user, String card, String excludePurchase) {
    finance.get("card", user, card);
    return repository.scalar(
        "SELECT COALESCE(SUM(p.amount),0) FROM installment p JOIN purchase c ON c.id=p.purchase_id JOIN invoice i ON i.id=p.invoice_id WHERE p.user_id=? AND c.card_id=? AND c.status='ATIVA' AND i.status<>'PAGA' AND c.id<>?",
        user,
        card,
        excludePurchase == null ? "" : excludePurchase);
  }

  public boolean exceedsLimit(String user, Map<String, String> form) {
    var card = finance.active("card", user, form.get("card_id"));
    return outstanding(user, str(card, "id"), form.get("id")) + cents(form.get("amount"), false)
        > number(card, "credit_limit");
  }

  public List<Preview> preview(String user, Map<String, String> form) {
    var card = finance.active("card", user, form.get("card_id"));
    LocalDate day = date(form.get("purchase_date"));
    require(!day.isAfter(finance.today(user)), "A compra não pode estar no futuro.");
    List<Long> parts = split(cents(form.get("amount"), false), integer(form, "installments"));
    YearMonth first = invoiceMonth(day, ((Number) card.get("closing_day")).intValue());
    // Existing cycles preserve their frozen closing date even after calendar changes.
    var frozen =
        repository.query(
            "SELECT * FROM invoice WHERE card_id=? AND month=?",
            card.get("id"),
            YearMonth.from(day).toString());
    if (!frozen.isEmpty())
      first =
          day.isBefore(date(str(frozen.getFirst(), "closing_date")))
              ? YearMonth.from(day)
              : YearMonth.from(day).plusMonths(1);
    List<Preview> result = new ArrayList<>();
    for (int i = 0; i < parts.size(); i++) {
      YearMonth month = first.plusMonths(i);
      var existing =
          repository.query(
              "SELECT * FROM invoice WHERE card_id=? AND month=?",
              card.get("id"),
              month.toString());
      LocalDate due =
          existing.isEmpty()
              ? onDay(month.plusMonths(1), ((Number) card.get("due_day")).intValue())
              : date(str(existing.getFirst(), "due_date"));
      result.add(new Preview(i + 1, parts.get(i), month.toString(), due));
    }
    return result;
  }

  public String purchase(String user, Map<String, String> form) {
    return operations.run(
        user,
        "purchase.save",
        form,
        () -> {
          finance.category(user, form.get("category_id"), "DESPESA");
          var card = finance.active("card", user, form.get("card_id"));
          List<Preview> plan = preview(user, form);
          String existingId = form.getOrDefault("id", "");
          Map<String, Object> fields =
              Map.of(
                  "card_id",
                  form.get("card_id"),
                  "category_id",
                  form.get("category_id"),
                  "description",
                  text(form.get("description"), 200),
                  "amount",
                  cents(form.get("amount"), false),
                  "installments",
                  integer(form, "installments"),
                  "purchase_date",
                  date(form.get("purchase_date")));
          String id;
          if (existingId.isBlank()) {
            id = repository.insert("purchase", user, fields);
            operations.audit(user, "purchase", id, "CRIAR", Map.of(), fields, "");
          } else {
            var before = finance.get("purchase", user, existingId);
            require(str(before, "status").equals("ATIVA"), "Compra cancelada.");
            assertOpen(user, existingId);
            repository.update("purchase", user, existingId, integer(form, "version"), fields);
            repository.execute(
                "DELETE FROM installment WHERE purchase_id=? AND user_id=?", existingId, user);
            id = existingId;
            operations.audit(user, "purchase", id, "EDITAR", before, fields, "");
          }
          for (Preview part : plan) {
            var invoices =
                repository.query(
                    "SELECT * FROM invoice WHERE card_id=? AND month=? AND user_id=?",
                    card.get("id"),
                    part.month(),
                    user);
            String invoice;
            if (invoices.isEmpty())
              invoice =
                  repository.insert(
                      "invoice",
                      user,
                      Map.of(
                          "card_id",
                          card.get("id"),
                          "month",
                          part.month(),
                          "closing_date",
                          onDay(month(part.month()), ((Number) card.get("closing_day")).intValue()),
                          "due_date",
                          part.due()));
            else {
              require(
                  str(invoices.getFirst(), "status").equals("ABERTA"),
                  "Reabra a fatura antes de corrigir ou adicionar esta compra.");
              invoice = str(invoices.getFirst(), "id");
            }
            repository.insert(
                "installment",
                user,
                Map.of(
                    "purchase_id",
                    id,
                    "invoice_id",
                    invoice,
                    "number",
                    part.number(),
                    "amount",
                    part.amount()));
          }
          return id;
        });
  }

  private void assertOpen(String user, String purchase) {
    require(
        repository.scalar(
                "SELECT COUNT(*) FROM installment p JOIN invoice i ON i.id=p.invoice_id WHERE p.user_id=? AND p.purchase_id=? AND i.status<>'ABERTA'",
                user,
                purchase)
            == 0,
        "Todas as faturas da compra precisam estar abertas para corrigir.");
  }

  public String cancelPurchase(String user, String id, Map<String, String> form) {
    return operations.run(
        user,
        "purchase.cancel." + id,
        form,
        () -> {
          var before = finance.get("purchase", user, id);
          require(str(before, "status").equals("ATIVA"), "Compra já cancelada.");
          assertOpen(user, id);
          text(form.get("reason"), 300);
          repository.update(
              "purchase", user, id, integer(form, "version"), Map.of("status", "CANCELADA"));
          operations.audit(
              user,
              "purchase",
              id,
              "CANCELAR",
              before,
              Map.of("status", "CANCELADA"),
              form.get("reason"));
          return id;
        });
  }

  public long total(String user, String id) {
    finance.get("invoice", user, id);
    return repository.scalar(
        "SELECT COALESCE(SUM(p.amount),0) FROM installment p JOIN purchase c ON c.id=p.purchase_id WHERE p.invoice_id=? AND p.user_id=? AND c.status='ATIVA'",
        id,
        user);
  }

  public String invoiceAction(String user, String id, String action, Map<String, String> form) {
    return operations.run(
        user,
        "invoice." + id + "." + action,
        form,
        () -> {
          var invoice = finance.get("invoice", user, id);
          String status = str(invoice, "status");
          Map<String, Object> changes = new LinkedHashMap<>();
          switch (action) {
            case "close" -> {
              require(status.equals("ABERTA"), "A fatura não está aberta.");
              require(
                  !date(str(invoice, "closing_date")).isAfter(finance.today(user)),
                  "Aguarde a data de fechamento.");
              changes.put("status", total(user, id) == 0 ? "PAGA" : "FECHADA");
              changes.put("manual_reopen", false);
            }
            case "reopen" -> {
              require(
                  status.equals("FECHADA") || status.equals("PAGA") && total(user, id) == 0,
                  "Desfaça o pagamento antes de reabrir.");
              changes.put("status", "ABERTA");
              changes.put("manual_reopen", true);
            }
            case "pay" -> {
              require(status.equals("FECHADA"), "Feche a fatura antes de pagar.");
              long value = total(user, id);
              require(value > 0, "Fatura sem valor a pagar.");
              String account = form.get("account_id");
              LocalDate day = date(form.get("effective_date"));
              finance.effective(user, account, day);
              repository.insert(
                  "invoice_payment",
                  user,
                  Map.of(
                      "invoice_id",
                      id,
                      "account_id",
                      account,
                      "amount",
                      value,
                      "effective_date",
                      day,
                      "active_invoice",
                      id));
              changes.put("status", "PAGA");
            }
            case "undo" -> {
              require(status.equals("PAGA"), "A fatura não está paga.");
              text(form.get("reason"), 300);
              require(
                  repository.execute(
                          "UPDATE invoice_payment SET active=FALSE,active_invoice=NULL WHERE invoice_id=? AND user_id=? AND active=TRUE",
                          id,
                          user)
                      == 1,
                  "Não há pagamento ativo para desfazer.");
              changes.put("status", "FECHADA");
            }
            default -> throw new DomainException("Ação inválida.");
          }
          repository.update("invoice", user, id, integer(form, "version"), changes);
          operations.audit(user, "invoice", id, action, invoice, changes, form.get("reason"));
          return id;
        });
  }

  @Transactional
  public void closeDue(String user) {
    repository.lockUser(user);
    for (var invoice :
        repository.query(
            "SELECT * FROM invoice WHERE user_id=? AND status='ABERTA' AND manual_reopen=FALSE AND closing_date<=?",
            user,
            finance.today(user))) {
      String id = str(invoice, "id");
      var changes = Map.<String, Object>of("status", total(user, id) == 0 ? "PAGA" : "FECHADA");
      repository.update("invoice", user, id, version(invoice), changes);
      operations.audit(user, "invoice", id, "FECHAR_AUTOMATICAMENTE", invoice, changes, "");
    }
  }
}
