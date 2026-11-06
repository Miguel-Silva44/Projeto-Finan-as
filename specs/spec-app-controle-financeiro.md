# Spec — App de controle financeiro pessoal

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico).
> **Versão:** 1.2 · **Data:** 29/10/2026.
> **Status:** implementação local funcional em Java; validações automatizadas e limitações operacionais registradas em [docs/validacao.md](../docs/validacao.md). Homologação de produção e aparelhos reais pendente.
> **Complexidade:** alta no conjunto; entrega dividida em incrementos verificáveis.
> **Pedido confirmado:** criar a especificação de um novo app de controle financeiro.
> **Diretriz confirmada:** webapp com arquitetura MVC, princípios de Clean Code e compatibilidade com celular por interface responsiva.
> **Premissas propostas:** uso pessoal, interface em português do Brasil e moeda BRL. Essas escolhas organizam esta versão e podem ser revistas pelo usuário.

## 1. Contexto e referências

O diretório atual contém sete specs de funcionalidades do AlocPro e configurações da IDE. Não foi encontrado código do novo app. Referências a classes, bancos, ambientes e commits nas specs antigas descrevem outro projeto e não estabelecem dependências para este sistema.

Todas as specs existentes foram consideradas como referência de documentação:

| Spec | Prática aproveitada |
|---|---|
| [Acesso por grupo](spec-acesso-alocpro-por-grupo.md) | Autorização no servidor e tratamento explícito de falhas |
| [Alerta de férias](spec-alerta-ferias-desligamento.md) | Separação entre alerta, bloqueio e automação |
| [Banco de horas](spec-banco-de-horas.md) | Definição precisa de saldo e prevenção de registros redundantes |
| [Consentimento de imagem e voz](spec-consentimento-imagem-voz.md) | Histórico consistente com o dado persistido, valores ausentes explícitos e controle por titular |
| [Faturamento externo](spec-faturamento-externo-devolver.md) | Estados, transições e rastreabilidade das operações |
| [Relatório de anotações](spec-relatorio-anotacoes-mapa.md) | Filtros, regras de agregação e critérios observáveis |
| [Visualização de MSG](spec-visualizar-msg-browser.md) | Dependências e limites técnicos documentados antes da implementação |

Esta spec descreve apenas o desenho vigente do novo produto. Revisões futuras devem atualizar as regras afetadas e registrar um resumo no histórico, evitando manter propostas contraditórias como se estivessem simultaneamente válidas.

## 2. Problema e objetivo

Permitir que uma pessoa saiba quanto possui em suas contas, quanto recebe e gasta, quais compromissos ainda precisa pagar e quanto pode gastar dentro de um orçamento mensal.

O app deve permitir registrar uma movimentação rapidamente, acompanhar cartões e parcelas sem duplicar despesas e consultar relatórios cujos números sejam explicáveis a partir dos lançamentos.

O sistema registra informações financeiras fornecidas pelo usuário. No MVP não movimenta dinheiro nem confirma pagamentos em instituições financeiras: marcar como pago significa registrar uma confirmação manual.

## 3. Público, plataforma e limites

### 3.1 Premissas de produto

- Cada usuário possui um espaço financeiro privado. Não há compartilhamento familiar ou empresarial nesta versão.
- Um usuário pode ter várias contas e cartões, todos em BRL.
- Interface pt-BR, valores como `R$ 1.234,56` e datas como `29/10/2026`.
- Plataforma definida: webapp MVC acessível pelo navegador de computadores e celulares, com interface responsiva. Instalação como PWA é uma evolução opcional, fora do MVP.
- Persistência no servidor e uso conectado. Sem conexão, informar indisponibilidade e preservar o formulário aberto em memória quando possível.
- Primeiro acesso começa sem valores fictícios. Exemplos, se disponíveis, devem ficar em ambiente demonstrativo separado.

### 3.2 Escopo do MVP completo

Autenticação, configuração inicial, contas, categorias, receitas e despesas, transferências, contas a pagar e receber, recorrências mensais, cartões, compras parceladas, faturas, orçamento por categoria, dashboard, relatórios e exportação CSV.

### 3.3 Fora do MVP

Integração bancária/Open Finance, execução de Pix ou boletos, importação de extratos, investimentos e cotações, múltiplas moedas, contabilidade empresarial, emissão fiscal, empréstimos com amortização, compartilhamento de dados, anexos, inteligência artificial, assinatura paga e notificações push/e-mail de vencimentos. Alertas internos estão incluídos.

## 4. Fluxos principais

### 4.1 Primeiro acesso

