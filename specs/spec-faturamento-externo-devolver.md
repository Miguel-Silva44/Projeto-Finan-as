# Spec — Faturamento externo: devolver/cancelar e histórico de aprovações

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 517086** · Tipo: Solicitação · Solicitante: Rosicleia Sousa · Abertura: 12/03/2024
> **Status:** Design — ainda não implementado.
> **Nível de dificuldade:** 🔴 Alta — mexe em dois processos MBPM encadeados (pai/filho), exige nova transição de retorno + callback + e-mail e agregação de histórico entre instâncias.
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

> ✅ **Print do exemplo recebido** ("Faturamento de Serviços Externos - Analise Diretor: 516363").

### Anexo recebido — evidência do problema
- A tela de **Analise Diretor** mostra só os botões **Aprovar / Voltar / Realocar** → **confirma que não há "Devolver"** (o que o chamado pede).
- **Histórico Demanda** com apenas 2 linhas: *"...Solicitação"* (Flavio Constancio) e *"Analise Diretor"* (Danilo Custodio) → **confirma que as aprovações da gerência não aparecem no histórico** (registradas no processo-filho, não agregadas na demanda pai consolidada — §4.2).

## 1. Descrição do chamado

Texto do solicitante (verbatim):

> "Para o processo de faturamento externo, por favor, inserir a possibilidade de devolver, pois, hoje, quando o diretor vai aprovar, não existe possibilidade de devolver, mas apenas aprovar. Outro ponto: as aprovações da gerência não estão aparecendo no histórico. No exemplo em anexo, o Raphael Santos aprovou e não aparece no histórico."

Complemento (verbatim):

> "Danilo pediu para inserir os dois fluxos: solicitar ajustes que volta para o Gerente e o 'Cancelar' para cancelar o processo e o que abriu a demanda recebe o processo por e-mail."

O chamado tem **dois itens independentes**:

- **(a) Novas transições de fluxo** na aprovação do diretor: "Solicitar Ajustes / Devolver" (volta para o Gerente) e "Cancelar" (encerra o processo + e-mail a quem abriu a demanda).
- **(b) Correção do histórico**: aprovações da gerência não aparecem no histórico da demanda consolidada de faturamento.

## 2. Objetivo

1. Permitir que, na etapa de aprovação do diretor no faturamento de serviços externos, além de **Aprovar**, existam as ações **Solicitar Ajustes/Devolver** (retorna ao Gerente) e **Cancelar** (encerra a demanda e notifica por e-mail o solicitante que abriu a demanda).
2. Fazer com que as **aprovações da gerência** apareçam no histórico da demanda consolidada exibida ao diretor.

## 3. Análise do sistema atual (refs arquivo:linha)

O "processo de faturamento externo" é composto por **dois processos MBPM/Singular encadeados**, no pacote `processo/mbpm/fabrica/faturarContagem`:

### 3.1 Processo filho — "Faturar Serviços Externos" (`FaturarContagemDefinicao`)

Iniciado pelo Analista de Métrica (`AcsPerfil.ANALISTA_METRICA`) via `FaturarContagemMetricasPage` ("Encaminhar").
[../src/main/java/com/miranteinfo/alocpro/processo/mbpm/fabrica/faturarContagem/FaturarContagemDefinicao.java](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/fabrica/faturarContagem/FaturarContagemDefinicao.java)

