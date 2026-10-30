package br.com.financa.controller;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component("fmt")
public class ViewFormat {
  public String money(Object cents) {
    if (cents == null) return "R$ 0,00";
    return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(((Number) cents).longValue(), 2));
  }

  public String decimal(Object cents) {
    if (cents == null) return "";
    return BigDecimal.valueOf(((Number) cents).longValue(), 2).toPlainString();
  }

  public String date(Object date) {
    if (date == null) return "—";
    return LocalDate.parse(date.toString().substring(0, 10))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
  }

  public String key() {
    return UUID.randomUUID().toString();
  }

  public String label(Object value) {
    return Map.ofEntries(
            Map.entry("dashboard", "Visão geral"),
            Map.entry("transactions", "Lançamentos"),
            Map.entry("accounts", "Minhas contas"),
            Map.entry("categories", "Categorias"),
            Map.entry("cards", "Cartões e faturas"),
            Map.entry("budgets", "Orçamento mensal"),
            Map.entry("recurrences", "Recorrências"),
            Map.entry("reports", "Relatórios"),
            Map.entry("settings", "Configurações"),
            Map.entry("audit", "Histórico de alterações"))
        .getOrDefault(String.valueOf(value), String.valueOf(value));
  }
}
