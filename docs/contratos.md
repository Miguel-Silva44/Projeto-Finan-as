# Contratos HTTP

A interface é MVC com formulários `application/x-www-form-urlencoded` e Views HTML. Todas as rotas de alteração exigem POST e token CSRF gerado pelo Thymeleaf. Não há API pública JSON nesta versão. Valores de formulário usam decimal textual (ex.: `1234,56` ou `1234.56`), convertido uma única vez em centavos no domínio; banco e exportações financeiras usam inteiros. Esse é o contrato de entrada de formulários, separado da representação monetária interna.

Datas civis: `YYYY-MM-DD`; mês: `YYYY-MM`. IDs e chaves de idempotência: UUID. Formulários financeiros incluem `key`; edições e transições incluem `version`. Repetir chave/corpo devolve o mesmo resultado; alterar o corpo ou usar versão antiga gera conflito. O titular vem da sessão.

| Método/rota | Comportamento |
|---|---|
| GET/POST `/auth/register`, `/auth/login`, `/auth/recover`, `/auth/reset` | Views e comandos de identidade |
| GET/POST `/auth/verify` | Exibe confirmação e consome token; GET não altera dados |
| POST `/auth/logout` | Invalida sessão |
| GET `/dashboard?month=2026-10&horizon=2026-11-30` | Resumo por competência e projeção |
| GET `/transactions` | Lista de 30 registros, página seguinte e filtros |
| POST `/transactions` | Criar/editar lançamento |
| POST `/transactions/{id}/realize`, `/undo`, `/cancel` | Transições; realização recebe `effective_date`, cancelamento recebe `reason` |
| POST `/transfers`, `/transfers/{id}/cancel` | Transferência integral e cancelamento |
| GET/POST `/accounts`, `/categories`, `/cards`, `/recurrences`, `/budgets` | Cadastros e planejamento |
| POST `/account/{id}/archive`, `/category/{id}/archive`, `/card/{id}/archive` | Arquivamento validado |
| POST `/purchases/preview`, `/purchases` | Prévia sem persistência e confirmação da compra |
| POST `/purchases/{id}/cancel` | Cancelar compra com todas as faturas abertas |
| POST `/invoices/{id}/close`, `/reopen`, `/pay`, `/undo` | Correções e pagamento integral |
| POST `/recurrences/{id}/stop` | Encerra geração futura |
| POST `/budgets/copy` | Copia limites ausentes do mês anterior |
| GET `/reports` | Resultado por competência ou fluxo de caixa (`basis=cash`) |
| GET `/exports/transactions`, `/exports/report` | CSV completo dos filtros, independente da paginação |
| GET `/me/export` | ZIP de todos os registros financeiros do titular, incluindo histórico |
| POST `/me`, `/me/password`, `/me/delete` | Perfil, troca de senha e exclusão |
| GET `/audit` | Últimos 100 eventos; exportação contém a trilha inteira |

Filtros de lançamentos: `q`, `basis` (`competence`, `due_date`, `realized_date`), `from`, `to`, `kind`, `status`, `account_id`, `category_id`, `page`. Filtros de relatório: `from`, `to`, `basis` (`competence`, `cash`), `account_id` e `category_id` (somente competência). Intervalos incluem as bordas. Parcelas usam o primeiro dia do mês de competência para filtragem de período.

Criação bem-sucedida retorna redirecionamento MVC para a listagem. Erros de domínio retornam HTML 422, registro alheio/inexistente 404, conflito de versão/idempotência 409, ausência de CSRF 403 e excesso de tentativas 429 com `Retry-After`. A página de erro contém código estável, mensagem e correlação; o navegador permite voltar ao formulário. Falhas inesperadas não exibem stack trace.

Downloads são autenticados, com `Cache-Control: no-store`, UTF-8 e fórmulas CSV neutralizadas. Nenhum recurso recebe `user_id` como parâmetro de autorização.