- Etapas (enum `FaturarContagemTask`): `ANALISE_GERENTE`, `AJUSTAR`, `AGUARDAR_ENVIO_DIRETOR`, `ANALISE_DIRETOR`, ... (`FaturarContagemDefinicao.java:48-62`).
- `ANALISE_GERENTE` é tarefa de pessoa do perfil `GERENTE` (`FaturarContagemDefinicao.java:102-106`).
- Aprovação do gerente: `from(ANALISE_GERENTE).go("Aprovar", NOTIFICAR_APROVACAO_GERENTE).thenGo(AGUARDAR_ENVIO_DIRETOR).thenGo(ANALISE_DIRETOR)` (`FaturarContagemDefinicao.java:152-154`). O gerente também tem "Solicitar Ajuste" (volta para a pessoa criadora, `AJUSTAR`), "Rejeitar" e "Cancelar Solicitação" (`FaturarContagemDefinicao.java:155-165`).
- `ANALISE_DIRETOR` é uma **wait task** (`addWaitTask`, `FaturarContagemDefinicao.java:134`) cujas únicas transições são **"Aprovar"** e **"Rejeitar"** (`FaturarContagemDefinicao.java:167-172`). **Não existe transição de volta ao Gerente.**
- `AGUARDAR_ENVIO_DIRETOR` é uma java task agendada (cron `0 0 12 8,9,10 * ?`, "dia 8/9/10 às 12h") que dispara `FaturarContagemProcess::enviarDiretor` (`FaturarContagemDefinicao.java:121-132`).

### 3.2 Processo pai/consolidado — "Faturamento de Serviços Externos" (`FaturamentoContagemDefinicao`)

É a **demanda que o diretor visualiza/aprova**. Consolida vários pedidos-filho por solicitante.
[../src/main/java/com/miranteinfo/alocpro/processo/mbpm/fabrica/faturarContagem/FaturamentoContagemDefinicao.java](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/fabrica/faturarContagem/FaturamentoContagemDefinicao.java)

- `setName(GRUPO_GERENCIA, "Faturamento de Serviços Externos")` (`FaturamentoContagemDefinicao.java:61`).
- Etapa inicial `ANALISE_DIRETOR`, tarefa de pessoa do perfil `DIRETOR` (`FaturamentoContagemDefinicao.java:76-79`, start em `:107`).
- **Transições existentes a partir de `ANALISE_DIRETOR`:**
  - `"Aprovar"` → `EMITIR_NF` → ... → `CONCLUIDO` (`FaturamentoContagemDefinicao.java:109-118`).
  - `"Cancelar Faturamento"` → `CANCELADO`, com `addParamMotivo(true)` (`FaturamentoContagemDefinicao.java:123-125`).
- **Não existe "Devolver / Solicitar Ajustes".** É exatamente o gap do chamado: "quando o diretor vai aprovar, não existe possibilidade de devolver, mas apenas aprovar".
- Fim `CANCELADO` já dispara `FaturarContagemProcess::pagamentoCancelado` + `notifyStartToInterestedUser()` (e-mail ao interessado/criador) (`FaturamentoContagemDefinicao.java:100-102`).

### 3.3 Encadeamento pai↔filho

- `FaturarContagemProcess::enviarDiretor` instancia o processo pai `FaturamentoContagemDefinicao`, agrupa os filhos por `getDemanda().getUserCreator()`, faz `faturarContagemInstancia.setParent(instanciarProcesso)` e define o `userCreator` do pai como o solicitante (`FaturarContagemProcess.java:110-131`).
- O pai enxerga os filhos por `getInstanciasFilhas(FaturarContagemDefinicao.class)` em `FaturamentoContagemInstancia.getSolicitacoes()` (`FaturamentoContagemInstancia.java:31-33`).
- Quando o diretor aprova o pai, o listener `FaturarContagemProcess::aprovar` percorre `getSolicitacoes()` e avança cada filho que está em `ANALISE_DIRETOR` (`FaturarContagemProcess.java:134-141`). O cancelamento faz o análogo em `pagamentoCancelado` (`FaturarContagemProcess.java:161-176`), rejeitando os filhos em `ANALISE_DIRETOR`.
- A aprovação do gerente gera e-mail via `FaturarContagemProcess::notificarAprovacaoGerente` (`FaturarContagemProcess.java:56-58`).

### 3.4 Como o histórico é montado (causa do item b)

O histórico exibido na demanda passa por:
`HistoricoDemandaPage` → `HistoricoDemandaUtil.adicionarHistoricoDemanda` → `ExibirHistoricoDemandaView`.

