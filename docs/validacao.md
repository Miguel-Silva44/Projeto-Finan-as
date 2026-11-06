# Validação da implementação

Ambiente: Windows 11, Temurin Java 21.0.9, Maven 3.9.12, Spring Boot 4.1.0 e H2 2.4.240. Todas as validações usam dados fictícios. A aplicação não inicia com dados demonstrativos.

## Evidências automatizadas

- `DevelopmentAccessTest`: login dev real sem confirmação de e-mail, navegação autenticada em todas as funcionalidades, conta dev bloqueada fora do perfil exclusivo de desenvolvimento, ausência do inicializador em produção e preservação de credenciais em reinicializações.
- `mvn.cmd verify`: testes de domínio, integração com banco, renderização MVC, segurança e análise de dependências dos Controllers via Checkstyle.
- `mvn.cmd spotless:check`: formatação Google Java Format.
- `FinanceIntegrationTest`: saldo/projeção/orçamento; transferências; idempotência; transições; edição concorrente; faturas e pagamento concorrente; recorrências; isolamento entre titulares; datas; CSV integral e proteção contra fórmulas; rollback da operação quando a auditoria falha; CSRF; login; tokens de uso único/expiração; revogação de sessões; exclusão completa; restauração de usuários e vínculos financeiros com saldo reconstruído.
- `FinancialRulesTest`: conversão exata de moeda, rejeição de valores inválidos, divisão de centavos e calendário.
- `BackupIntegrationTest`: snapshot criptografado, senha incorreta recusada e restauração em banco separado.
- `scripts/browser-check.cjs`: Chromium desktop 1440 px, Chromium móvel e WebKit móvel 390 px; variantes em paisagem e 320 px. Percorre cadastro, confirmação, login, contas, despesa realizada, transferência, orçamento, compra com prévia, fechamento e pagamento de fatura; abre dez telas, verifica ausência de erros JavaScript e rolagem horizontal da página.

O roteiro de navegador usa um usuário por execução e solicita sua exclusão ao terminar. As capturas ficam em `target/screenshots/`. Os motores instalados para esse ensaio são Chromium 156 e WebKit 27.2 (Playwright 1.64.1). São execuções automatizadas no Windows, não aparelhos Android/iOS físicos.

## Desempenho

Comando opt-in:

```powershell
mvn.cmd test '-Dtest=PerformanceTest' '-Dfinance.performance=true'
```

Medição local: 100.000 lançamentos para um titular, dashboard aquecido com três consultas, 20 leituras simultâneas, JVM 21.0.9, Windows 11, 12 processadores lógicos. P95 observado: **583 ms**, maior amostra: 584 ms. A asserção da meta de até 1 segundo passou. Resultado detalhado em `target/performance.txt`.

Esse ensaio mede o serviço de agregação com H2 em memória, sem rede, renderização nem volume de produção. Não comprova o mesmo percentil em uma hospedagem ou banco PostgreSQL. A medição HTTP com 20 sessões reais permanece parte da homologação do ambiente escolhido.

## Rastreabilidade dos critérios

| Critérios | Evidência |
|---|---|
| CA01–CA06 | Integração de contas, saldos, transferências, idempotência, transições e rollback |
| CA07–CA11 | Testes de calendário/centavos, pagamento concorrente e bloqueio de correção em fatura fechada/paga; roteiro de compra/pagamento no navegador |
| CA12–CA18 | Recorrências, orçamento, isolamento, versões, CSV, identidade e arquivamento |
| CA19–CA22 | Restauração em banco independente, falha de auditoria, pendências por data e CSV protegido |
| CA23 | Automação Chromium/WebKit em dimensões móveis; aparelhos reais ainda pendentes |
| CA24 | Separação MVC e Checkstyle impedindo dependências JDBC/repositório nos Controllers |

## Limites e pendências externas

Em 06/11/2026, a integração Neon também foi validada: `npm run build` aprovado (34 testes executados e 1 opcional ignorado), Flyway v7 na branch de teste e em `production`, TLS e permissões runtime aprovados por `npm run neon:verify`, e jornadas financeiras Chromium desktop/celular aprovadas na branch isolada. Procedimentos e limites em [neon.md](neon.md). O servidor Java ainda roda localmente; Neon hospeda o banco.

- Entrega SMTP real não foi acionada. E-mails locais são arquivos `.eml` privados em `data/mail`; produção exige configuração e confirmação de recebimento.
- Hospedagem, HTTPS, armazenamento independente de backups, retenção e restauração em até 8 horas dependem da operação.
- PostgreSQL 18.6 em Docker foi executado e validado em 06/11/2026: 19 testes de integração aprovados, recriação de containers com dados preservados e backup criptografado restaurado em banco separado. Evidências locais: `target/postgres-verification.log` e `target/postgres-backup-validation.txt`; procedimentos reproduzíveis no [guia PostgreSQL](postgresql-docker.md).
- Safari/iOS, Chrome/Android em aparelhos reais, Edge/Firefox e leitor de tela exigem homologação específica.
- Relatórios, histórico e exportação completa ainda podem ser volumosos; a lista de lançamentos é paginada, o histórico em tela limita a 100 registros e o restante permanece disponível na exportação.
- Não há integração bancária, execução de pagamentos nem processamento de cartão pelo app.

Assim, a implementação pode ser executada e revisada localmente. A definição de pronto para produção da spec não deve ser marcada como satisfeita enquanto essas verificações externas estiverem pendentes.
