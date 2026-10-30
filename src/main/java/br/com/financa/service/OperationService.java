package br.com.financa.service;

import br.com.financa.model.DomainException;
import br.com.financa.repository.FinanceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationService {
  private final FinanceRepository repository;

  public OperationService(FinanceRepository repository) {
    this.repository = repository;
  }

  public static String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public String run(
      String user, String operation, Map<String, String> form, Supplier<String> action) {
    String key = form.get("key");
    try {
      UUID.fromString(key);
    } catch (RuntimeException e) {
      throw new DomainException("Recarregue o formulário antes de salvar.");
    }
    repository.lockUser(user);
    var body = new TreeMap<>(form);
    body.remove("_csrf");
    StringBuilder canonical = new StringBuilder();
    body.forEach(
        (name, value) ->
            canonical
                .append(name.length())
                .append(':')
                .append(name)
                .append(value.length())
                .append(':')
                .append(value));
    String fingerprint = hash(canonical.toString());
    var previous =
        repository.query(
            "SELECT * FROM idempotency WHERE user_id=? AND operation_key=?", user, key);
    if (!previous.isEmpty()) {
      var row = previous.getFirst();
      if (!fingerprint.equals(row.get("request_hash")) || !operation.equals(row.get("operation")))
        throw new DomainException(
            "CONFLICT", "Este formulário já foi utilizado com outros dados. Recarregue a página.");
      return row.get("result").toString();
    }
    String result = action.get();
    repository.execute(
        "INSERT INTO idempotency(user_id,operation_key,operation,request_hash,result) VALUES(?,?,?,?,?)",
        user,
        key,
        operation,
        fingerprint,
        result);
    return result;
  }

  public void audit(
      String user,
      String entity,
      String id,
      String action,
      Map<String, Object> before,
      Map<String, Object> after,
      String reason) {
    repository.insert(
        "audit",
        user,
        Map.of(
            "entity",
            entity,
            "entity_id",
            id,
            "action",
            action,
            "before_value",
            before.toString(),
            "after_value",
            after.toString(),
            "reason",
            reason == null ? "" : reason));
  }
}