- [../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/HistoricoDemandaPage.java](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/HistoricoDemandaPage.java) chama `HistoricoDemandaUtil.adicionarHistoricoDemanda(...)` (`HistoricoDemandaPage.java:248`).
- [../src/main/java/com/miranteinfo/alocpro/processo/HistoricoDemandaUtil.java](../src/main/java/com/miranteinfo/alocpro/processo/HistoricoDemandaUtil.java) instancia `ExibirHistoricoDemandaView` passando **apenas o `codDemanda` da instância atual** (`HistoricoDemandaUtil.java:65-70`).
- [../src/main/java/com/miranteinfo/alocpro/processo/ExibirHistoricoDemandaView.java](../src/main/java/com/miranteinfo/alocpro/processo/ExibirHistoricoDemandaView.java): o `LeitorArvore` tem como raiz **uma única demanda** (`getRaizes()` retorna `demanda`, `:100-104`) e como filhos apenas `processInstance.getTasks()` daquela instância (`:106-119`). **Não percorre instâncias filhas** (`getInstanciasFilhas`).

**Causa-raiz do item (b):** a aprovação do gerente é registrada na tarefa `ANALISE_GERENTE` do **processo-filho** (`FaturarContagem`), que é uma demanda separada. O histórico da demanda **pai consolidada** (`FaturamentoContagem`) que o diretor abre só lista as tarefas do próprio pai — logo, a aprovação da gerência (ex.: "Raphael Santos aprovou") não aparece. (Secundariamente, a flag `exibirTarefasJava` esconde tarefas Java como `NOTIFICAR_APROVACAO_GERENTE` quando `false`, `ExibirHistoricoDemandaView.java:146,175-177` — mas isso não é o núcleo do problema, que é a não-agregação pai↔filho.)

## 4. Solução proposta

### 4.1 Transições "Devolver/Solicitar Ajustes" e "Cancelar" (item a)

Alvo principal: `FaturamentoContagemDefinicao.createFlowMap()` (a tela de aprovação do diretor).

1. **Nova transição "Solicitar Ajustes" (Devolver ao Gerente)** a partir de `ANALISE_DIRETOR`:
   - Adicionar `from(ANALISE_DIRETOR).go("Solicitar Ajustes", <fim/cancelado do pai>).addParamMotivo(true)` com um `StartedTaskListener` novo (ex.: `FaturarContagemProcess::solicitarAjustesGerente`) — análogo a `aprovar`/`pagamentoCancelado` (`FaturamentoContagemDefinicao.java:109-125` como modelo; listener em `FaturarContagemProcess.java:134-176`).
   - Esse listener deve percorrer `getSolicitacoes()` e, para cada filho em `ANALISE_DIRETOR`, executar uma **nova transição de retorno ao gerente** no processo-filho.
   - **Requer nova transição no filho** `FaturarContagemDefinicao`: `from(ANALISE_DIRETOR).go("Solicitar Ajustes"/"Devolver", ANALISE_GERENTE).addParamMotivoRejeicao(true)` (hoje só há "Aprovar"/"Rejeitar", `FaturarContagemDefinicao.java:167-172`). Reaproveitar a notificação `notificarSolicitarAjuste` (`FaturarContagemProcess.java:37-54`) como referência de e-mail.
   - Como o pai é apenas o consolidado, ao devolver todos os filhos o **pai deve ser encerrado** (novo fim, ex.: `DEVOLVIDO`), evitando pedido consolidado órfão.

2. **Ação "Cancelar" com e-mail ao solicitante** — em grande parte **já existe**: `"Cancelar Faturamento"` → `CANCELADO` com `notifyStartToInterestedUser()` (e-mail ao criador/solicitante, `FaturamentoContagemDefinicao.java:100-102,123-125`). Ações necessárias:
   - Confirmar com o anexo/negócio se o rótulo deve ser **"Cancelar"** (renomear a transição) e se o texto/anexo do e-mail atende ao pedido ("recebe o processo por e-mail").
   - Garantir que `pagamentoCancelado` também trate filhos que estejam em `ANALISE_DIRETOR` — já trata (`FaturarContagemProcess.java:169-174`).

