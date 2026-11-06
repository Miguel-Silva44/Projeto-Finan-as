# PostgreSQL e Docker

O ambiente preserva PostgreSQL em volume nomeado e mantém H2 disponível para execução legada. O banco H2 existente não é removido nem importado automaticamente. A versão PostgreSQL é 18.6-bookworm, fixada por digest em `config/images.env` e no Compose.

## Preparar Windows

Na raiz do projeto, PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-environment.ps1 -ReportPath data/environment-check.json
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-dev-environment.ps1 -Install
```

O diagnóstico não instala nada. A preparação só instala componentes ausentes; pode precisar de PowerShell elevado para WSL ou confirmação do instalador Docker. Não aceita licenças nem reinicia automaticamente. Se houver solicitação de reboot, salve o trabalho, reinicie manualmente e repita. `ExecutionPolicy Bypass` nesses comandos vale somente para o processo lançado, sem alteração global do Windows.

Depois da instalação/primeiro uso do Docker:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-dev-environment.ps1 -StartDocker -PullImages
```

O Docker Desktop inclui engine Linux e Compose. Não é necessário instalar PostgreSQL, pgAdmin, psql ou servidor Linux à parte. O Docker precisa continuar aberto para executar os containers.

## Criar e iniciar desenvolvimento

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/new-environment.ps1 -Environment dev
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-environment.ps1 -Environment dev
```

`new-environment` é executado uma vez. Ele recusa sobrescrever `.env.dev` ou segredos existentes. Se o ambiente já existe, execute somente `start-environment`. Senhas administrativas, de migração, runtime e backup são aleatórias, diferentes e gravadas em `data/secrets/dev`, ignoradas pelo Git. Não apague esses arquivos ao reiniciar: trocar arquivos não troca automaticamente senhas em um banco existente.

Abra `http://localhost:8080`; login dev: `dev@financas.local` / `DevFinanceiro123!`. O perfil dev está isolado do H2 legado e de outros projetos Compose. O primeiro build baixa as imagens e dependências Maven e pode demorar. Para usar outra porta, informe `-AppPort 8081` ao criar um ambiente novo, ou edite `APP_PORT` e `BASE_URL` do arquivo existente sem mudar as credenciais.

Volumes são `financas-dev_postgres_data` e `financas-dev_dev_mail`. `scripts/stop-environment.ps1 -Environment dev` executa `down` preservando os volumes. Não use opções que removem volumes para parar o app. O script de infraestrutura pode recriar containers para conferir persistência:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-infrastructure.ps1 -Environment dev -RecreateContainers
```

Ele usa uma tabela de prova separada, sem alterar registros financeiros, e remove somente a linha de prova ao terminar.

## Executar Java na IDE com o banco no Docker

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-environment.ps1 -Environment dev -DatabaseOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/run-local-postgres.ps1 -Environment dev
```

Se o container app já estiver rodando, pare somente esse serviço antes de usar a mesma porta na IDE. O script local configura as variáveis Spring no processo Maven e lê senhas dos arquivos sem exibi-las.

Para configuração manual do IntelliJ: use Java 21, raiz do projeto como Working directory, perfil `dev,postgres` e `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/financas`, `SPRING_DATASOURCE_USERNAME=financas_app`, `SPRING_FLYWAY_USER=financas_migrator`. Carregue `SPRING_DATASOURCE_PASSWORD` e `SPRING_FLYWAY_PASSWORD` dos arquivos de segredo na configuração local não versionada. Os nomes Spring têm precedência sobre os defaults H2 do perfil dev.

## Papéis, migrações e saúde

- `financas_admin`: inicialização, backup e recuperação; não é o usuário da aplicação.
- `financas_migrator`: proprietário do schema e executor Flyway.
- `financas_app`: leitura/escrita dos dados de negócio, sem superusuário, CREATE DATABASE/ROLE ou CREATE no schema.

Migrações comuns V1–V4 permanecem inalteradas. As migrações PostgreSQL V5–V7 protegem o histórico Flyway, criam o manifesto de importação e aplicam permissões de execução. Cada startup valida a cadeia; falha de conexão/schema impede prontidão. O endpoint `/health/ready` informa somente `ready`/`unavailable`, sem detalhes de banco ou credenciais. O perfil PostgreSQL não cai silenciosamente em H2.

## Backups

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/backup-postgres.ps1 -Environment dev -Mode once
```

O job usa `pg_dump -Fc`, criptografia OpenPGP AES-256, senha em arquivo, arquivo `.partial` até a conclusão e SHA-256 do arquivo final. O fluxo binário não passa pelo redirecionamento textual do PowerShell e não é carregado inteiro em memória. É diferente do formato `.fin` usado pelo backup H2.

Backups dev ficam em `data/backups/dev`. Para execução diária independente do processo Java, inicie com `start-environment.ps1 -Environment dev -WithBackup`. O job registra `last-success` e possui healthcheck; falhas são repetidas após cinco minutos. A retenção aceita de 1 a 90 dias e remove somente arquivos desse job. Configure armazenamento em disco/serviço independente antes de produção; o diretório dev no mesmo PC não protege contra perda do PC.

Verificar e restaurar, substituindo o nome pelo arquivo gerado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/backup-postgres.ps1 -Environment dev -Mode verify -File financas-IDENTIFICADOR.dump.gpg
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/backup-postgres.ps1 -Environment dev -Mode restore -File financas-IDENTIFICADOR.dump.gpg -RestoreDatabase financas_restore_ensaio
```

