package br.com.financa;

import static org.assertj.core.api.Assertions.*;

import br.com.financa.model.BackupCrypto;
import br.com.financa.service.BackupService;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class BackupIntegrationTest {
  @TempDir Path directory;

  @Test
  void encryptedSnapshotRestoresRecordsAndRejectsWrongPassword() throws Exception {
    char[] password = "Backup seguro de teste 123!".toCharArray();
    try (var original =
            DriverManager.getConnection(
                "jdbc:h2:mem:backupSource;DATABASE_TO_LOWER=TRUE", "sa", "");
        var restored =
            DriverManager.getConnection(
                "jdbc:h2:mem:backupRestored;DATABASE_TO_LOWER=TRUE", "sa", "")) {
      JdbcTemplate source = new JdbcTemplate(new SingleConnectionDataSource(original, true));
      source.execute("CREATE TABLE movements(id INT PRIMARY KEY,amount BIGINT)");
      source.update("INSERT INTO movements VALUES(1,100000),(2,-3334)");
      Path backup = new BackupService(source, "").create(directory, password);
      byte[] encrypted = Files.readAllBytes(backup);
      assertThat(new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1))
          .doesNotContain("CREATE TABLE");
      assertThatThrownBy(
              () -> BackupCrypto.decrypt(encrypted, "Senha errada de teste 123".toCharArray()))
          .isInstanceOf(Exception.class);
      Path script = directory.resolve("restore.sql");
      Files.write(script, BackupCrypto.decrypt(encrypted, password));
      JdbcTemplate target = new JdbcTemplate(new SingleConnectionDataSource(restored, true));
      target.execute("RUNSCRIPT FROM '" + script.toString().replace("'", "''") + "'");
      assertThat(target.queryForObject("SELECT SUM(amount) FROM movements", Long.class))
          .isEqualTo(96666);
      assertThat(directory.toFile().listFiles((dir, name) -> name.startsWith("snapshot-")))
          .isEmpty();
    }
  }
}