### 4.2 Correção do histórico de aprovações da gerência (item b)

Fazer o histórico da demanda **pai consolidada** incluir as aprovações dos filhos. Opções:

- **Opção B1 (recomendada) — agregar histórico dos filhos na view do pai.** Em `ExibirHistoricoDemandaView`, quando a demanda for `FaturamentoContagem` (ou, de forma genérica, quando houver instâncias filhas), incluir como sub-árvore as tarefas relevantes de cada `getSolicitacoes()` (mínimo: a aprovação `ANALISE_GERENTE`). Ponto de extensão: `getFilhos`/`getRaizes` do `LeitorArvore` (`ExibirHistoricoDemandaView.java:100-119`). Alternativamente, expor um bloco "Histórico das Solicitações" na `ViewFaturamentoContagem` (`ViewFaturamentoContagem.java:90-154`, que já percorre `getSolicitacoes()`).
- **Opção B2 — propagar a aprovação para o pai.** No `enviarDiretor`/`notificarAprovacaoGerente`, gravar uma anotação/entrada de histórico no pai (via `AnotacaoDemandaUtil`) com autor e data da aprovação do gerente. Menos invasivo na view, porém duplica dados.

Recomenda-se **B1** (fonte única de verdade, sem duplicação).

## 5. Alternativas

- **Devolver no filho em vez do pai:** adicionar "Devolver" só no `ANALISE_DIRETOR` do filho e expor uma tela de aprovação individual ao diretor (via `FaturarContagemProcess::aprovarPeloDiretor`/`rejeitarPeloDiretor`, hoje definidos em `FaturarContagemProcess.java:99-108`). Rejeitada como principal porque o diretor opera sobre a **demanda consolidada** (o pai), não item a item.
- **Histórico via B2** (propagação/anotação) — mantida como fallback caso a agregação pai↔filho na árvore se mostre custosa.

## 6. Impacto / arquivos candidatos

| Arquivo | Mudança |
|---|---|
| `processo/mbpm/fabrica/faturarContagem/FaturamentoContagemDefinicao.java` | Nova transição "Solicitar Ajustes/Devolver" + novo fim (ex.: `DEVOLVIDO`); revisar rótulo "Cancelar". |
| `processo/mbpm/fabrica/faturarContagem/FaturarContagemDefinicao.java` | Nova transição `ANALISE_DIRETOR → ANALISE_GERENTE` (retorno ao gerente). |
| `processo/mbpm/fabrica/faturarContagem/FaturarContagemProcess.java` | Novo listener `solicitarAjustesGerente` percorrendo `getSolicitacoes()`; e-mail de devolução. |
| `processo/ExibirHistoricoDemandaView.java` | Agregar histórico das instâncias-filhas na árvore (item b, opção B1). |
| `processo/mbpm/fabrica/faturarContagem/ViewFaturamentoContagem.java` | (Alternativa B1) bloco "Histórico das Solicitações" a partir de `getSolicitacoes()`. |

> Lembrete: todos os `.java` são ISO-8859-1 — editar preservando o encoding.

## 7. Riscos e decisões em aberto

- **Escopo do item (b) — confirmar no app:** com o print recebido, resta validar se a aprovação some por não-agregação pai↔filho (seção 3.4) ou também pela ocultação de tarefas Java (`exibirTarefasJava`). Verificável abrindo o histórico da demanda 516363.
- **Semântica do "Devolver" no consolidado:** o pai agrupa vários filhos de solicitantes distintos (`enviarDiretor`, `FaturarContagemProcess.java:114-131`). Decidir se "Solicitar Ajustes" devolve **todos** os filhos do consolidado ou permite seleção item a item — impacta UX e o encerramento do pai.
- **Ciclo de vida do pai ao devolver:** ao devolver os filhos ao gerente, definir se o pai é encerrado (`DEVOLVIDO`) ou reaberto no próximo ciclo agendado (cron dia 8/9/10). Recomenda-se encerrar e deixar o reenvio para o job.
- **Rótulo/e-mail de "Cancelar":** confirmar com Danilo/Rosicleia se "Cancelar Faturamento" já atende (e-mail via `notifyStartToInterestedUser`) ou se o texto/anexo precisa mudar.
- **Migração de instâncias em andamento:** alterar o `FlowMap` de processos MBPM com instâncias vivas exige atenção à versão do fluxo; validar com demandas abertas em homologação.
- **Teste do caminho agendado:** o envio ao diretor depende de cron mensal; testar sem esperar a data real (disparo manual do bloco agendado).

