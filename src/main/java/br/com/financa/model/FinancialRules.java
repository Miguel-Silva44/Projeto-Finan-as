package br.com.financa.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class FinancialRules {
  public static final long MAX_CENTS = 999_999_999_999L;

  private FinancialRules() {}

  public static long cents(String input, boolean allowNegative) {
    try {
      String normalized = input.strip();
      if (normalized.contains(",")) normalized = normalized.replace(".", "").replace(',', '.');
      if (!normalized.matches("-?\\d+(\\.\\d{1,2})?")) throw new NumberFormatException();
      long value = new BigDecimal(normalized).movePointRight(2).longValueExact();
      require(
          value <= MAX_CENTS && value >= -MAX_CENTS && (allowNegative || value > 0),
          "Valor fora do intervalo permitido.");
      return value;
    } catch (ArithmeticException | NumberFormatException | NullPointerException e) {
      throw new DomainException("Informe um valor válido com até duas casas decimais.");
    }
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new DomainException(message);
  }

  public static String text(String value, int max) {
    require(
        value != null && !value.isBlank() && value.strip().length() <= max,
        "Preencha o texto obrigatório (até " + max + " caracteres).");
    return value.strip();
  }

  public static LocalDate date(String value) {
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException e) {
      throw new DomainException("Informe uma data válida.");
    }
  }

  public static YearMonth month(String value) {
    try {
      return YearMonth.parse(value);
    } catch (RuntimeException e) {
      throw new DomainException("Informe um mês válido.");
    }
  }

  public static LocalDate onDay(YearMonth month, int day) {
    require(day >= 1 && day <= 31, "O dia deve estar entre 1 e 31.");
    return month.atDay(Math.min(day, month.lengthOfMonth()));
  }

  public static YearMonth invoiceMonth(LocalDate purchase, int closingDay) {
    YearMonth month = YearMonth.from(purchase);
    return purchase.isBefore(onDay(month, closingDay)) ? month : month.plusMonths(1);
  }

  public static List<Long> split(long amount, int count) {
    require(
        count >= 1 && count <= 120 && amount >= count && amount <= MAX_CENTS,
        "Use de 1 a 120 parcelas, sem parcela de valor zero.");
    List<Long> parts = new ArrayList<>();
    for (int i = 0; i < count; i++) parts.add(amount / count + (i < amount % count ? 1 : 0));
    return List.copyOf(parts);
  }
}
