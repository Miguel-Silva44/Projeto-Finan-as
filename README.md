# Controle Financeiro

Próximas entregas especificadas: [PostgreSQL e Docker com persistência](specs/spec-postgresql-docker-persistencia.md) e [análise/distribuição Android APK](specs/spec-android-apk.md).

Webapp em **Java 21, Spring Boot 4.1.0, Spring MVC, Thymeleaf, Spring Security e Spring JDBC**, com interface responsiva. Banco H2 persistente para uso local, migrações Flyway e driver PostgreSQL disponível para implantação.

## Executar

Pré-requisitos: JDK 21 e Maven 3.9+. No PowerShell:

```powershell
mvn.cmd spring-boot:run
```

Abra **http://localhost:8080**. Crie sua conta; o ambiente local grava o e-mail de confirmação como `.eml` em `data/mail/`. Abra esse arquivo, copie o link de confirmação para o navegador e confirme o acesso. Não existe senha padrão nem conta demonstrativa. O banco fica em `data/financas.mv.db` e sobrevive a reinicializações.

Para acessar pelo celular na mesma rede, abra `http://IP-DO-COMPUTADOR:8080`; ajuste `BASE_URL` para esse endereço antes de gerar links de acesso. A liberação da porta no firewall depende da configuração do computador. HTTPS é necessário para uso de produção.

## Login de desenvolvimento

```powershell
mvn.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Em `http://localhost:8080/auth/login`, use:

- **E-mail:** `dev@financas.local`
- **Senha inicial:** `DevFinanceiro123!`

Essa conta já está confirmada e acessa todos os recursos do aplicativo, mantendo o isolamento dos dados de outros usuários. O perfil `dev` usa banco separado em `data/dev/financas.mv.db`. Sem esse perfil, ou junto com `prod`/`production`, a autenticação da conta dev é bloqueada, inclusive se o banco for copiado.

Para personalizar as credenciais antes da primeira inicialização, defina `DEV_LOGIN_EMAIL` e `DEV_LOGIN_PASSWORD`. Reiniciar não redefine uma senha alterada nem reativa uma conta excluída. Com JAR: `java -jar target/controle-financeiro-1.0.0.jar --spring.profiles.active=dev`.

## Recursos

- Cadastro com confirmação de e-mail, recuperação de senha, sessão e proteção CSRF.
- Contas, saldo inicial, extrato, categorias e arquivamento com validação.
- Receitas/despesas, realização, correção, cancelamento e histórico imutável nas telas.
- Transferências atômicas, idempotência e detecção de edição concorrente.
- Recorrências mensais e geração recuperável de pendências.
- Cartões, prévia de parcelas, faturas, pagamento integral e correções.
- Orçamento mensal com alertas, resultado por competência e fluxo de caixa.
- Exportação filtrada CSV e exportação completa ZIP.
- Perfil, mudança de senha, revogação de sessões e exclusão de conta.
- Backup H2 criptografado diário quando configurado.

Os registros são manuais: marcar como pago não executa transferência bancária. Transferências e pagamentos de fatura não duplicam despesas por competência. Veja as regras na [spec](specs/spec-app-controle-financeiro.md).

## Qualidade e build

```powershell
mvn.cmd test
mvn.cmd spotless:check
mvn.cmd package
java -jar target/controle-financeiro-1.0.0.jar
```

Para formatar Java: `mvn.cmd spotless:apply`. Testes usam banco H2 em memória separado dos dados locais e relógio controlado nos cenários financeiros. O teste de backup restaura um snapshot em outro banco e compara o saldo.

`mvn.cmd verify` também executa Checkstyle para impedir dependências de banco/repositório nos Controllers. O teste de carga é opcional: `mvn.cmd test '-Dtest=PerformanceTest' '-Dfinance.performance=true'`. Evidências e limites em [validação](docs/validacao.md).

Automação opcional de navegador usa Playwright **apenas como ferramenta de teste**, sem dependência Node no app Java:

```powershell
npm.cmd install --prefix target/browser-tools playwright
npm.cmd exec --prefix target/browser-tools -- playwright install chromium webkit
$env:PLAYWRIGHT_MODULE = (Resolve-Path target/browser-tools/node_modules/playwright).Path
node scripts/browser-check.cjs
```

Execute com a aplicação iniciada e `MAIL_MODE=file`, em ambiente de teste. O roteiro cria usuários fictícios, percorre os fluxos e solicita sua exclusão ao terminar. Imagens ficam em `target/screenshots/`. WebKit/Chromium com viewport móvel não substituem homologação em aparelhos Android/iOS reais.

## Configuração

Variáveis de ambiente são lidas pelo Spring; arquivos `.env` não são carregados automaticamente.

| Variável | Padrão / uso |
|---|---|
| `PORT` | `8080` |
| `BASE_URL` | `http://localhost:8080`; endereço usado nos e-mails |
| `DATABASE_URL` | H2 local; para PostgreSQL: `jdbc:postgresql://servidor:5432/financas` |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Credenciais do banco |
| `COOKIE_SECURE` | `false` local; `true` sob HTTPS |
| `MAIL_MODE` | `file` local; `smtp` para entrega real |
| `SMTP_HOST`, `SMTP_PORT` | `localhost`, `1025` |
| `SMTP_USER`, `SMTP_PASSWORD` | Credenciais SMTP |
| `SMTP_AUTH`, `SMTP_TLS` | `false` local; configurar conforme provedor |
| `MAIL_FROM` | Remetente autorizado pelo servidor SMTP |
| `BACKUP_PASSWORD` | Segredo de pelo menos 16 caracteres; habilita backup diário às 03h UTC |

Recorrências, fechamento e remoção de contas desativadas executam a cada hora, com primeira execução após 10 segundos. A aplicação precisa permanecer em execução. Falhas de tarefas geram log sem conteúdo financeiro e são tentadas novamente. O limite de tentativas de autenticação é local à instância: 30 POSTs por IP em 15 minutos. Uma implantação com várias instâncias exige limitador e sessões compartilhados.

## Backup, restauração e implantação

Consulte [operação](docs/operacao.md), [decisão de arquitetura](docs/decisao-arquitetura.md) e [contratos](docs/contratos.md). Os prazos de retenção, entrega SMTP, infraestrutura de produção, homologação em aparelhos reais e testes de carga devem ser verificados antes de liberar para usuários reais.