1. Criar conta de acesso, verificar e-mail e entrar.
2. Confirmar moeda BRL e fuso; padrão proposto `America/Sao_Paulo`.
3. Criar uma conta financeira com nome, tipo, data e saldo inicial, inclusive zero ou negativo.
4. Receber categorias iniciais editáveis, separadas entre receita e despesa.
5. Abrir dashboard com orientação para registrar o primeiro lançamento.

### 4.2 Receita ou despesa

1. Selecionar tipo, descrição, valor, categoria, conta, competência e vencimento.
2. Manter pendente ou registrar realização com data efetiva.
3. Salvar; a lista, o saldo e os indicadores passam a refletir a mesma operação persistida.
4. Edição de valor ou conta em operação realizada exige confirmação e recalcula os efeitos atomicamente.

### 4.3 Transferência

Selecionar contas distintas do mesmo usuário, valor e data efetiva. Confirmar uma operação indivisível com saída na origem e entrada no destino. Não escolher categoria de receita/despesa.

### 4.4 Compra no cartão

Informar cartão, descrição, categoria, data da compra, valor total e número de parcelas. Antes de salvar, apresentar parcelas, valores e respectivas faturas. Registrar o pagamento de uma fatura posteriormente, escolhendo conta e data de pagamento.

### 4.5 Planejamento mensal

Escolher mês e definir limites por categoria de despesa. Acompanhar gasto confirmado, compromissos pendentes e limite disponível. Abrir os lançamentos que formam qualquer indicador.

## 5. Requisitos funcionais

| ID | Requisito e comportamento esperado |
|---|---|
| RF01 | Cadastro, verificação de e-mail, login, logout e recuperação de senha com tokens de uso único e expiração |
| RF02 | Criar, editar e arquivar contas dos tipos corrente, poupança e carteira; informar saldo inicial e data de referência |
| RF03 | Criar, renomear e arquivar categorias de receita ou despesa; impedir troca de tipo se já utilizada |
| RF04 | Criar, listar, filtrar, editar e cancelar receitas/despesas; descrição e categoria obrigatórias |
| RF05 | Registrar realização e desfazer realização; pendência não altera saldo realizado |
| RF06 | Transferir entre contas em uma transação atômica e cancelar a transferência inteira |
| RF07 | Exibir pendências por vencimento, incluindo vencidas e próximos sete dias |
| RF08 | Cadastrar recorrência mensal com início e término opcional; gerar ocorrências sem duplicação |
| RF09 | Cadastrar cartão com nome, limite informativo e dias de fechamento/vencimento |
| RF10 | Registrar compras à vista ou parceladas no cartão e exibir a distribuição antes de confirmar |
| RF11 | Listar faturas, fechar fatura e registrar pagamento integral manual em uma conta |
| RF12 | Definir orçamento mensal por categoria; copiar o mês anterior mediante ação explícita |
| RF13 | Dashboard com saldos, resultado mensal, pendências, faturas e gastos por categoria |
| RF14 | Relatórios por período, categoria e conta, com acesso aos lançamentos de origem |
| RF15 | Exportar os dados filtrados em CSV e oferecer exportação completa dos dados financeiros do usuário |
| RF16 | Consultar histórico de alterações financeiras; edição de outro campo sem mudança financeira não cria movimentação |
| RF17 | Alterar nome, fuso e senha; solicitar exclusão da conta de acesso com confirmação reforçada |

## 6. Regras de negócio e cálculos

### 6.1 Valores e datas

- RN01 — Valores monetários são inteiros em centavos, positivos nas entradas de usuário. O tipo da operação determina o sinal. Não usar ponto flutuante em cálculo monetário.
- RN02 — Valor de lançamento deve ser maior que zero; saldo inicial pode ser zero ou negativo. Rejeitar casas decimais além de duas e valores fora do intervalo suportado, definido igualmente na API e no banco.
- RN03 — Competência, vencimento e realização são datas civis independentes, armazenadas sem conversão de fuso. Eventos de auditoria usam instante UTC.
- RN04 — Competência indica o mês do resultado financeiro. Vencimento indica quando o compromisso deve ser cumprido. Realização indica quando o dinheiro entrou ou saiu.
- RN05 — Uma realização não pode ter data futura. Uma pendência está vencida quando seu vencimento é anterior ao dia atual no fuso do usuário.
- RN06 — Períodos de consulta incluem as duas datas informadas. Nenhuma movimentação pode anteceder a data do saldo inicial da conta; corrigir essa data exige validação de todas as movimentações existentes.

### 6.2 Estados e correções

Receitas e despesas diretas têm estados `PENDENTE`, `REALIZADO` ou `CANCELADO`. “Vencido” é uma condição calculada de pendência, sem estado persistido adicional.

Transições permitidas: pendente → realizado; realizado → pendente; pendente ou realizado → cancelado. Cancelado é terminal no MVP. Não há pagamento parcial de lançamento direto.

