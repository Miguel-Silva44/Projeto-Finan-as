package br.com.financa.controller;

import br.com.financa.service.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class ExportController {
  private final IdentityService identity;
  private final ReportService reports;
  private final ExportService exports;
  private final FinanceService finance;

  public ExportController(
      IdentityService identity,
      ReportService reports,
      ExportService exports,
      FinanceService finance) {
    this.identity = identity;
    this.reports = reports;
    this.exports = exports;
    this.finance = finance;
  }

  @GetMapping("/exports/transactions")
  public ResponseEntity<byte[]> entries(
      @RequestParam Map<String, String> filters, Principal principal) {
    return file(
        exports.csv(reports.entries(identity.id(principal.getName()), filters, false)),
        "lancamentos.csv",
        "text/csv;charset=UTF-8");
  }

  @GetMapping("/exports/report")
  public ResponseEntity<byte[]> report(
      @RequestParam Map<String, String> filters, Principal principal) {
    String user = identity.id(principal.getName());
    LocalDate today = finance.today(user),
        from =
            br.com.financa.model.FinancialRules.date(
                filters.getOrDefault("from", today.withDayOfMonth(1).toString())),
        to =
            br.com.financa.model.FinancialRules.date(
                filters.getOrDefault("to", today.withDayOfMonth(today.lengthOfMonth()).toString()));
    return file(
        exports.csv(
            filters.getOrDefault("basis", "competence").equals("cash")
                ? reports.cashflow(user, from, to, filters.getOrDefault("account_id", ""))
                : reports.competence(
                    user,
                    from,
                    to,
                    filters.getOrDefault("category_id", ""),
                    filters.getOrDefault("account_id", ""))),
        "relatorio.csv",
        "text/csv;charset=UTF-8");
  }

  @GetMapping("/me/export")
  public ResponseEntity<byte[]> complete(Principal principal) {
    return file(
        exports.complete(identity.id(principal.getName())), "financas.zip", "application/zip");
  }

  private ResponseEntity<byte[]> file(byte[] content, String filename, String type) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .contentType(MediaType.parseMediaType(type))
        .body(content);
  }
}