## 8. Referências de código (tabela)

| Ref | Arquivo:linha | Papel |
|---|---|---|
| Processo filho — definição/tasks | `FaturarContagemDefinicao.java:48-62` | Enum de etapas do "Faturar Serviços Externos". |
| Aprovação do gerente | `FaturarContagemDefinicao.java:152-154` | `ANALISE_GERENTE → "Aprovar" → NOTIFICAR_APROVACAO_GERENTE → ... → ANALISE_DIRETOR`. |
| Diretor (filho) sem "Devolver" | `FaturarContagemDefinicao.java:167-172` | `ANALISE_DIRETOR` só "Aprovar"/"Rejeitar". |
| Envio agendado ao diretor | `FaturarContagemDefinicao.java:121-132` | Cron dia 8/9/10 → `enviarDiretor`. |
| Processo pai — nome | `FaturamentoContagemDefinicao.java:61` | "Faturamento de Serviços Externos". |
| Diretor (pai) — tarefa | `FaturamentoContagemDefinicao.java:76-79,107` | `ANALISE_DIRETOR` perfil `DIRETOR`, start. |
| Diretor (pai) sem "Devolver" | `FaturamentoContagemDefinicao.java:109-125` | Só "Aprovar" e "Cancelar Faturamento". |
| Cancelar + e-mail ao interessado | `FaturamentoContagemDefinicao.java:100-102` | `CANCELADO` + `pagamentoCancelado` + `notifyStartToInterestedUser`. |
| Encadeamento pai←filhos | `FaturarContagemProcess.java:110-131` | `enviarDiretor` cria o pai e agrupa filhos por solicitante. |
| Listener "aprovar" (modelo) | `FaturarContagemProcess.java:134-141` | Avança filhos em `ANALISE_DIRETOR`. |
| Listener "cancelar" (modelo) | `FaturarContagemProcess.java:161-176` | Rejeita/cancela filhos. |
| E-mail aprovação gerente | `FaturarContagemProcess.java:56-58` | `notificarAprovacaoGerente`. |
| E-mail solicitar ajuste (modelo) | `FaturarContagemProcess.java:37-54` | `notificarSolicitarAjuste`. |
| Filhos do pai | `FaturamentoContagemInstancia.java:31-33` | `getSolicitacoes()` = `getInstanciasFilhas`. |
| Histórico — entrada | `HistoricoDemandaPage.java:248` | Chama `adicionarHistoricoDemanda`. |
| Histórico — util | `HistoricoDemandaUtil.java:65-70` | Passa só `codDemanda` da instância atual. |
| Histórico — árvore (causa item b) | `ExibirHistoricoDemandaView.java:100-119` | Raiz = 1 demanda; filhos = `getTasks()`; não agrega filhos. |
| Histórico — filtro tarefas Java | `ExibirHistoricoDemandaView.java:146,175-177` | `exibirTarefasJava` esconde tasks Java. |
| View consolidada do pai | `ViewFaturamentoContagem.java:90-154` | Já percorre `getSolicitacoes()` (ponto p/ B1 alternativo). |

---

### Anexo

✅ Print recebido ("Faturamento de Serviços Externos - Analise Diretor: 516363") — ver seção 1. Resta confirmar **no app**, abrindo o histórico dessa demanda, se o item (b) se resume à não-agregação pai↔filho (seção 3.4) ou envolve também a ocultação de tarefas Java.