Desfazer realização remove seu efeito no saldo e limpa a data efetiva. Cancelar um realizado desfaz seu efeito e preserva o histórico. Operações financeiras não são apagadas fisicamente pelas telas comuns. Correções ficam auditadas com valor anterior e novo, autor, instante e motivo quando houver cancelamento.

### 6.3 Saldo e transferências

`Saldo da conta em D = saldo inicial + soma dos efeitos realizados até D, excluindo operações canceladas`.

O saldo inicial vale na abertura da data de referência. Movimentos desse dia entram depois dele. Alterá-lo exige confirmação e histórico; não constitui receita do mês.

Transferência realizada produz efeitos `-valor` e `+valor`. Sua soma no conjunto de contas é zero. Transferências e pagamentos de fatura são excluídos de receitas, despesas por competência e orçamento. Tarifas, quando existirem, são despesas separadas criadas pelo usuário.

Permitir saldo negativo com aviso. Contas arquivadas continuam em históricos e no saldo consolidado, identificadas como arquivadas; não recebem novos movimentos. Só permitir arquivamento quando saldo atual for zero e não houver pendências ou pagamentos vinculados em aberto.

### 6.4 Resultado, orçamento e projeção

- **Receitas confirmadas do mês:** receitas diretas realizadas com competência no mês.
- **Despesas confirmadas do mês:** despesas diretas realizadas e parcelas de compras ativas atribuídas a faturas com competência no mês, mesmo se a fatura não foi paga.
- **Resultado mensal:** receitas confirmadas menos despesas confirmadas. É resultado por competência, separado do fluxo de caixa.
- **Fluxo de caixa:** efeitos realizados em contas agrupados por data efetiva. Em relatório consolidado, eliminar transferências internas; discriminar pagamentos de fatura sem tratá-los como nova despesa por categoria.
- **Orçamento consumido:** despesas confirmadas da categoria na competência escolhida. **Comprometido pendente:** despesas diretas pendentes dessa competência. Parcelas já entram no consumido, sem somá-las novamente ao pendente.
- **Disponível para comprometer:** limite menos consumido menos comprometido pendente. Pode ficar negativo. Sem limite cadastrado, exibir “Sem orçamento”, nunca limite zero implícito.
- Alertas internos de orçamento em 80% e 100% de `(consumido + pendente) / limite`. Limite deve ser positivo.
- **Saldo projetado até D:** saldo realizado de hoje, mais receitas pendentes e menos despesas pendentes com vencimento até D, menos faturas fechadas/em aberto ainda não pagas com vencimento até D. Incluir atrasados uma única vez. Oferecer projeção somente para hoje ou datas futuras.

Cada indicador mostra sua base de datas e permite abrir a lista correspondente. Gráficos e listas devem usar a mesma regra no servidor.

### 6.5 Cartões, parcelas e faturas

- Cartão é um instrumento de crédito, sem saldo de conta bancária. Compra não altera saldo de conta; pagamento de fatura altera.
- Para o MVP, uma compra é confirmada ao salvar. Compra cancelada deixa de compor fatura e resultado conforme as restrições de correção abaixo.
- Fatura de competência M fecha no dia configurado de M e vence no dia configurado do mês M+1. Dias inexistentes são ajustados para o último dia do mês. Datas são civis; compras feitas no dia de fechamento pertencem à fatura seguinte.
- Exemplo: fechamento dia 20, vencimento dia 10. Compra em 19/10 entra na fatura de outubro, vencendo em 10/11; compra em 20/10 entra na de novembro, vencendo em 10/12.
- As parcelas seguintes pertencem às competências mensais seguintes. A competência da despesa de cartão é a competência da fatura, inclusive em compras parceladas.
- Dividir centavos por quociente inteiro; distribuir o resto, um centavo por parcela, a partir da primeira. R$ 100,00 em três parcelas resulta em R$ 33,34 + R$ 33,33 + R$ 33,33.
- Número de parcelas proposto: de 1 a 120, sem parcela de valor zero. Não aplicar juros automaticamente; o total informado já é o total da compra.
- Estados de fatura: `ABERTA` → `FECHADA` → `PAGA`. “Vencida” é derivado do vencimento e da ausência de pagamento. Fechamento automático na data prevista e fechamento manual a partir dessa data usam a mesma operação idempotente.
- Pagamento somente integral e para fatura fechada de valor positivo, com data efetiva não futura; gera uma única saída vinculada à fatura, sem categoria de despesa. Fatura fechada de valor zero é encerrada sem movimentação.
- Pagamento parcial, rotativo, estorno comercial e crédito em fatura ficam fora do MVP.
- Permitir editar/cancelar compra somente se todas as faturas afetadas estiverem abertas. Para correção de fatura fechada, exigir reabertura explícita; se paga, desfazer o pagamento antes. Todas as ações ficam auditadas. A reabertura impede execução concorrente do fechamento durante a correção e exige fechamento manual ao terminar.
- Alterações de calendário do cartão só afetam ciclos futuros ainda sem fatura criada. Faturas existentes preservam fechamento e vencimento; compras novas usam o calendário do ciclo de destino.
- Limite é informativo: avisar quando o total das parcelas ainda não pagas exceder o limite. Permitir continuar. Não afirmar que o limite calculado corresponde ao disponível no banco.

