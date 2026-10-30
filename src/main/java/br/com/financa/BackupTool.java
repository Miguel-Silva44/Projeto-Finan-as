package br.com.financa;

import br.com.financa.model.BackupCrypto;
import java.nio.file.*;

/** Offline decryption. The output is sensitive and must be removed after restoration. */
public class BackupTool {
  public static void main(String[] args) throws Exception {
    if (args.length != 2)
      throw new IllegalArgumentException("Uso: BackupTool arquivo.fin novo-arquivo.sql");
    String password = System.getenv("BACKUP_PASSWORD");
    if (password == null) throw new IllegalArgumentException("Defina BACKUP_PASSWORD.");
    byte[] plain =
        BackupCrypto.decrypt(Files.readAllBytes(Path.of(args[0])), password.toCharArray());
    Files.write(Path.of(args[1]), plain, StandardOpenOption.CREATE_NEW);
    System.out.println("Snapshot restaurado no arquivo indicado. Trate-o como dado sensível.");
  }
}
