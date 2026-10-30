package br.com.financa;

import static org.assertj.core.api.Assertions.*;

import br.com.financa.model.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class FinancialRulesTest {
  @Test
  void csvProtectsMultilineFormulasAndPreservesNumericValues() {
    assertThat(br.com.financa.service.ExportService.cell("=HYPERLINK(1)\nextra")).startsWith("\"'");
    assertThat(br.com.financa.service.ExportService.cell(-2050L)).isEqualTo("\"-2050\"");
  }

  @Test
  void parsesMoneyWithoutFloatingPoint() {
    assertThat(FinancialRules.cents("1.234,56", false)).isEqualTo(123456);
    assertThat(FinancialRules.cents("0.01", false)).isEqualTo(1);
    assertThat(FinancialRules.cents("-20,50", true)).isEqualTo(-2050);
    for (String bad : new String[] {"0", "1.001", "1,001", "1e3", "NaN", "999999999999.99"})
      assertThatThrownBy(() -> FinancialRules.cents(bad, false))
          .isInstanceOf(DomainException.class);
  }

  @Test
  void preservesEveryCent() {
    assertThat(FinancialRules.split(10000, 3)).containsExactly(3334L, 3333L, 3333L);
    assertThatThrownBy(() -> FinancialRules.split(2, 3)).isInstanceOf(DomainException.class);
  }

  @Test
  void closingDayBelongsToNextInvoice() {
    assertThat(FinancialRules.invoiceMonth(LocalDate.parse("2026-10-19"), 20))
        .isEqualTo(YearMonth.parse("2026-10"));
    assertThat(FinancialRules.invoiceMonth(LocalDate.parse("2026-10-20"), 20))
        .isEqualTo(YearMonth.parse("2026-11"));
  }

  @Test
  void monthlyAnchorSurvivesFebruary() {
    assertThat(FinancialRules.onDay(YearMonth.parse("2026-02"), 31))
        .isEqualTo(LocalDate.parse("2026-02-28"));
    assertThat(FinancialRules.onDay(YearMonth.parse("2026-03"), 31))
        .isEqualTo(LocalDate.parse("2026-03-31"));
  }
}