### 6.6 Recorrências e cadastros

- Recorrência mensal gera lançamentos diretos pendentes, sem marcar pagamento automaticamente. Não se combina com compra parcelada no MVP.
- No cadastro, gerar ocorrências do início até o fim do mês seguinte ao atual; a execução diária amplia esse horizonte e recupera ocorrências não geradas após interrupções.
- Usar unicidade por recorrência e mês de ocorrência. Editar uma ocorrência não modifica a regra; editar a regra afeta somente ocorrências futuras ainda não geradas. Encerrar a regra não apaga as existentes.
- Dia 29, 30 ou 31 inexistente usa o último dia daquele mês e mantém o dia original como âncora dos meses seguintes.
- Categorias arquivadas continuam nos relatórios e não podem ser escolhidas em novos lançamentos. Bloquear arquivamento enquanto houver recorrência ativa que as utilize.

## 7. Telas e experiência

| Tela | Conteúdo e ações |
|---|---|
| Acesso | Cadastro, login, verificação e recuperação |
| Início | Mês selecionado, saldo atual com data, resultado por competência, projeção, próximos vencimentos e atalhos |
| Lançamentos | Lista paginada, busca por descrição e filtros de competência/vencimento/realização, tipo, categoria, conta e estado |
| Novo lançamento | Receita, despesa ou transferência; formulário específico para cada operação |
| Contas | Saldo por conta, extrato e histórico do saldo inicial |
| Cartões | Compras, parcelas, faturas, fechamento, pagamento e correções |
| Orçamento | Limites por categoria, consumido, pendente e disponível do mês |
| Relatórios | Resultado por competência, fluxo de caixa e composição por categoria |
| Configurações | Categorias, recorrências, perfil, exportação e exclusão da conta de acesso |

No celular, navegação principal: Início, Lançamentos, Cartões e Mais, com acesso visível a novo lançamento. No desktop, os mesmos destinos em navegação lateral.

Estados obrigatórios em cada tela: carregando, vazio, erro com opção de tentar novamente, sucesso e indisponibilidade de rede. Não mostrar sucesso antes da confirmação da API. Se a resposta se perder, permitir repetir a operação com a mesma chave, sem duplicação.

Usar rótulos visíveis, navegação por teclado, foco identificável e erros próximos dos campos. Não depender apenas de cor para representar atraso ou saldo negativo. Gráficos têm alternativa textual. Formulário deve aceitar teclado decimal e apresentar confirmação antes de cancelar operações realizadas.

## 8. Modelo de dados proposto

Todos os registros financeiros possuem identificador opaco, `user_id`, datas de criação/atualização e versão para concorrência. Relacionamentos entre registros devem validar o mesmo titular também no banco, por chaves compostas ou mecanismo equivalente.

| Entidade | Campos e restrições centrais |
|---|---|
| Usuário | ID, nome, e-mail normalizado único, hash de senha, verificação, fuso e estado da conta |
| Sessão / token de acesso | Usuário, hash do segredo, expiração e revogação; tokens de verificação/recuperação separados por finalidade |
| Conta | Usuário, nome, tipo, saldo inicial em centavos, data de referência e arquivamento |
| Categoria | Usuário, nome, tipo receita/despesa e arquivamento |
| Lançamento direto | Usuário, conta, categoria, tipo, descrição, valor, competência, vencimento, realização opcional, estado e recorrência opcional |
| Transferência | Usuário, origem, destino, valor, data efetiva e estado; origem diferente de destino |
| Recorrência | Usuário, conta, categoria, tipo, descrição, valor, dia âncora, início e fim opcional |
| Cartão | Usuário, nome, limite, dias de fechamento/vencimento e arquivamento; sem número completo ou código de segurança |
| Compra | Usuário, cartão, categoria, descrição, data, valor total, quantidade de parcelas e estado |
| Parcela | Usuário, compra, número, valor e fatura; unicidade compra/número |
| Fatura | Usuário, cartão, competência, datas congeladas, estado e modo de reabertura; unicidade cartão/competência |
| Pagamento de fatura | Usuário, fatura, conta, valor, data efetiva e cancelamento; apenas um pagamento ativo por fatura |
| Orçamento | Usuário, categoria de despesa, mês e limite; unicidade usuário/categoria/mês |
| Auditoria | Usuário, entidade, ação, campos alterados antes/depois, instante e identificador da operação |
| Idempotência | Usuário, chave, operação, hash da requisição e resultado; unicidade por usuário/chave/operação |

