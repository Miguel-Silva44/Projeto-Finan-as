package br.com.financa.controller;

import br.com.financa.service.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class FinanceController {
  private final IdentityService identity;
  private final FinanceService finance;
  private final CardService cards;
  private final PlanningService planning;
  private final ReportService reports;

  public FinanceController(
      IdentityService identity,
      FinanceService finance,
      CardService cards,
      PlanningService planning,
      ReportService reports) {
    this.identity = identity;
    this.finance = finance;
    this.cards = cards;
    this.planning = planning;
    this.reports = reports;
  }

  private String user(Principal principal) {
    return identity.id(principal.getName());
  }

  @ModelAttribute
  public void shared(Principal principal, Model model) {
    model.addAttribute("emptyMap", Map.of());
    model.addAttribute("editType", "");
    if (principal != null) {
      String user = user(principal);
      model.addAttribute("profile", identity.profile(principal.getName()));
      model.addAttribute("today", finance.today(user));
      model.addAttribute("accounts", reports.accounts(user));
      model.addAttribute("categories", finance.list("category", user));
      model.addAttribute("cards", reports.cards(user));
    }
  }

  @GetMapping("/")
  public String home() {
    return "redirect:/dashboard";
  }

  @GetMapping({
    "/dashboard",
    "/transactions",
    "/accounts",
    "/categories",
    "/cards",
    "/budgets",
    "/recurrences",
    "/reports",
    "/settings",
    "/audit"
  })
  public String page(
      jakarta.servlet.http.HttpServletRequest request,
      @RequestParam Map<String, String> query,
      Principal principal,
      Model model) {
    String user = user(principal);
    String page = request.getRequestURI().substring(1);
    LocalDate today = finance.today(user);
    YearMonth month =
        br.com.financa.model.FinancialRules.month(
            query.getOrDefault("month", YearMonth.from(today).toString()));
    model.addAttribute("page", page);
    model.addAttribute("month", month);
    model.addAttribute("query", query);
    model.addAttribute("edit", Map.of());
    model.addAttribute("preview", List.of());
    switch (page) {
      case "dashboard" -> {
        LocalDate horizon =
            query.containsKey("horizon")
                ? br.com.financa.model.FinancialRules.date(query.get("horizon"))
                : today.plusDays(30);
        model.addAttribute("summary", reports.dashboard(user, month, horizon));
        model.addAttribute("horizon", horizon);
      }
      case "transactions" -> {
        var rows = reports.entries(user, query, true);
        model.addAttribute("hasNext", rows.size() > 30);
        model.addAttribute("entries", rows.stream().limit(30).toList());
        model.addAttribute("transfers", reports.transfers(user));
        model.addAttribute("currentPage", Math.max(1, parsePage(query.getOrDefault("page", "1"))));
      }
      case "cards" -> {
        model.addAttribute("invoices", reports.invoices(user));
        model.addAttribute("purchases", reports.purchases(user));
        if (query.containsKey("invoice"))
          model.addAttribute("invoiceParts", reports.installments(user, query.get("invoice")));
      }
      case "budgets" -> model.addAttribute("breakdown", reports.breakdown(user, month));
      case "recurrences" -> model.addAttribute("recurrences", finance.list("recurrence", user));
      case "audit" -> model.addAttribute("history", reports.audit(user));
      case "reports" -> {
        LocalDate
            from =
                br.com.financa.model.FinancialRules.date(
                    query.getOrDefault("from", month.atDay(1).toString())),
            to =
                br.com.financa.model.FinancialRules.date(
                    query.getOrDefault("to", month.atEndOfMonth().toString()));
        var rows =
            query.getOrDefault("basis", "competence").equals("cash")
                ? reports.cashflow(user, from, to, query.getOrDefault("account_id", ""))
                : reports.competence(
                    user,
                    from,
                    to,
                    query.getOrDefault("category_id", ""),
                    query.getOrDefault("account_id", ""));
        model.addAttribute("reportRows", rows);
        model.addAttribute("reportTotals", reports.totals(rows));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
      }
      default -> {}
    }
    if (query.containsKey("edit")) {
      String table =
          switch (page) {
            case "accounts" -> "account";
            case "categories" -> "category";
            case "transactions" -> "entry";
            case "cards" ->
                query.getOrDefault("type", "card").equals("purchase") ? "purchase" : "card";
            case "recurrences" -> "recurrence";
            default -> throw new br.com.financa.model.DomainException("Edição indisponível.");
          };
      model.addAttribute("edit", finance.get(table, user, query.get("edit")));
      model.addAttribute("editType", table);
    }
    return "app";
  }

  private int parsePage(String value) {
    try {
      return Math.min(100000, Integer.parseInt(value));
    } catch (NumberFormatException e) {
      return 1;
    }
  }

  private String success(String target, RedirectAttributes flash) {
    flash.addFlashAttribute("message", "Operação salva com sucesso.");
    return "redirect:/" + target;
  }

  @PostMapping("/accounts")
  public String account(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    finance.saveAccount(user(principal), form);
    return success("accounts", flash);
  }

  @PostMapping("/categories")
  public String category(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    finance.saveCategory(user(principal), form);
    return success("categories", flash);
  }

  @PostMapping("/transactions")
  public String entry(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    finance.saveEntry(user(principal), form);
    return success("transactions", flash);
  }

  @PostMapping("/transactions/{id}/{action}")
  public String entryAction(
      @PathVariable String id,
      @PathVariable String action,
      @RequestParam Map<String, String> form,
      Principal principal,
      RedirectAttributes flash) {
    finance.entryAction(user(principal), id, action, form);
    return success("transactions", flash);
  }

  @PostMapping("/transfers")
  public String transfer(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    finance.transfer(user(principal), form);
    return success("transactions", flash);
  }

  @PostMapping("/transfers/{id}/cancel")
  public String cancelTransfer(
      @PathVariable String id,
      @RequestParam Map<String, String> form,
      Principal principal,
      RedirectAttributes flash) {
    finance.cancelTransfer(user(principal), id, form);
    return success("transactions", flash);
  }

  @PostMapping("/{table:account|category|card}/{id}/archive")
  public String archive(
      @PathVariable String table,
      @PathVariable String id,
      @RequestParam Map<String, String> form,
      Principal principal,
      RedirectAttributes flash) {
    finance.archive(user(principal), table, id, form);
    return success(
        switch (table) {
          case "account" -> "accounts";
          case "category" -> "categories";
          default -> "cards";
        },
        flash);
  }

  @PostMapping("/cards")
  public String card(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    cards.save(user(principal), form);
    return success("cards", flash);
  }

  @PostMapping("/purchases/preview")
  public String preview(@RequestParam Map<String, String> form, Principal principal, Model model) {
    model.addAttribute("page", "cards");
    model.addAttribute("month", YearMonth.from(finance.today(user(principal))));
    model.addAttribute("query", Map.of());
    model.addAttribute("edit", form);
    model.addAttribute("editType", "purchase");
    model.addAttribute("preview", cards.preview(user(principal), form));
    model.addAttribute("limitWarning", cards.exceedsLimit(user(principal), form));
    model.addAttribute("purchaseForm", form);
    model.addAttribute("invoices", reports.invoices(user(principal)));
    model.addAttribute("purchases", reports.purchases(user(principal)));
    return "app";
  }

  @PostMapping("/purchases")
  public String purchase(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    cards.purchase(user(principal), form);
    return success("cards", flash);
  }

  @PostMapping("/purchases/{id}/cancel")
  public String cancelPurchase(
      @PathVariable String id,
      @RequestParam Map<String, String> form,
      Principal principal,
      RedirectAttributes flash) {
    cards.cancelPurchase(user(principal), id, form);
    return success("cards", flash);
  }

  @PostMapping("/invoices/{id}/{action}")
  public String invoiceAction(
      @PathVariable String id,
      @PathVariable String action,
      @RequestParam Map<String, String> form,
      Principal principal,
      RedirectAttributes flash) {
    cards.invoiceAction(user(principal), id, action, form);
    return success("cards", flash);
  }

  @PostMapping("/recurrences")
  public String recurrence(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    planning.saveRecurrence(user(principal), form);
    return success("recurrences", flash);
  }

  @PostMapping("/recurrences/{id}/stop")
  public String stop(
      @PathVariable String id,
      @RequestParam Map<String, String> form,
      Principal principal,
      RedirectAttributes flash) {
    planning.stop(user(principal), id, form);
    return success("recurrences", flash);
  }

  @PostMapping("/budgets")
  public String budget(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    planning.budget(user(principal), form);
    return success("budgets?month=" + form.get("month"), flash);
  }

  @PostMapping("/budgets/copy")
  public String copy(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    planning.copyBudget(user(principal), form);
    return success("budgets?month=" + form.get("month"), flash);
  }

  @PostMapping("/me")
  public String profile(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    identity.update(principal.getName(), form);
    return success("settings", flash);
  }

  @PostMapping("/me/password")
  public String password(
      @RequestParam Map<String, String> form, Principal principal, RedirectAttributes flash) {
    identity.changePassword(principal.getName(), form);
    return "redirect:/auth/login?expired";
  }

  @PostMapping("/me/delete")
  public String delete(
      @RequestParam Map<String, String> form,
      Principal principal,
      jakarta.servlet.http.HttpServletRequest request) {
    identity.delete(principal.getName(), form);
    request.getSession().invalidate();
    return "redirect:/auth/login?deleted";
  }
}