O restore exige nome novo iniciado por `financas_restore_`; não apaga nem substitui banco existente. Confere checksum, autenticação do arquivo, restaura em transação e reaplica proteção do histórico Flyway. Após restaurar, comparar usuários, operações, vínculos, auditoria, saldos e faturas antes de configurar a aplicação para o novo banco. Guardar a senha de backup separadamente; sem ela não há descriptografia.

O container de backup usa a identidade administrativa necessária à recuperação e root do container por padrão para compatibilidade com permissões de bind mounts. Em host Linux com diretório preparado, definir um usuário de container correspondente ao dono do diretório. Não há montagem de socket Docker, modo privilegiado ou diretórios gerais do host.

## Migrar H2 existente

Pare escritas na origem e no destino, gere backup e confira que o PostgreSQL não tem dados de negócio. Não inicie o app dev antes de importar para um destino novo: o inicializador cria uma conta e deixa de ser um destino vazio.

```powershell
mvn.cmd -DskipTests package
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-environment.ps1 -Environment dev -DatabaseOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/import-h2-postgres.ps1 -SourceJdbcUrl 'jdbc:h2:file:C:/CAMINHO/data/financas;MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE;ACCESS_MODE_DATA=r' -Environment dev -WritesStopped
```

Use `-AllowDevelopment` somente se a origem tiver contas dev e o destino for dev. O script nunca importa contas dev para hml/prod. A importação usa o schema PostgreSQL criado pelo Flyway, copia IDs, hashes, versões, centavos e auditoria e compara fingerprints completos do conteúdo antes de confirmar. Tokens de acesso/recuperação não são copiados; sessões devem ser refeitas. Resultados ficam em `data/import-dev-report.txt`, sem credenciais. Repetição só é aceita quando origem e destino ainda correspondem ao mesmo manifesto; destino alterado não recebe nova carga.

Para ambiente sem porta publicada (hml/prod), execute `br.com.financa.tools.H2PostgresImporter` em host administrativo/rede autorizada, usando `IMPORT_SOURCE_URL`, `IMPORT_SOURCE_USER`, `IMPORT_SOURCE_PASSWORD` quando necessário, `IMPORT_TARGET_URL`, `IMPORT_TARGET_USER`, `IMPORT_TARGET_PASSWORD_FILE` e `IMPORT_REPORT`. O utilitário também é carregável pelo PropertiesLauncher do JAR, como no script. Não expor a porta de produção para facilitar importação.

## Homologação e produção

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/new-environment.ps1 -Environment hml -AppPort 8081
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/new-environment.ps1 -Environment prod -BaseUrl https://financas.seudominio.com -BackupDirectory D:/BACKUP-INDEPENDENTE/financas
```

Projetos/volumes são separados por ambiente. Hml não cria conta dev; produção força perfil `prod,postgres`, SMTP, cache e cookies seguros. Configure SMTP, segredos SMTP e proxy HTTPS no host escolhido antes de iniciar produção; essa infraestrutura externa não é provisionada pelos scripts. A porta da aplicação fica em loopback para um proxy confiável. O banco não publica porta em hml/prod. Revise `TRUSTED_PROXY_PATTERN` conforme a rede real do proxy.

## Testes e atualização

```powershell
mvn.cmd verify spotless:check
mvn.cmd -Ppostgres-it verify spotless:check
mvn.cmd -Ppostgres-it verify '-Dit.test=PostgresLoadIT' '-Dfinance.postgres-performance=true'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-scripts.ps1
```

A etapa `postgres-it` usa Docker e falha se ele não estiver disponível. Ela reutiliza os cenários financeiros, autorização, concorrência, rollback e exclusão do app contra PostgreSQL, além de testar permissões runtime, backup/restore e migração H2. O workflow de CI executa essa etapa obrigatoriamente. O teste H2 rápido continua independente de Docker. A última linha habilita a carga opcional: em 06/11/2026, 100 mil lançamentos e 20 sessões HTTP reais tiveram P95 de 893 ms; relatório em `target/postgres-http-performance.txt`. O resultado depende do ambiente.

Antes de atualização: backup verificado, testes e revisão de migrações. Recrie os containers com o mesmo projeto e volume. Atualização de major PostgreSQL exige procedimento de migração (`pg_upgrade` ou dump/restore), não apenas mudar a tag. Nunca use `latest`. Os digests consultados estão registrados em `config/images.env`; atualizar exige revalidar a matriz.