Saldos e totais derivam dessas fontes. Se forem usados caches ou projeções, devem ser reconstruíveis. Não manter um saldo manual independente dos movimentos.

Efeitos de conta são uma visão unificada de lançamentos realizados, pernas de transferências e pagamentos de fatura ativos. Não duplicar cada efeito como nova despesa em outra tabela. Auditoria e operação financeira são gravadas na mesma transação.

## 9. Arquitetura e contratos

### 9.1 Organização MVC

Adotar MVC (Model–View–Controller) em uma webapp inicialmente única, organizada pelos módulos de identidade, cadastros, movimentações, cartões, planejamento e relatórios.

Fluxo principal: navegador → rota autenticada → Controller → serviço de aplicação → Model/repositório → banco relacional transacional. O Controller entrega os dados de apresentação à View, que renderiza a resposta responsiva. Interações assíncronas podem consumir respostas JSON dos mesmos serviços de aplicação, sem duplicar regras financeiras.

| Componente | Responsabilidade | Limite |
|---|---|---|
| Model | Entidades, objetos de valor, invariantes, cálculos financeiros e contratos de persistência | Não depende de telas, requisições HTTP ou Controllers |
| View | Templates, componentes visuais, formatação, acessibilidade e adaptação a telas | Não consulta banco nem executa cálculo financeiro autoritativo |
| Controller | Recebe a requisição, valida seu formato, obtém identidade, chama o caso de uso e seleciona resposta/View | Não contém regra financeira nem acesso direto ao banco |
| Serviços de aplicação | Coordenam casos de uso, autorização por titular, transações, idempotência e auditoria | Reutilizáveis pelos Controllers e tarefas agendadas |
| Repositórios e infraestrutura | Implementam persistência, envio de e-mail e outros serviços externos | Não decidem regras de negócio ou apresentação |

Um executor de tarefas cuida de recorrências, fechamento e recuperação de execuções interrompidas, chamando os mesmos serviços de aplicação usados pelos Controllers. Cálculos e autorização ficam no servidor; validações no navegador auxiliam o preenchimento e são repetidas no servidor.

Organização conceitual por módulo, a adaptar às convenções do framework:

```text
modulos/<modulo>/
  controllers/
  models/
  services/
  repositories/
  views/
assets/
config/
migrations/
tests/
```

Stack definida por instrução do usuário: Java 21, Spring Boot 4.1.0, Spring MVC, Thymeleaf, Spring Security, Spring JDBC e Flyway. PostgreSQL 18.6 em Docker com volume persistente foi implementado e validado localmente; o modo H2 continua disponível. CSS próprio implementa a interface responsiva. Identidade é gerenciada pelo Spring Security; hospedagem permanece em aberto. Decisão e limites em [decisao-arquitetura.md](../docs/decisao-arquitetura.md), execução no [README](../README.md). Esta implementação não herda a stack Java/Wicket do AlocPro.

### 9.2 Princípios de Clean Code

- Usar nomes claros e consistentes com o domínio financeiro; evitar abreviações ambíguas e valores mágicos.
- Manter funções e classes coesas, com uma responsabilidade principal e dependências explícitas.
- Centralizar regras de saldo, competência, parcelamento e fatura no Model e nos serviços correspondentes; reutilizá-las em telas, relatórios e tarefas.
- Separar validação de formato, regras de domínio e apresentação de erros, preservando os códigos de erro dos contratos.
- Usar injeção de dependências nos limites necessários para testar domínio, persistência, relógio e integrações; evitar estado global mutável.
- Extrair abstrações quando houver responsabilidade ou variação concreta, sem criar camadas vazias ou generalizações antecipadas.
- Comentários explicam motivos e restrições; comportamento deve ser compreensível pelos nomes e estrutura do código.
- Padronizar formatação e análise estática; revisões verificam limites MVC, duplicação de regras e tratamento de falhas.
- Testar cálculos e invariantes no domínio, coordenação e persistência em integração, e jornadas críticas pela interface.

### 9.3 Rotas e contratos propostos

Os grupos abaixo são atendidos por Controllers MVC. Rotas de consulta podem renderizar Views; comandos processam formulários ou JSON conforme a interação. O contrato deve explicitar o formato de cada resposta. Não é obrigatório construir uma SPA separada.

