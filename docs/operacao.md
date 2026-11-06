# Operação local e preparação para produção

Para PostgreSQL em Docker, siga o [guia de instalação, execução, IntelliJ, backup e restauração](postgresql-docker.md). Os procedimentos H2 abaixo se aplicam ao modo local sem o perfil `postgres`.

## Backup H2

Defina `BACKUP_PASSWORD` com pelo menos 16 caracteres. O serviço gera diariamente, às 03h UTC, um snapshot consistente H2 criptografado em `data/backups/*.fin`. O formato usa AES-256-GCM autenticado, salt aleatório e chave derivada com PBKDF2-HMAC-SHA256, 600 mil iterações. O snapshot SQL temporário é removido após criptografar. Restrinja acesso ao diretório `data`, inclusive aos temporários.

Faça cópia dos arquivos criptografados para armazenamento independente. Guarde a senha separadamente. Agendamento depende da aplicação em execução; monitorar o log de conclusão/falha. Não há configuração de armazenamento remoto neste repositório. A retenção máxima de 90 dias e a meta de perda de até 24 horas precisam ser aplicadas e monitoradas pelo operador.

## Restaurar um backup H2

1. Pare a aplicação para não misturar registros novos com o snapshot. Preserve o banco anterior para recuperação.
2. Compile: `mvn.cmd compile`.
3. Defina a mesma variável `BACKUP_PASSWORD` usada no backup.
4. Descriptografe em arquivo novo:

```powershell
java -cp target/classes br.com.financa.BackupTool data/backups/arquivo.fin data/restauracao.sql
```

5. Com o driver H2 resolvido pelo Maven, aplique `RUNSCRIPT FROM 'caminho-absoluto/data/restauracao.sql'` usando `org.h2.tools.RunScript` em um **novo banco**. Use a mesma URL de configuração, trocando o nome final para `financas-restauradas`. Nunca sobreponha um banco em uso. Exemplo, após obter o classpath:

```powershell
mvn.cmd dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt'
$taskClasspath = (Get-Content target/classpath.txt -Raw).Trim()
java -cp $taskClasspath org.h2.tools.RunScript -url 'jdbc:h2:file:./data/financas-restauradas;MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE' -user sa -script data/restauracao.sql
```

6. Configure `DATABASE_URL` apontando para o novo banco e inicie a aplicação. Flyway valida as migrações já presentes no snapshot.
7. Confira usuários, quantidade de operações, vínculos, saldos e relatórios. O teste automatizado `BackupIntegrationTest` verifica criptografia, rejeição de senha errada e restauração em outro banco. Um ensaio completo com dados e volume de produção continua obrigatório para validar a meta de 8 horas.
8. Remova com segurança o SQL descriptografado depois da validação. Restrinja acesso ao banco preservado e aplique a política de retenção.

Para PostgreSQL, use `pg_dump` e o mecanismo de criptografia/retencão do ambiente. O serviço de snapshot deste projeto é específico para H2 e recusa outros bancos.

## Deploy e rollback

Antes do deploy: `mvn.cmd test spotless:check package`; gerar backup; verificar SMTP com endereço de teste autorizado, HTTPS e cookies seguros. Em produção usar `MAIL_MODE=smtp`, `COOKIE_SECURE=true` e credenciais fora do repositório. Desative cache de templates apenas no desenvolvimento; em produção sobrescreva `spring.thymeleaf.cache=true`.

Após deploy, valide login, uma consulta por titular, totais e logs de tarefas. Não executar scripts de demonstração em produção. Reverter o JAR só é seguro se compatível com o schema atual. Migrações destrutivas exigem plano próprio; restaurar backup pode perder alterações posteriores e não é rollback automático.

## Exclusão e retenção

Solicitar exclusão desativa a conta e revoga sessões imediatamente. A rotina horária remove fisicamente usuários desativados e seus registros por cascata, cumprindo o prazo proposto de até 30 dias enquanto o serviço estiver ativo. Monitorar falhas de exclusão. Backups antigos não são reescritos: expiram conforme a retenção do armazenamento. Os prazos devem ser formalizados antes da produção.

## Homologações externas pendentes

SMTP real, PostgreSQL em ambiente de implantação, navegadores/aparelhos reais (Android, iOS, Edge, Firefox e Safari), teste com leitor de tela e infraestrutura de backup remoto ainda dependem do ambiente operacional. Não interpretar teste de WebKit em Windows como execução em um iPhone real.
