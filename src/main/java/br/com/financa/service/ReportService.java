package br.com.financa.service;

import static br.com.financa.model.FinancialRules.*;
import static br.com.financa.repository.FinanceRepository.*;

import br.com.financa.repository.FinanceRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
@org.springframework.transaction.annotation.Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class ReportService {
  private final FinanceRepository repository;
  private final FinanceService finance;
  private final CardService cards;

  public ReportService(FinanceRepository repository, FinanceService finance, CardService cards) {
    this.repository = repository;
    this.finance = finance;
    this.cards = cards;
  }

  public List<Map<String, Object>> accounts(String user) {
    return finance.list("account", user).stream()
        .map(
            row -> {
              var result = new LinkedHashMap<>(row);
              result.put("balance", finance.balance(user, str(row, "id"), finance.today(user)));
              return (Map<String, Object>) result;
            })
        .toList();
  }

  public List<Map<String, Object>> cards(String user) {
    return finance.list("card", user).stream()
        .map(
            row -> {
              row.put("outstanding", cards.outstanding(user, str(row, "id"), null));
              return row;
            })
        .toList();
  }

  public List<Map<String, Object>> invoices(String user) {
    return repository
        .query(
            "SELECT i.*,c.name AS card_name FROM invoice i JOIN card c ON c.id=i.card_id WHERE i.user_id=? ORDER BY i.month DESC,c.name",
            user)
        .stream()
        .map(
            row -> {
              row.put("total", cards.total(user, str(row, "id")));
              row.put(
                  "overdue",
                  !str(row, "status").equals("PAGA")
                      && date(str(row, "due_date")).isBefore(finance.today(user)));
              return row;
            })
        .toList();
  }

  public List<Map<String, Object>> purchases(String user) {
    return repository.query(
        "SELECT p.*,c.name AS card_name,g.name AS category_name FROM purchase p JOIN card c ON c.id=p.card_id JOIN category g ON g.id=p.category_id WHERE p.user_id=? ORDER BY p.purchase_date DESC,p.id",
        user);
  }

  public List<Map<String, Object>> entries(
      String user, Map<String, String> filters, boolean paginate) {
    List<Object> args = new ArrayList<>();
    args.add(user);
    StringBuilder sql =
        new StringBuilder(
            "SELECT e.*,a.name AS account_name,c.name AS category_name FROM entry e JOIN account a ON a.id=e.account_id JOIN category c ON c.id=e.category_id WHERE e.user_id=?");
    String dateField =
        switch (filters.getOrDefault("basis", "competence")) {
          case "due_date" -> "due_date";
          case "realized_date" -> "realized_date";
          default -> "competence";
        };
    for (String key : List.of("account_id", "category_id", "kind", "status")) {
      if (!filters.getOrDefault(key, "").isBlank()) {
        sql.append(" AND e.").append(key).append("=?");
        args.add(filters.get(key));
      }
    }
    if (!filters.getOrDefault("from", "").isBlank()) {
      sql.append(" AND e.").append(dateField).append(">=?");
      args.add(date(filters.get("from")));
    }
    if (!filters.getOrDefault("to", "").isBlank()) {
      sql.append(" AND e.").append(dateField).append("<=?");
      args.add(date(filters.get("to")));
    }
    if (!filters.getOrDefault("q", "").isBlank()) {
      sql.append(" AND LOWER(e.description) LIKE ? ESCAPE '!'");
      args.add(
          "%"
              + filters
                  .get("q")
                  .toLowerCase(Locale.ROOT)
                  .replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_")
              + "%");
    }
    sql.append(" ORDER BY e.").append(dateField).append(" DESC,e.id");
    if (paginate) {
      int page = 1;
      try {
        page = Math.max(1, Math.min(100000, Integer.parseInt(filters.getOrDefault("page", "1"))));
      } catch (NumberFormatException ignored) {
      }
      sql.append(" LIMIT 31 OFFSET ?");
      args.add((page - 1) * 30);
    }
    return repository.query(sql.toString(), args.toArray()).stream()
        .map(
            row -> {
              row.put(
                  "overdue",
                  str(row, "status").equals("PENDENTE")
                      && date(str(row, "due_date")).isBefore(finance.today(user)));
              return row;
            })
        .toList();
  }

  public List<Map<String, Object>> transfers(String user) {
    return repository.query(
        "SELECT t.*,a.name AS source_name,b.name AS destination_name FROM transfer t JOIN account a ON a.id=t.source_id JOIN account b ON b.id=t.destination_id WHERE t.user_id=? ORDER BY t.effective_date DESC,t.id",
        user);
  }

  public List<Map<String, Object>> breakdown(String user, YearMonth month) {
    var categories =
        repository.query(
            "SELECT * FROM category WHERE user_id=? AND kind='DESPESA' ORDER BY name", user);
    for (var category : categories) {
      String id = str(category, "id");
      long consumed =
          repository.scalar(
                  "SELECT COALESCE(SUM(amount),0) FROM entry WHERE user_id=? AND category_id=? AND status='REALIZADO' AND competence BETWEEN ? AND ?",
                  user,
                  id,
                  month.atDay(1),
                  month.atEndOfMonth())
              + repository.scalar(
                  "SELECT COALESCE(SUM(p.amount),0) FROM installment p JOIN purchase c ON c.id=p.purchase_id JOIN invoice i ON i.id=p.invoice_id WHERE p.user_id=? AND c.category_id=? AND c.status='ATIVA' AND i.month=?",
                  user,
                  id,
                  month.toString());
      long pending =
          repository.scalar(
              "SELECT COALESCE(SUM(amount),0) FROM entry WHERE user_id=? AND category_id=? AND status='PENDENTE' AND competence BETWEEN ? AND ?",
              user,
              id,
              month.atDay(1),
              month.atEndOfMonth());
      var budget =
          repository.query(
              "SELECT * FROM budget WHERE user_id=? AND category_id=? AND month=?",
              user,
              id,
              month.toString());
      category.put("consumed", consumed);
      category.put("pending", pending);
      category.put("limit", budget.isEmpty() ? null : budget.getFirst().get("amount"));
      category.put("budget_version", budget.isEmpty() ? "" : str(budget.getFirst(), "version"));
      if (!budget.isEmpty()) {
        long limit = number(budget.getFirst(), "amount");
        category.put("available", limit - consumed - pending);
        category.put("percent", (consumed + pending) * 100 / limit);
      }
    }
    return categories;
  }

  public Map<String, Object> dashboard(String user, YearMonth month, LocalDate horizon) {
    require(
        !horizon.isBefore(finance.today(user)), "A projeção deve ser para hoje ou data futura.");
    var accounts = accounts(user);
    long balance = accounts.stream().mapToLong(row -> number(row, "balance")).sum();
    var breakdown = breakdown(user, month);
    long income =
        repository.scalar(
            "SELECT COALESCE(SUM(amount),0) FROM entry WHERE user_id=? AND kind='RECEITA' AND status='REALIZADO' AND competence BETWEEN ? AND ?",
            user,
            month.atDay(1),
            month.atEndOfMonth());
    long expense = breakdown.stream().mapToLong(row -> number(row, "consumed")).sum();
    long projected =
        balance
            + repository.scalar(
                "SELECT COALESCE(SUM(CASE WHEN kind='RECEITA' THEN amount ELSE -amount END),0) FROM entry WHERE user_id=? AND status='PENDENTE' AND due_date<=?",
                user,
                horizon);
    var invoices = invoices(user);
    for (var invoice : invoices)
      if (!str(invoice, "status").equals("PAGA")
          && !date(str(invoice, "due_date")).isAfter(horizon))
        projected -= number(invoice, "total");
    var due =
        repository.query(
            "SELECT e.*,a.name AS account_name FROM entry e JOIN account a ON a.id=e.account_id WHERE e.user_id=? AND e.status='PENDENTE' AND e.due_date<=? ORDER BY e.due_date,e.id LIMIT 20",
            user,
            finance.today(user).plusDays(7));
    return Map.of(
        "balance",
        balance,
        "income",
        income,
        "expense",
        expense,
        "result",
        income - expense,
        "projected",
        projected,
        "accounts",
        accounts,
        "breakdown",
        breakdown,
        "due",
        due,
        "invoices",
        invoices);
  }

  public List<Map<String, Object>> competence(
      String user, LocalDate from, LocalDate to, String category, String account) {
    require(!to.isBefore(from), "O fim do período deve ser posterior ao início.");
    List<Object> args = new ArrayList<>(List.of(user, from, to));
    String where = "";
    if (!category.isBlank()) {
      where += " AND e.category_id=?";
      args.add(category);
    }
    if (!account.isBlank()) {
      where += " AND e.account_id=?";
      args.add(account);
    }
    var rows =
        repository.query(
            "SELECT e.id,e.description,e.kind,e.amount,e.competence AS report_date,c.name AS category_name,a.name AS account_name,'CONTA' AS origin FROM entry e JOIN category c ON c.id=e.category_id JOIN account a ON a.id=e.account_id WHERE e.user_id=? AND e.status='REALIZADO' AND e.competence BETWEEN ? AND ?"
                + where,
            args.toArray());
    if (account.isBlank()) {
      args = new ArrayList<>(List.of(user, from, to));
      where = "";
      if (!category.isBlank()) {
        where = " AND c.category_id=?";
        args.add(category);
      }
      rows.addAll(
          repository.query(
              "SELECT c.id,c.description,'DESPESA' AS kind,p.amount,CAST(CONCAT(i.month,'-01') AS DATE) AS report_date,g.name AS category_name,k.name AS account_name,'CARTAO' AS origin FROM installment p JOIN purchase c ON c.id=p.purchase_id JOIN invoice i ON i.id=p.invoice_id JOIN category g ON g.id=c.category_id JOIN card k ON k.id=c.card_id WHERE p.user_id=? AND c.status='ATIVA' AND CAST(CONCAT(i.month,'-01') AS DATE) BETWEEN ? AND ?"
                  + where,
              args.toArray()));
    }
    rows.sort(Comparator.comparing(row -> str(row, "report_date")));
    return rows;
  }

  public List<Map<String, Object>> cashflow(
      String user, LocalDate from, LocalDate to, String account) {
    require(!to.isBefore(from), "O fim do período deve ser posterior ao início.");
    List<Object> args = new ArrayList<>(List.of(user, from, to));
    String clause = account.isBlank() ? "" : " AND account_id=?";
    if (!account.isBlank()) args.add(account);
    var rows =
        repository.query(
            "SELECT id,description,kind,amount,realized_date AS report_date,account_id,'CONTA' AS origin FROM entry WHERE user_id=? AND status='REALIZADO' AND realized_date BETWEEN ? AND ?"
                + clause,
            args.toArray());
    rows.addAll(
        repository.query(
            "SELECT id,'Pagamento de fatura' AS description,'DESPESA' AS kind,amount,effective_date AS report_date,account_id,'FATURA' AS origin FROM invoice_payment WHERE user_id=? AND active=TRUE AND effective_date BETWEEN ? AND ?"
                + clause,
            args.toArray()));
    if (!account.isBlank()) {
      rows.addAll(
          repository.query(
              "SELECT id,'Transferência recebida' AS description,'RECEITA' AS kind,amount,effective_date AS report_date,destination_id AS account_id,'TRANSFERENCIA' AS origin FROM transfer WHERE user_id=? AND status='REALIZADO' AND effective_date BETWEEN ? AND ? AND destination_id=?",
              user,
              from,
              to,
              account));
      rows.addAll(
          repository.query(
              "SELECT id,'Transferência enviada' AS description,'DESPESA' AS kind,amount,effective_date AS report_date,source_id AS account_id,'TRANSFERENCIA' AS origin FROM transfer WHERE user_id=? AND status='REALIZADO' AND effective_date BETWEEN ? AND ? AND source_id=?",
              user,
              from,
              to,
              account));
    }
    rows.sort(Comparator.comparing(row -> str(row, "report_date")));
    return rows;
  }

  public List<Map<String, Object>> audit(String user) {
    return repository.query(
        "SELECT * FROM audit WHERE user_id=? ORDER BY created_at DESC,id LIMIT 100", user);
  }

  public Map<String, Long> totals(List<Map<String, Object>> rows) {
    long income = 0, expense = 0;
    for (var row : rows) {
      if (str(row, "kind").equals("RECEITA")) income = Math.addExact(income, number(row, "amount"));
      else expense = Math.addExact(expense, number(row, "amount"));
    }
    return Map.of("income", income, "expense", expense, "result", income - expense);
  }

  public List<Map<String, Object>> installments(String user, String invoice) {
    finance.get("invoice", user, invoice);
    return repository.query(
        "SELECT p.*,c.description,c.installments,c.status FROM installment p JOIN purchase c ON c.id=p.purchase_id WHERE p.user_id=? AND p.invoice_id=? ORDER BY c.purchase_date,p.number",
        user,
        invoice);
  }
}