| Grupo | Operações |
|---|---|
| `/auth` | Cadastro, verificação, login, logout e recuperação |
| `/me` | Perfil, preferências, exportação e solicitação de exclusão |
| `/accounts`, `/categories` | Listagem, criação, edição e arquivamento |
| `/transactions` | Listagem, criação e edição; comandos `realize`, `undo-realization` e `cancel` |
| `/transfers` | Criação, listagem e cancelamento integral |
| `/recurrences` | Criação, edição, listagem e encerramento |
| `/cards`, `/purchases` | Cadastros e compras com prévia de parcelamento |
| `/invoices` | Consulta, fechamento, reabertura, pagamento e desfazimento do pagamento |
| `/budgets` | Consulta e atualização por mês/categoria |
| `/dashboard`, `/reports`, `/exports` | Agregações e exportações com filtros explícitos |

Na implementação MVC, formulários enviam decimal textual com até duas casas (por exemplo `1234,56`), convertido no servidor em inteiro de centavos; persistência e exportações usam centavos. Uma futura API JSON deverá usar inteiros. Datas civis usam `YYYY-MM-DD`; mês, `YYYY-MM`; instantes, UTC. A lista de lançamentos tem 30 registros por página, ordenação por data e ID e exportação completa. Contratos e regras de filtros em [contratos.md](../docs/contratos.md).

O servidor extrai o titular da sessão, nunca de um `user_id` fornecido pelo cliente. Consultas por identificador de outro titular retornam resposta sem revelar a existência do recurso.

Criações financeiras, realização, transferências, compras e pagamentos exigem chave de idempotência. Mesma chave e mesmo corpo devolvem o resultado anterior; corpo diferente gera conflito. Preservar chaves financeiras enquanto a operação existir. Edição envia versão esperada; conflito concorrente exige recarga, sem sobrescrever silenciosamente.

Erros têm código estável, mensagem pt-BR, campos afetados e identificador de correlação. Contrato detalhado com exemplos e estados HTTP deve acompanhar a implementação de cada incremento.

## 10. Segurança, privacidade e operação

