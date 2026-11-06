package br.com.financa.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeonSettingsTest {
  @TempDir Path directory;

  private Map<String, String> values() {
    return new HashMap<>(
        Map.of(
            "DATABASE_URL",
            "postgresql://owner:secret@ep-test-pooler.sa-east-1.aws.neon.tech/neondb?sslmode=require",
            "DATABASE_URL_UNPOOLED",
            "postgresql://owner:secret@ep-test.sa-east-1.aws.neon.tech/neondb?sslmode=require",
            "NEON_APP_PASSWORD",
            "runtime-secret",
            "NEON_MIGRATION_PASSWORD",
            "migration-secret"));
  }

  @Test
  void separatesPoolAndMigrationCredentialsWithVerifiedTls() {
    var values = values();
    var settings = NeonSettings.jdbc(values::get);
    assertThat(settings.get("spring.datasource.url").toString())
        .contains("-pooler.", "sslmode=verify-full")
        .doesNotContain("owner", "secret");
    assertThat(settings.get("spring.flyway.url").toString()).doesNotContain("-pooler.");
    assertThat(settings.get("spring.datasource.username")).isEqualTo("financas_app");
    assertThat(settings.get("spring.datasource.password")).isEqualTo("runtime-secret");
    assertThat(settings.get("spring.flyway.password")).isEqualTo("migration-secret");
  }

  @Test
  void rejectsMixedBranchesAndMissingRuntimeSecret() {
    var values = values();
    values.put(
        "DATABASE_URL_UNPOOLED",
        "postgresql://owner:secret@ep-other.sa-east-1.aws.neon.tech/neondb");
    assertThatThrownBy(() -> NeonSettings.jdbc(values::get)).hasMessageContaining("mesma branch");
    var missing = values();
    missing.remove("NEON_APP_PASSWORD");
    assertThatThrownBy(() -> NeonSettings.jdbc(missing::get))
        .hasMessageContaining("neon:bootstrap");
  }

  @Test
  void readsQuotedDotenvAndPreservesEncodedPassword() throws Exception {
    Path file = directory.resolve(".env.local");
    Files.writeString(
        file,
        "# comment\nexport DATABASE_URL='postgresql://user:a%2Bb%3Ac@ep-test.aws.neon.tech/neondb'\nEMPTY=\n");
    var values = NeonSettings.read(file);
    assertThat(NeonSettings.password(NeonSettings.uri(values.get("DATABASE_URL"))))
        .isEqualTo("a+b:c");
    assertThat(values.get("EMPTY")).isEmpty();
    assertThat(NeonSettings.read(directory.resolve("missing"))).isEmpty();
  }

  @Test
  void rejectsNonNeonUrlsWithoutLeakingInput() {
    assertThatThrownBy(() -> NeonSettings.uri("postgresql://owner:private-password@localhost/db"))
        .hasMessageNotContaining("private-password")
        .hasMessageContaining("URL Neon inválida");
  }
}
