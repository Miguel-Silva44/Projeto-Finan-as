package br.com.financa.service;

import br.com.financa.repository.FinanceRepository;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
  private final FinanceRepository repository;

  public ExportService(FinanceRepository repository) {
    this.repository = repository;
  }

  public static String cell(Object value) {
    String text = value == null ? "" : value.toString();
    if (!(value instanceof Number) && text.stripLeading().matches("(?s)^[=+@\\-\\t\\r].*"))
      text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  public byte[] csv(List<Map<String, Object>> rows) {
    StringBuilder text = new StringBuilder("\uFEFF");
    if (rows.isEmpty()) return (text + "Sem dados no período\r\n").getBytes(StandardCharsets.UTF_8);
    var keys = rows.getFirst().keySet();
    text.append(String.join(";", keys.stream().map(ExportService::cell).toList())).append("\r\n");
    for (var row : rows)
      text.append(String.join(";", keys.stream().map(key -> cell(row.get(key))).toList()))
          .append("\r\n");
    return text.toString().getBytes(StandardCharsets.UTF_8);
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public byte[] complete(String user) {
    try (var bytes = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (String table :
          List.of(
              "account",
              "category",
              "entry",
              "transfer",
              "recurrence",
              "card",
              "purchase",
              "installment",
              "invoice",
              "invoice_payment",
              "budget",
              "audit")) {
        zip.putNextEntry(new ZipEntry(table + ".csv"));
        zip.write(csv(repository.all(table, user)));
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry("LEIA-ME.txt"));
      zip.write(
          "Valores monetários são inteiros em centavos. Datas civis: YYYY-MM-DD. Separador CSV: ponto e vírgula; codificação UTF-8. Transferências e pagamentos não são novas despesas por competência. Descrições iniciadas por fórmulas recebem apóstrofo de proteção.\n"
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      zip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Falha ao gerar exportação.", e);
    }
  }
}
