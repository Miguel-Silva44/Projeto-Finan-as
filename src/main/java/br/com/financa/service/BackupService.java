package br.com.financa.service;

import br.com.financa.model.BackupCrypto;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BackupService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(BackupService.class);
  private final JdbcTemplate jdbc;
  private final String password;

  public BackupService(JdbcTemplate jdbc, @Value("${app.backup-password:}") String password) {
    this.jdbc = jdbc;
    this.password = password;
  }

  public Path create(Path directory, char[] secret) throws Exception {
    try (var connection = jdbc.getDataSource().getConnection()) {
      if (!connection.getMetaData().getDatabaseProductName().equals("H2"))
        throw new IllegalStateException(
            "Use pg_dump e criptografia gerenciada para backups PostgreSQL.");
      Files.createDirectories(directory);
      Path snapshot = Files.createTempFile(directory, "snapshot-", ".sql");
      try {
        try (var statement = connection.createStatement()) {
          statement.execute(
              "SCRIPT TO '" + snapshot.toAbsolutePath().toString().replace("'", "''") + "'");
        }
        byte[] encrypted = BackupCrypto.encrypt(Files.readAllBytes(snapshot), secret);
        Path output =
            directory.resolve(
                "financas-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ".fin");
        Files.write(output, encrypted, StandardOpenOption.CREATE_NEW);
        return output;
      } finally {
        Files.deleteIfExists(snapshot);
      }
    }
  }

  @org.springframework.beans.factory.annotation.Value("${app.backup-enabled:true}")
  private boolean enabled = true;

  @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
  public void daily() {
    if (!enabled) return;
    if (password.length() < 16) {
      LOG.warn(
          "Backup automático não configurado: defina BACKUP_PASSWORD com pelo menos 16 caracteres.");
      return;
    }
    try {
      create(Path.of("data", "backups"), password.toCharArray());
      LOG.info("Backup diário criptografado concluído.");
    } catch (Exception e) {
      LOG.error("Falha no backup diário. Tipo: {}", e.getClass().getSimpleName());
    }
  }
}
