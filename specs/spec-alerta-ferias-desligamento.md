# Spec — Alerta de férias em andamento no desligamento

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 697015** · Tipo: Solicitação · Solicitante: Talia Lopes (Departamento de Pessoal) · Abertura: 30/01/2026
> **Status:** ⏸️ **Descartado por ora:** branch `feat/tarefas-prioritarias` apagada em 09/10/2026 a pedido do Aderbal (tarefa simples, refazer se precisar; último commit era `6dbd1f555`). A spec continua valendo como desenho.
> **Nível de dificuldade:** 🟡 Média — o mecanismo de detecção de férias já existe e é reutilizável; o esforço está em espalhar o alerta para o início do fluxo e em notificar o colaborador (padrão novo).
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

> ✅ **IMPLEMENTADO (MVP)** — commit local na branch `feat/tarefas-prioritarias`, refeita sobre a desenv em 30/09/2026. Alerta **não-bloqueante** na abertura do desligamento (`DesligamentoPage.onSelecionar`): ao selecionar um colaborador com **férias solicitadas em andamento**, avisa o DP com o período (reusa `PeriodoFeriasRhBean.MapaFerias.listarFeriasSolicitadas`). O bloqueio "duro" já existente no encerramento do acordo (`DesligamentoDefinicao:522`) permanece. `mvn compiler:compile` OK.
> **Fora do MVP (decisão de produto pendente):** ciência ao próprio colaborador (conflita com `removePessoa(rh)`) e cancelamento automático das férias (risco financeiro). Ver §7.

## 1. Descrição do chamado
Transcrição verbatim da solicitação:

> "Gostaria de solicitar que o alocpro identifique que um colaborador demitido ou que pediu para sair possui solicitação de férias em andamento. Esse Bloqueio/alerta seria para evitarmos pagamentos em duplicidade das férias e também, para que o colaborador tenha ciência de que durante o cumprimento do aviso, as férias serão canceladas."

Em outras palavras, ao abrir/tramitar um **desligamento** de um colaborador que possui uma **solicitação de férias em andamento** (processo "Marcar Férias" ainda não encerrado), o AlocPro deve:
1. **Detectar** o cruzamento desligamento × férias em andamento;
2. **Alertar/bloquear** quem conduz o desligamento, para evitar pagamento em duplicidade (rescisão paga férias proporcionais/vencidas, e a solicitação de férias paga o mesmo direito);
3. **Dar ciência ao colaborador** de que, durante o aviso prévio, as férias solicitadas serão canceladas.

## 2. Objetivo
Inserir, no fluxo de desligamento, uma verificação de **férias em andamento** já no momento da abertura/solicitação (e não apenas no encerramento do acordo, onde a checagem já existe), produzindo:
- um **alerta visível** (e, se decidido, um **bloqueio/confirmação**) para o responsável pelo desligamento;
- uma **notificação ao colaborador** informando que as férias solicitadas serão canceladas durante o aviso.

Reaproveitando ao máximo a infraestrutura existente de consulta de férias solicitadas.

## 3. Análise do sistema atual (com refs arquivo:linha)

