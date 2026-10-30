package br.com.financa.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
  private final JdbcTemplate jdbc;

  public IdentityRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Map<String, Object>> email(String email) {
    return jdbc
        .query(
            "SELECT * FROM app_user WHERE email=? AND deleted_at IS NULL",
            FinanceRepository::row,
            email)
        .stream()
        .findFirst();
  }

  public Optional<Map<String, Object>> includingDeleted(String email) {
    return jdbc
        .query("SELECT * FROM app_user WHERE email=?", FinanceRepository::row, email)
        .stream()
        .findFirst();
  }

  public void markDevelopment(String id) {
    jdbc.update("UPDATE app_user SET development_only=TRUE,verified=TRUE WHERE id=?", id);
  }

  public Map<String, Object> user(String id) {
    return jdbc
        .query(
            "SELECT id,name,email,timezone,verified FROM app_user WHERE id=? AND deleted_at IS NULL",
            FinanceRepository::row,
            id)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public void insert(String id, String name, String email, String hash) {
    jdbc.update(
        "INSERT INTO app_user(id,name,email,password_hash) VALUES(?,?,?,?)", id, name, email, hash);
  }

  public void token(String hash, String user, String purpose, java.time.Instant expiry) {
    jdbc.update("DELETE FROM access_token WHERE user_id=? AND purpose=?", user, purpose);
    jdbc.update(
        "INSERT INTO access_token(hash,user_id,purpose,expires_at) VALUES(?,?,?,?)",
        hash,
        user,
        purpose,
        java.time.OffsetDateTime.ofInstant(expiry, java.time.ZoneOffset.UTC));
  }

  public Optional<Map<String, Object>> consume(String hash, String purpose, java.time.Instant now) {
    var records =
        jdbc.query(
            "SELECT * FROM access_token WHERE hash=? AND purpose=? AND expires_at>? FOR UPDATE",
            FinanceRepository::row,
            hash,
            purpose,
            java.time.OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
    if (records.isEmpty()) return Optional.empty();
    jdbc.update("DELETE FROM access_token WHERE hash=?", hash);
    return records.stream().findFirst();
  }

  public void verify(String id) {
    jdbc.update("UPDATE app_user SET verified=TRUE WHERE id=? AND deleted_at IS NULL", id);
  }

  public void password(String id, String hash) {
    jdbc.update("UPDATE app_user SET password_hash=? WHERE id=? AND deleted_at IS NULL", hash, id);
  }

  public void profile(String id, String name, String zone) {
    jdbc.update(
        "UPDATE app_user SET name=?,timezone=? WHERE id=? AND deleted_at IS NULL", name, zone, id);
  }

  public void requestDeletion(String id) {
    jdbc.update("UPDATE app_user SET deleted_at=CURRENT_TIMESTAMP WHERE id=?", id);
    jdbc.update("DELETE FROM access_token WHERE user_id=?", id);
  }

  @org.springframework.transaction.annotation.Transactional
  public void purge() {
    for (String user :
        jdbc.queryForList(
            "SELECT id FROM app_user WHERE deleted_at IS NOT NULL FOR UPDATE", String.class)) {
      // Delete dependants first: composite owner FKs intentionally restrict cross-record deletion.
      for (String table :
          List.of(
              "audit",
              "idempotency",
              "invoice_payment",
              "installment",
              "purchase",
              "invoice",
              "entry",
              "transfer",
              "recurrence",
              "budget",
              "card",
              "category",
              "account",
              "access_token")) {
        jdbc.update("DELETE FROM " + table + " WHERE user_id=?", user);
      }
      jdbc.update("DELETE FROM app_user WHERE id=? AND deleted_at IS NOT NULL", user);
    }
  }

  public List<String> activeIds() {
    return jdbc.queryForList("SELECT id FROM app_user WHERE deleted_at IS NULL", String.class);
  }
}