- Ambiente dev: perfil explícito `dev`, banco separado e conta inicial verificada com acesso a todas as funcionalidades. A conta recebe marca persistida `development_only`; login e criação automática são bloqueados fora de `dev` e quando `prod`/`production` também estão ativos. Mantém autorização por titular, CSRF e regras financeiras. Credenciais e execução em [README](../README.md#login-de-desenvolvimento).
- Toda rota financeira exige autenticação e autorização por titular, inclusive exportação e auditoria.
- Senhas usam hash adaptativo e segredos nunca entram no repositório. Sessões devem poder ser revogadas; recuperação de senha revoga sessões existentes.
- Se usadas sessões por cookie, exigir proteção contra CSRF e atributos seguros; tráfego de produção usa HTTPS.
- Limitar tentativas de autenticação e recuperação; respostas não devem enumerar contas cadastradas.
- Dados financeiros não entram em logs operacionais. Auditoria contém somente os campos necessários, sem senhas ou tokens, e não é editável nas telas.
- Exportações autenticadas não ficam em URLs públicas. CSV deve preservar centavos e acentos, documentar colunas e neutralizar conteúdo interpretável como fórmula em planilhas.
- Conta de acesso excluída perde sessões imediatamente. Proposta operacional: apagar dados ativos em até 30 dias e expirar cópias de backup em até 90 dias. Validar prazos e política antes da produção; não prometer eliminação instantânea de backups.
- Backups diários criptografados com teste de restauração antes do lançamento. Metas propostas: perda máxima de 24 horas de dados e restauração em até 8 horas.
- Desenvolvimento e homologação usam dados fictícios e envio de e-mail de teste. A entrega de e-mails de acesso precisa ser validada; enfileirar não prova recebimento.
- Migrações versionadas, configuração de ambiente documentada e procedimento de recuperação de deploy que preserve dados financeiros.
- Monitorar erros, latência, falhas de tarefas e divergências entre agregações e movimentos de origem.

## 11. Requisitos não funcionais

| ID | Meta verificável |
|---|---|
| RNF01 | Layout responsivo utilizável a partir de 320 px até desktop, em retrato e paisagem; tabelas extensas usam visualização adaptada ou rolagem restrita ao componente, sem rolagem horizontal da página |
| RNF02 | Meta inicial: percentil 95 das consultas principais até 1 segundo no servidor, com 100 mil lançamentos por usuário e 20 sessões simultâneas em ambiente de referência documentado |
| RNF03 | Salvamentos financeiros atômicos: falha em qualquer etapa não deixa efeito parcial nem auditoria de sucesso |
| RNF04 | Tarefas repetidas ou concorrentes não duplicam ocorrências, parcelas, fechamento ou pagamentos |
| RNF05 | Relatórios e exportações reproduzem os totais das fontes sob os mesmos filtros; exportação completa não é limitada à página visível |
| RNF06 | Acessibilidade validada com teclado e leitor de tela nos fluxos de acesso, criação e pagamento |
| RNF07 | Setup local reproduzível, migrações automáticas e dados demonstrativos separados dos dados reais |
| RNF08 | Validar fluxos críticos em Chrome no Android, Safari no iOS e navegadores desktop Chrome, Edge, Firefox e Safari, nas versões estáveis disponíveis na homologação; registrar versões e resultados |
| RNF09 | Controles principais com área de toque mínima de 44 × 44 px, formulários utilizáveis com teclado virtual, sem ações exclusivas por hover e com foco preservado após validação |
| RNF10 | Controllers sem regras financeiras ou consultas diretas ao banco; Views sem persistência; formatação e análise estática aprovadas conforme configuração definida em I0 |

## 12. Critérios de aceitação e testes

Os cenários abaixo são obrigatórios para considerar concluído o MVP. Verificar cálculos por testes de domínio; atomicidade, isolamento e concorrência com banco real de teste; fluxos críticos pela interface.

| ID | Cenário | Resultado esperado |
|---|---|---|
| CA01 | Criar usuário e primeira conta | Dashboard vazio, saldo inicial correto e dados persistidos após novo login |
| CA02 | Saldo inicial R$ 1.000; receita realizada R$ 3.000; despesa realizada R$ 200; despesa pendente R$ 100 | Saldo realizado R$ 3.800; projeção incluindo a pendência R$ 3.700 |
| CA03 | Transferir R$ 500 entre duas contas | Origem -500, destino +500, consolidado inalterado e nenhum efeito em resultado/orçamento |
| CA04 | Falha entre efeitos de uma transferência | Nenhuma perna persistida e nenhum evento de sucesso |
| CA05 | Repetir criação após resposta de rede perdida | Mesmo recurso e um único efeito financeiro |
| CA06 | Realizar, desfazer e cancelar despesa | Saldo e estados corretos a cada passo, histórico preservado |
| CA07 | Compra de R$ 100 em três parcelas | 33,34 + 33,33 + 33,33; soma exata e competências consecutivas |
| CA08 | Fechamento dia 20; compras em 19/10 e 20/10 | Faturas de outubro e novembro, respectivamente |
| CA09 | Comprar no cartão e pagar fatura integral | Compra afeta despesa por competência; pagamento reduz conta sem duplicar a despesa |
| CA10 | Duas requisições concorrentes de pagamento | Um único pagamento ativo; outra tentativa recebe resultado idempotente ou conflito |
| CA11 | Corrigir compra em fatura paga | Edição bloqueada até desfazer pagamento e reabrir; correção e novo fechamento auditados |
| CA12 | Recorrência dia 31, incluindo fevereiro e março | Último dia de fevereiro, retorno ao dia 31 em março; reexecução sem duplicar |
| CA13 | Orçamento de R$ 500; consumido R$ 300; pendente R$ 100 | Disponível R$ 100, comprometimento 80%, lista explica os valores |
| CA14 | Usuário A usa IDs de B em leitura, escrita, relações e exportação | Nenhum dado de B revelado ou alterado |
| CA15 | Duas edições com mesma versão | Primeira persiste; segunda recebe conflito, sem perda silenciosa |
| CA16 | Relatório/exportação com mais de uma página e período nas bordas | Totais iguais, todas as linhas exportadas e datas inicial/final incluídas |
| CA17 | Token expirado/reutilizado e sessão após logout | Acesso recusado conforme finalidade e revogação |
| CA18 | Arquivar conta com saldo ou pendência | Operação bloqueada; conta válida arquivada mantém histórico |
| CA19 | Restauração de backup em ambiente isolado | Usuários, operações e vínculos íntegros; saldos reconstruídos iguais |
| CA20 | Falha ao persistir auditoria | Operação financeira também revertida |
| CA21 | Pendência vence ontem no fuso do usuário | Aparece vencida; lançamento de hoje não é marcado como atrasado |
| CA22 | CSV contém descrição iniciada por fórmula | Conteúdo neutralizado sem execução ao abrir em planilha |
| CA23 | Criar lançamento, transferir, consultar orçamento e pagar fatura em celular | Fluxos concluídos em Android/Chrome e iOS/Safari, em retrato e paisagem, sem controles ocultos pelo teclado ou dependência de hover |
| CA24 | Revisar um fluxo financeiro completo no código | Rota passa pelo Controller e serviço; regra fica no Model/serviço de domínio; persistência no repositório; View recebe dados de apresentação |

Exemplo adicional de coerência: compra à vista de R$ 120 na fatura de outubro gera despesa de outubro. Pagamento de R$ 120 em novembro gera saída de caixa de novembro. O total de despesas somando outubro e novembro continua R$ 120.

## 13. Ordem de implementação

| Incremento | Entrega | Condição de conclusão |
|---|---|---|
| I0 — Fundação | Stack MVC, estrutura de módulos, banco, migrações, padrões de Clean Code, formatação, análise estática e execução local | Ambiente reproduzível, responsabilidades MVC e contratos iniciais registrados |
| I1 — Acesso e cadastros | Autenticação, isolamento, contas, categorias e primeiro acesso | CA01, CA14 e CA17 aplicáveis aprovados |
| I2 — Movimentações | Receitas, despesas, realização, transferências, saldo e auditoria | CA02–CA06, CA15, CA18 e CA20 aprovados |
| I3 — Planejamento | Pendências, recorrências e orçamento | CA12, CA13 e CA21 aprovados |
| I4 — Cartões | Compras, parcelas, ciclos, fechamento e pagamento | CA07–CA11 aprovados |
| I5 — Visão financeira | Dashboard completo, relatórios e exportação | CA16, CA22 e reconciliação de todos os indicadores aprovados |
| I6 — Preparação para uso | Compatibilidade com celular e navegadores, acessibilidade, desempenho, backup e exclusão | CA19, CA23, CA24, metas operacionais e revisão dos fluxos críticos aprovados |

I1 e I2 já permitem experimentar um núcleo utilizável; o MVP desta spec só está concluído após I6. Cada incremento atualiza este documento com evidência de validação e limitações reais. Não marcar uma entrega como validada apenas porque compilou.

## 14. Decisões em aberto e riscos

| Decisão | Premissa desta versão | Impacto de alteração |
|---|---|---|
| Pessoal, familiar ou empresarial? | Pessoal, um titular por espaço | Compartilhamento exige membros, permissões e autoria própria |
| Plataforma e arquitetura | Confirmadas: webapp MVC com Clean Code e interface responsiva para celular | PWA opcional e aplicativo nativo exigem evolução própria |
| Nome e identidade visual | Nome provisório “Controle Financeiro” | Definir antes do refinamento visual |
| Stack e hospedagem | Java 21 e Spring; execução local H2 e PostgreSQL/Docker implementadas | Hospedagem e serviços de produção precisam de homologação |
| Lançamentos manuais ou sincronizados? | Manuais | Integração bancária precisa spec de consentimento, conciliação e deduplicação |
| Recursos de crédito avançados? | Pagamento integral e parcelas sem juros calculados | Rotativo, estorno comercial e parcial exigem modelo adicional |
| Retenção e exclusão | Prazos operacionais propostos na seção 10 | Formalizar política antes da produção |

Riscos principais: despesas duplicadas por pagamento de fatura, diferenças entre competência e caixa, calendário de cartões, repetição de requisições, concorrência e vazamento entre titulares. As regras e cenários desta spec cobrem esses riscos diretamente.

As premissas de público, moeda e integrações permitem preparar o projeto e ainda não representam escolhas explicitamente confirmadas pelo usuário. A diretriz de webapp MVC com Clean Code e compatibilidade com celular foi confirmada. Mudanças nessas definições exigem revisão desta spec antes de implementar as partes afetadas.

## 15. Definição de pronto

MVP pronto significa: requisitos funcionais entregues, cenários obrigatórios aprovados, cálculos reconciliados com as fontes, isolamento de usuários verificado, execução local documentada, restauração comprovada e nenhuma pendência crítica de segurança ou integridade financeira. Funcionalidades adiadas devem estar claramente identificadas nas próximas versões da spec.

## 16. Histórico

Entrega complementar implementada e validada localmente: [PostgreSQL/Docker](spec-postgresql-docker-persistencia.md). A preparação PWA e o projeto [Android/APK](spec-android-apk.md) estão implementados; compilar e homologar o APK depende de SDK/licenças, servidor HTTPS e aparelho. As evidências e pendências operacionais estão nas specs correspondentes.

| Versão | Data | Alteração |
|---|---|---|
| 1.0 | 29/10/2026 | Proposta inicial baseada na leitura das sete specs existentes, com escopo, regras financeiras, desenho e critérios de aceitação |
| 1.1 | 29/10/2026 | Define webapp MVC, responsabilidades das camadas, princípios de Clean Code e validação responsiva em celulares; PWA passa a evolução opcional |
| 1.2 | 29/10/2026 | Implementação Java/Spring MVC, contratos de formulários e documentação de validação e operação |
