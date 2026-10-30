# Decisão 001 — Java e MVC

**Status:** adotada. A instrução do usuário define Java e frameworks adequados.

- Java 21, Maven e Spring Boot 4.1.0.
- Spring MVC para Controllers e rotas; Thymeleaf para Views renderizadas no servidor.
- Spring Security para autenticação, proteção CSRF, fixação de sessão, cookies e cabeçalhos.
- Senhas com PBKDF2 do Spring Security, salt e custo adaptativo; tokens de acesso armazenados como SHA-256 de segredos aleatórios de uso único.
- Spring JDBC para repositórios, com SQL parametrizado e transações Spring.
- H2 em arquivo no desenvolvimento e nos primeiros testes locais. Driver e migrações compatíveis com PostgreSQL; homologação PostgreSQL ainda deve ser executada antes dessa implantação.
- Flyway para migrações imutáveis. Alterações posteriores entram em nova versão; nunca editar migração já aplicada.
- Spring Mail para SMTP; adaptador de arquivo apenas no desenvolvimento.
- JUnit 5, AssertJ, MockMvc e testes de integração com banco real H2. Google Java Format via Spotless padroniza o código.

## Responsabilidades

`controller` adapta HTTP, identidade e dados de apresentação. `service` coordena casos de uso e transações. `model` contém invariantes e cálculos puros. `repository` executa persistência. `templates` contém Views e `static` o CSS e JavaScript progressivo. Templates não acessam repositórios nem calculam totais financeiros. O mapa de dados de consultas mantém nomes das colunas; formatação monetária fica em `ViewFormat`.

Toda operação financeira passa por `OperationService`, que bloqueia o titular dentro da transação, verifica idempotência, executa a alteração, grava auditoria e registra o resultado. Esse bloqueio serializa escritas de um mesmo usuário. Versões detectam formulários desatualizados. Relações compostas `(id,user_id)` impedem vínculos entre titulares também no banco.

Transferências são um único registro com dois efeitos derivados. Pagamento de fatura tem vínculo único ativo. Saldos derivam dos registros; não são mantidos em campo mutável. Relatórios e exportação completa usam transação de leitura com snapshot repetível.

## Limites operacionais

H2 em arquivo e sessões em memória pressupõem uma instância. Reiniciar encerra sessões, preservando os dados. Escala horizontal requer PostgreSQL homologado, sessões compartilhadas, controle distribuído de tentativas e agendamento coordenado. O banco local não é uma plataforma de hospedagem provisionada.

Não foi criada implementação Node/Express. O script JavaScript em `scripts/` serve exclusivamente à validação de navegador.

Referências: [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [Spring MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html), [Thymeleaf](https://www.thymeleaf.org/documentation.html).
