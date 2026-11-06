# Neon com backend Java MVC

O projeto foi vinculado ao projeto Neon fornecido pelo usuário, branch `production`, somente PostgreSQL. Spring Security, MVC/Thymeleaf, Spring JDBC e Flyway permanecem responsáveis por autenticação, telas, regras e schema. Não foram habilitados Better Auth, Functions, Data API nem outros serviços.

O CLI cria `neon.ts` e baixa `DATABASE_URL` (pooler) e `DATABASE_URL_UNPOOLED` (direta) para `.env.local`. Esse arquivo contém credenciais e é ignorado pelo Git. Não o publique nem envie seu conteúdo no chat. `neon deploy` aplica a política de serviços do Neon; não hospeda o servidor Spring Boot.

## Preparar e executar

Requisitos: JDK 21, Maven 3.9+, Node/npm para CLI e scripts de conveniência. Na raiz:

```powershell
neon deploy
npm run build
npm run neon:bootstrap
npm run start:neon
```

`npm run build` chama `mvn verify spotless:check`; o artefato continua sendo um JAR Java. Não há servidor Node nem driver Postgres JavaScript. O perfil `neon` inclui `postgres`, carrega as variáveis do ambiente ou `.env.local`, converte URLs para JDBC e valida que os endpoints pooled/direct pertencem à mesma branch e banco.

O bootstrap usa a conexão direta de administração apenas para criar `financas_migrator` e `financas_app`, com senhas aleatórias locais e privilégios separados. Não imprime nem redefine senhas existentes; se um papel existir sem a credencial local correspondente, interrompe para recuperação. O usuário runtime não recebe privilégios administrativos ou CREATE no schema; as migrações existentes protegem também o histórico Flyway.

`start:neon` abre **http://localhost:8083**, apenas em loopback. Não habilita login dev nem importa os arquivos H2/PostgreSQL locais. Cadastre uma conta; neste modo local, links de confirmação ficam em arquivos privados em `data/mail`. Para uso público, configure SMTP real e HTTPS. A branch chamada `production` não ativa automaticamente o perfil Spring `prod`.

Para escolher outro arquivo/porta no PowerShell:

```powershell
$env:NEON_ENV_FILE='.env.neon-test.local'
$env:PORT='8084'
$env:BASE_URL='http://localhost:8084'
npm run start:neon
```

Variáveis do processo têm precedência sobre o arquivo. Após testes com outro ambiente, remova essas variáveis do terminal antes de voltar para `production`. O CLI preserva variáveis personalizadas ao atualizar os valores Neon; mantenha o arquivo/segredos certo para cada branch.

## Conexões e TLS

- App: JDBC pelo endpoint `-pooler`, papel `financas_app`, Hikari com máximo de quatro conexões e mínimo ocioso zero.
- Flyway: JDBC direto, papel `financas_migrator`.
- TLS: `sslmode=verify-full` com truststore padrão do JDK; não desativar validação de certificado.
- `.env.local` recebe `NEON_APP_PASSWORD` e `NEON_MIGRATION_PASSWORD` no bootstrap. O app não usa o usuário proprietário no pool runtime.
- Produção hospedada deve fornecer variáveis como segredos do provedor. Não versionar `.env`, senhas, chaves ou dumps. O arquivo local do CLI contém credencial administrativa para bootstrap; não copiá-lo indiscriminadamente para o host do app.

O uso de JDBC preserva as transações e as consultas existentes. A conexão direta para migrações segue a [orientação oficial de pooling Neon](https://neon.com/docs/connect/connection-pooling); a compatibilidade com drivers PostgreSQL está na [documentação de conexão](https://neon.com/docs/connect/connect-from-any-app).

## Verificar

```powershell
npm run neon:verify
```

Confere TLS/JDBC pooled, usuário runtime, sete migrações, ausência de privilégios administrativos e recusa de escrita no histórico Flyway. O teste de permissão tenta atualizar zero linhas e encerra com rollback, preservando os dados.

Para testar telas sem tocar em dados de produção, foi criada `codex-neon-integration`, derivada de `production`, com expiração em 13/11/2026. Variáveis em `.env.neon-test.local`; `.neon` permanece vinculado a `production`. Esse arquivo de teste é local e não deve ser incluído no Git. A expiração da branch de teste exige renovação explícita ou outra branch para ensaios futuros.

O teste de navegador existente pode usar essa instância:

```powershell
$env:BASE_URL='http://localhost:8083'
$env:BROWSER='chromium'
node scripts/browser-check.cjs
```

Requer Playwright/Chromium configurados como nas verificações anteriores. Cria contas de teste, confirma pelo e-mail local, executa operações e solicita exclusão ao final. Não executar contra um ambiente público com dados reais sem isolamento.

## Hospedagem e APK

Neon disponibiliza o banco, não uma URL HTTPS para este servidor Java. O app continua precisando de hospedagem Java/Docker, por exemplo Render, ou de um túnel configurado. Para hospedagem pública, configurar `prod,neon`, `BASE_URL` HTTPS, cookies seguros, SMTP, proxy confiável, segredos de runtime/migração e backups externos. Desativar tarefas locais de snapshot H2 permanece obrigatório.

O APK ainda depende dessa origem HTTPS e de `assetlinks.json`. Vincular o banco ao Neon não transforma o APK de preparação em uma versão conectada. O fluxo Android está em [android.md](android.md).

## Evidências de 06/11/2026

- CLI Neon 9.0.0 instalado; `init` vinculou o projeto inicialmente sem verificação. `neon deploy` concluiu autenticação, retornou `No changes — branch production already matches the policy`, confirmou somente Postgres e baixou as variáveis.
- `npm run build` terminou com código 0 e `BUILD SUCCESS`: 35 testes, 0 falhas, 0 erros e 1 teste de carga opcional ignorado; 0 violações Checkstyle e Spotless aprovado. O primeiro build falhou por uma captura de variável no teste novo; corrigido antes das verificações finais. O compilador também informa depreciação da interface de extensão Spring usada; isso não impediu build ou execução.
- Branch isolada: sete migrações aplicadas, TLS verificado e `HTTP 200 ready`. Chromium desktop e celular aprovaram cadastro, confirmação, login, contas, lançamento, transferência, orçamento, compra, fatura e dez telas. As contas de ensaio solicitaram exclusão ao final.
- `production`: bootstrap dos papéis concluído, sete migrações aplicadas e app local em `http://localhost:8083`, perfil `neon,postgres`. `npm run neon:verify` aprovou a conexão pooled/TLS, o usuário sem privilégios administrativos e a recusa de escrita no histórico (SQLSTATE 42501).
- A instância temporária da branch de teste foi encerrada; a instância final usa `production`. O Docker local da porta 8080 e os arquivos H2 foram preservados. Nenhum dado local foi importado e não foi criado login dev no Neon.
- Nenhum servidor Java foi publicado na nuvem nem novo APK conectado foi gerado. Hospedagem HTTPS, SMTP público e política de backups continuam sendo etapas necessárias para disponibilização externa.

Logs locais de inicialização: `target/neon-test-app.log` e `target/neon-production-app.log`. Verificações são reproduzíveis pelos comandos acima; `.env.local` e `.env.neon-test.local` permanecem ignorados pelo Git.