### 3.1 Fluxo de desligamento
- Definição do processo (tarefas e transições): [DesligamentoDefinicao.java:99](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L99). Enum de tarefas em [DesligamentoDefinicao.java:145](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L145). Fluxo inicia em `NOTIFICAR_DESLIGAMENTO` ([:310](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L310)) e segue para `TAREFA_ANALISE_RH` / `TAREFA_DEFINIR_PARAMETROS`.
- Tela inicial "Encaminhar Solicitação": [DesligamentoPage.java:81](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L81). O `onSubmit()` valida e salva a solicitação em [DesligamentoPage.java:114](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L114).
- **Precedente de alerta/erro na abertura**: a própria tela já verifica se existe outro desligamento aberto e emite `error(...)` / `ValidationError` — ver [DesligamentoPage.java:97](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L97) e o método `verificarExistenciaProcessoDesligamento` em [DesligamentoPage.java:132](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L132). Esse é o ponto natural para acrescentar a checagem de férias em andamento.
- Notificação inicial e transição de estado: `notificarDesligamento` em [DesligamentoProcess.java:139](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L139); `notificarSolicitacao` em [DesligamentoProcess.java:282](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L282).
- Data de saída e tipo de demissão da instância: `getDataSaida()` em [DesligamentoInstancia.java:46](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoInstancia.java#L46); `getTipoDemissao()` em [DesligamentoInstancia.java:58](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoInstancia.java#L58). Enum `TipoDemissao` (cobre `pedidoEmpregado`, `semJustaCausa`, etc.) em [DesligamentoInstancia.java:209](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoInstancia.java#L209) — atende ao "demitido ou que pediu para sair".

### 3.2 Verificação de férias JÁ EXISTENTE (parcial) no desligamento
Já existe uma checagem de férias, porém **tardia** (na transição de encerramento do acordo) e **apenas bloqueante**, sem cancelamento nem ciência ao colaborador:

`FormularioEncerrarAcordo.validarTransicao`, em [DesligamentoDefinicao.java:522](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L522):
```java
for (TBInstanciaProcesso instanciaProcesso : PeriodoFeriasRhBean.listarFormulariosFerias(new PeriodoDias(dataFim.minusDays(45), null), rhFoco)) {
    if(!instanciaProcesso.getSituacao().isEnd()){
        validationResult.addErro("Existem férias solicitadas que não foram encerradas: "+instanciaProcesso.getDescription());
    }
}
```
Ou seja: a lógica de "identificar férias em andamento" já está implementada e comprovada — falta **antecipá-la** para a abertura e transformá-la em alerta + ciência ao colaborador.

### 3.3 Como o sistema representa "férias em andamento"
- Processo "Marcar Férias": [AgendarFeriasDefinicao.java:102](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasDefinicao.java#L102). Estados (enum `AgendarFeriasTask`) em [AgendarFeriasDefinicao.java:126](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasDefinicao.java#L126): `AnaliseGerente`, `AnaliseDP`, `AguardarEnvioDP`, `EnviarParaAContabilidade`, `ConferirLiberar`, `AgendarPagamento`, `LiberarPagamento`, `AguardarFerias`, `Concluido`, `Cancelado`...
- **Definição canônica de "férias solicitadas / em andamento"**: `PeriodoFeriasRhBean.getTarefasFeriasSolicitadas()` em [PeriodoFeriasRhBean.java:340](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L340) — retorna exatamente as tarefas não-encerradas (`AnaliseGerente`, `AnaliseDP`, `AguardarEnvioDP`, `EnviarParaAContabilidade`, `ConferirLiberar`, `AgendarPagamento`).
- Consulta pronta por RH: `MapaFerias.listarFeriasSolicitadas(rh)` em [PeriodoFeriasRhBean.java:616](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L616), que usa `Flow.getProcessDefinition(AgendarFeriasDefinicao.class).getDataService().getInstanciasPessoaAlvoNoEstado(rh, getTarefasFeriasSolicitadas())` e devolve `List<AgendarFeriasInstancia>` (tem `getDataInicioFerias()`, `getPeriodoFerias()`, `getDescription()`).
- Consulta alternativa por período (a usada no encerramento): `PeriodoFeriasRhBean.listarFormulariosFerias(periodo, rh)` em [PeriodoFeriasRhBean.java:104](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L104) → `DmdDemandaDAO.consultarFerias` em [DmdDemandaDAO.java:392](../src/main/java/com/miranteinfo/alocpro/dao/DmdDemandaDAO.java#L392).
- Férias já **efetivadas/lançadas** (não o processo em trâmite) ficam na entidade `PeriodoFerias` (tabela `RH_HISTORICO_FERIAS`): [PeriodoFerias.java:24](../src/main/java/com/miranteinfo/alocpro/entity/PeriodoFerias.java#L24), com `dataInicio`/`dataFim`/`getPeriodo()` e `comparaData(...)` em [PeriodoFerias.java:84](../src/main/java/com/miranteinfo/alocpro/entity/PeriodoFerias.java#L84) — úteis para cruzar com a data de saída/aviso.

### 3.4 Cancelamento de férias (para a "ciência de que serão canceladas")
- Cancelamento programático de uma solicitação de férias: `AgendarFeriasProcess.cancelar(AgendarFeriasInstancia)` em [AgendarFeriasProcess.java:453](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasProcess.java#L453) (exclui o período em `RH_HISTORICO_FERIAS`, recalcula folha e saldo). Há também transição de fluxo `ACAO_CANCELAR` para cada tarefa em [AgendarFeriasDefinicao.java:333](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasDefinicao.java#L333).
- Observação: o chamado pede **alerta/ciência**, não necessariamente o cancelamento automático. O cancelamento efetivo hoje é manual/decisório; o design abaixo mantém isso e apenas dá visibilidade.

### 3.5 Notificação ao colaborador — atenção
As notificações do desligamento **removem o colaborador foco** da lista de destinatários (padrão intencional): ver `mail.removePessoa(rh)` em `notificarDesligamento` ([DesligamentoProcess.java:157](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L157)) e repetido nas demais notificações. Logo, "dar ciência ao colaborador" é um **padrão novo** e precisa de decisão explícita (e-mail dedicado ao próprio RH foco, ou anotação visível para a pessoa).

### 3.6 Infra de alertas (opção complementar)
Existe framework de alertas de RH: `AlertaDesligamento` ([AlertaDesligamento.java:31](../src/main/java/com/miranteinfo/alocpro/alerta/pessoa/AlertaDesligamento.java#L31)) e congêneres de férias (`AlertaFeriasAVencer`, `AlertaFeriasAPerder`). Poderia hospedar um alerta persistente "desligamento com férias em andamento", mas é um caminho mais pesado que o alerta em tela.

## 4. Solução proposta (design + pontos de alteração/ganchos)

Abordagem recomendada em duas camadas, priorizando reuso:

### 4.1 Serviço de detecção (reuso)
Criar um helper (ex.: método estático em `DesligamentoProcess` ou em `PeriodoFeriasRhBean`) `listarFeriasEmAndamento(RecursoHumano rh)` que apenas **encapsula** a consulta já existente:
- reutilizar `PeriodoFeriasRhBean.MapaFerias.listarFeriasSolicitadas(rh)` ([PeriodoFeriasRhBean.java:616](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L616)), que já filtra pelos estados de "férias solicitadas".
- retornar as `AgendarFeriasInstancia` em andamento (com período e descrição para exibir).

Nenhuma nova regra de negócio de estado é necessária — a definição de "em andamento" já está centralizada em `getTarefasFeriasSolicitadas()`.

### 4.2 Alerta na abertura do desligamento (gancho principal)
No `DesligamentoPage`, ao selecionar o colaborador e/ou no `onSubmit`, invocar o serviço 4.1 e, havendo férias em andamento, exibir aviso — espelhando o padrão já existente de "Já existe um processo de desligamento":
- Ponto de exibição por seleção: dentro do `onSelecionar`/validador do `AutocompletePessoaField` em [DesligamentoPage.java:154](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L154) (mesmo local onde hoje há `error(new ValidationError(...))` para desligamento duplicado).
- Ponto de validação no envio: `onSubmit` em [DesligamentoPage.java:114](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L114).
- Mensagem sugerida: *"O colaborador possui solicitação de férias em andamento (período X a Y). Durante o aviso prévio essas férias devem ser canceladas para evitar pagamento em duplicidade."*

**Decisão de bloqueio vs. alerta** (ver §7): recomenda-se **alerta não-bloqueante com confirmação** (permitir prosseguir cientemente), pois há cenários legítimos.

### 4.3 Reforço no encerramento do acordo (já existe)
Manter/estender a checagem existente em `FormularioEncerrarAcordo.validarTransicao` ([DesligamentoDefinicao.java:522](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L522)). Se necessário, alinhar a janela de datas e a mensagem com a nova detecção 4.1. Isso já cobre o "bloqueio" contra pagamento em duplicidade no ponto crítico (encerrar acordo → cálculo da rescisão).

### 4.4 Ciência ao colaborador
Opção recomendada (menor risco): incluir a informação no e-mail que já vai ao colaborador quando **ele mesmo** pediu desligamento (`TipoDemissao.pedidoEmpregado`), e/ou registrar uma **anotação visível para a pessoa** (padrão `RHAnotacao.setVisivelParaPessoa(true)`, usado em [PeriodoFeriasRhBean.java:198](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L198) e [DesligamentoProcess.java:127](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L127)). Requer decisão de produto, dado o `removePessoa(rh)` padrão (§3.5).

## 5. Alternativas
- **A) Somente antecipar o alerta na tela (4.1 + 4.2)** e manter o bloqueio no encerramento (4.3). Menor esforço; entrega o essencial (detecção + alerta + evita duplicidade). *Recomendada como MVP.*
- **B) A + cancelamento automático das férias** ao aprovar o desligamento, via `AgendarFeriasProcess.cancelar` ([AgendarFeriasProcess.java:453](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasProcess.java#L453)) ou transição `ACAO_CANCELAR`. Mais automação, porém alto risco financeiro (cancelar férias já em pagamento) — requer regras finas por estado.
- **C) Alerta persistente via framework `DefinicaoAlertaRh`** ([AlertaDesligamento.java:31](../src/main/java/com/miranteinfo/alocpro/alerta/pessoa/AlertaDesligamento.java#L31)). Bom para visibilidade contínua no painel, mas mais custoso que o alerta em tela e fora do fluxo imediato pedido.

## 6. Impacto / arquivos candidatos
| Arquivo | Alteração prevista |
| --- | --- |
| [DesligamentoPage.java](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java) | Chamar a detecção e exibir alerta/confirmação (seleção do colaborador e/ou `onSubmit`). Principal ponto de UI. |
| [DesligamentoProcess.java](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java) | (Opc.) Helper de detecção; (Opc.) incluir ciência ao colaborador em `notificarDesligamento`. |
| [PeriodoFeriasRhBean.java](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java) | (Opc.) Expor/encapsular `listarFeriasEmAndamento` reusando `listarFeriasSolicitadas`. |
| [DesligamentoDefinicao.java](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java) | (Opc.) Alinhar mensagem/janela da checagem já existente no encerramento do acordo (linha 522). |
| [AgendarFeriasProcess.java](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasProcess.java) | Somente se adotada a Alternativa B (cancelamento automático). |

Fontes `.java` são ISO-8859-1 — apenas leitura nesta análise; nenhuma alteração aplicada.

## 7. Riscos e decisões em aberto
- **Bloquear ou apenas alertar?** O chamado usa "Bloqueio/alerta". Recomenda-se alerta com confirmação na abertura + o bloqueio já existente no encerramento; confirmar com o DP.
- **Cancelamento automático das férias?** Risco financeiro alto se a solicitação já estiver em `AgendarPagamento`/`LiberarPagamento`/`Concluido`. Definir se é manual (recomendado) ou automático, e em quais estados.
- **Ciência ao colaborador conflita com `removePessoa(rh)`** (§3.5): decidir canal (e-mail dedicado ao RH foco vs. anotação visível para a pessoa) e em quais tipos de demissão notificar.
- **Janela de cruzamento de datas:** a checagem existente usa `dataFim - 45 dias`; para a abertura talvez interesse considerar todo o período do aviso prévio (data do aviso → data de saída). Definir a janela correta em relação a `getDataSaida()` / data de aviso.
- **Férias solicitadas vs. já lançadas:** "em andamento" = processo `AgendarFeriasInstancia` não encerrado (§3.3). Se o escopo incluir férias **já lançadas** dentro do período de aviso, será preciso também cruzar `PeriodoFerias` (`RH_HISTORICO_FERIAS`). Confirmar escopo com a solicitante.
- **Contratos não-CLT:** processo de férias exige CLT ([AgendarFeriasDefinicao.java:854](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasDefinicao.java#L854)); o alerta é irrelevante para PJ/estágio — filtrar para evitar ruído.

## 8. Referências de código
| Assunto | Local |
| --- | --- |
| Tela inicial do desligamento ("Encaminhar Solicitação") | [DesligamentoPage.java:81](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L81) |
| `onSubmit` (validação/envio) — gancho de alerta | [DesligamentoPage.java:114](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L114) |
| Precedente: verifica desligamento já existente | [DesligamentoPage.java:132](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L132) |
| Seleção do colaborador (onSelecionar/validador) | [DesligamentoPage.java:154](../src/main/java/com/miranteinfo/alocpro/wicket/processo/mbpm/dp/desligamento/DesligamentoPage.java#L154) |
| Definição do processo de desligamento | [DesligamentoDefinicao.java:99](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L99) |
| **Checagem de férias já existente (encerrar acordo)** | [DesligamentoDefinicao.java:522](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoDefinicao.java#L522) |
| Notificação inicial do desligamento / `removePessoa(rh)` | [DesligamentoProcess.java:139](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L139), [:157](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L157) |
| Data de saída / tipo de demissão da instância | [DesligamentoInstancia.java:46](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoInstancia.java#L46), [:209](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoInstancia.java#L209) |
| Definição de "férias solicitadas/em andamento" (estados) | [PeriodoFeriasRhBean.java:340](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L340) |
| Consulta de férias solicitadas por RH (reuso) | [PeriodoFeriasRhBean.java:616](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L616) |
| Consulta de formulários de férias por período | [PeriodoFeriasRhBean.java:104](../src/main/java/com/miranteinfo/alocpro/bean/rh/PeriodoFeriasRhBean.java#L104) → [DmdDemandaDAO.java:392](../src/main/java/com/miranteinfo/alocpro/dao/DmdDemandaDAO.java#L392) |
| Processo "Marcar Férias" e estados | [AgendarFeriasDefinicao.java:102](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasDefinicao.java#L102), [:126](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasDefinicao.java#L126) |
| Cancelamento programático de férias | [AgendarFeriasProcess.java:453](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/agendarferias/AgendarFeriasProcess.java#L453) |
| Entidade de férias lançadas (`RH_HISTORICO_FERIAS`) | [PeriodoFerias.java:24](../src/main/java/com/miranteinfo/alocpro/entity/PeriodoFerias.java#L24) |
| Anotação visível para a pessoa (ciência ao colaborador) | [DesligamentoProcess.java:127](../src/main/java/com/miranteinfo/alocpro/processo/mbpm/dp/desligamento/DesligamentoProcess.java#L127) |
| Framework de alertas de RH (alternativa C) | [AlertaDesligamento.java:31](../src/main/java/com/miranteinfo/alocpro/alerta/pessoa/AlertaDesligamento.java#L31) |
