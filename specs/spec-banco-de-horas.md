# Spec — Linha de "Banco de Horas" no AlocPro

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 635159** · Tipo: Sugestão de melhoria · Solicitante: Maiara Silva (Alocpro) · Abertura: 11/06/2025
> **Status:** Design / análise de viabilidade — **ainda não implementado** (documento para decisão antes de codar).
> **Nível de dificuldade:** 🟢 **Baixa** (caminho B1 recomendado) — correção cirúrgica que reaproveita o mecanismo de Folga já existente; exige **1 verificação empírica** antes de codar. *(Caminho A = 🟢 Baixa mas semântica incorreta; Caminho B2 = 🟡 Média.)*
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

## 1. Descrição do chamado (na íntegra)

**Título:** Criação da Linha de "Banco de Horas" no alocpro.

> Atualmente, quando utilizo o banco de horas para compensar ausências, não há uma linha específica para esse registro no sistema AlocPro. Com isso, a ausência acaba sendo marcada como "não preenchida", o que aciona notificações automáticas para os gestores e pode até causar bloqueios desnecessários no sistema. Isso gera retrabalho, mal-entendidos e sobrecarga para o gestor, que precisa validar algo que já está previamente acordado.
>
> Minha sugestão é criar uma linha/tarefa chamada "Banco de Horas" no sistema AlocPro, exclusiva para registrar ausências que estão sendo compensadas com banco de horas. Essa linha permitiria distinguir de forma clara e oficial o uso do banco de horas dentro do sistema.
>
> **Benefícios esperados:** redução de notificações e bloqueios indevidos; otimização do tempo dos gestores; mais transparência/controle no uso do banco de horas; melhoria na acuracidade das informações de presença/ausência; fortalecimento da cultura de confiança e autonomia.

---

## 2. Objetivo

Permitir que um dia de ausência **compensado com banco de horas** seja registrado de forma oficial e reconhecível, de modo que **não** seja tratado como "não preenchido" — eliminando as notificações e bloqueios indevidos ao gestor.

---

## 3. Análise do sistema atual

O AlocPro já possui **dois ledgers de horas independentes**:

1. **Saldo corrido** (`ATV_DIA_ATIVIDADE_RH.qtd_minuto_saldo`, entidade `DiaAtividade`) — o **banco de horas contínuo**. Recalculado dia a dia em [`DiaAtividadeRecHumano.calcularSaldos`](../src/main/java/com/miranteinfo/alocpro/util/saldo/DiaAtividadeRecHumano.java#L152):
   ```
   novoSaldo += qtdAtv − novoEspDia − qtdMinFolga
   ```
   (minutos executados − esperado do dia − folga).

2. **Folgas / Créditos** (`RH_HISTORICO_FOLGA`, entidade `CreditoHora`) — ledger de crédito que o colaborador **acumula e gasta** para tirar dia. Já existe tela pronta **"Utilizar Folgas"** ([`UtilizarFolgasPage`](../src/main/java/com/miranteinfo/alocpro/wicket/rh/UtilizarFolgasPage.java)), que consome o saldo (`creditoHoraRhBean.getSaldo`) e **neutraliza o dia** no cálculo (a folga entra negativa e cancela o esperado).

Existe também o mecanismo de **Abono** ([`LancarAbonoRhPage`](../src/main/java/com/miranteinfo/alocpro/wicket/rh/acoes/LancarAbonoRhPage.java)): justifica a ausência gravando **minutos de atividade** no dia. Tipos de abono são **orientados a dados** — linhas de `TipoAtividade` marcadas pela propriedade `RefPropriedade.TipoAtividadeAbono` ([`TipoAtividadeDAO.retrieveAllAbono`](../src/main/java/com/miranteinfo/alocpro/dao/TipoAtividadeDAO.java#L46-L48)), criáveis via tela (`EditarTipoAtividadePage`).

### Gatilho da dor (o "não preenchida")

O alerta [`AlertaRhNaoPreenchimentoHoras`](../src/main/java/com/miranteinfo/alocpro/alerta/pessoa/AlertaRhNaoPreenchimentoHoras.java) (agendado diário, 3h30) considera "não preenchido" com base **exclusivamente** na tabela de atividades — [`AtividadeExecutadaDAO.retrieveDtUltimoPreenchimento`](../src/main/java/com/miranteinfo/alocpro/dao/AtividadeExecutadaDAO.java#L479-L488):
```sql
select cod_recurso_humano, max(data) from ATV_ATIVIDADE_EXECUTADA ...
```

---

## 4. Causa-raiz (diagnóstico)

- **Abono grava `AtividadeExecutada`** → o dia é visto como preenchido → **suprime** o alerta. Porém é **neutro no saldo** (cobre o esperado; **não debita** o banco acumulado).
- **Folga grava só `CreditoHora` (tipo `Uso`)** — confirmado em [`CreditoHoraRhBean.lancarFolgas`](../src/main/java/com/miranteinfo/alocpro/bean/rh/CreditoHoraRhBean.java#L117), **não cria `AtividadeExecutada`**.

Como o alerta olha **apenas** `ATV_ATIVIDADE_EXECUTADA`, **um dia de folga fica invisível para ele** → continua marcado como "não preenchido" e gera notificação/bloqueio.

> **Este é, muito provavelmente, exatamente o problema relatado no chamado:** a colaboradora usa o banco de horas (folga), mas o sistema não reconhece isso como preenchimento. A funcionalidade de "compensar com banco" já existe; o que falta é o alerta/marca **reconhecê-la**.

---

## 5. Alternativas de solução

| # | Abordagem | Debita o banco? | Suprime o alerta? | Esforço | Observação |
|---|---|:---:|:---:|:---:|---|
| **B1** *(recomendada)* | Tornar o alerta/marca **ciente de folga** (excluir dias com `CreditoHora Uso` da contagem de não-preenchimento) | ✅ (via folga já existente) | ✅ (após correção) | **Baixo — cirúrgico** | Ataca a causa-raiz; reaproveita 100% do banco de horas atual |
| A | Novo **tipo de abono "Banco de Horas"** (linha em `TipoAtividade`) | ❌ (infla o saldo) | ✅ | Baixo | Rápido, mas semanticamente incorreto para banco de horas |
| B2 | Novo tipo de lançamento que grava atividade **e** debita saldo | ✅ | ✅ | Médio | Redundante com o mecanismo de Folga |

---

## 6. Solução proposta (B1)

Fazer os pontos que detectam "não preenchimento" **desconsiderarem** os dias que possuem folga lançada (`CreditoHora` do tipo `Uso`), assim como já desconsideram férias.

- **Ponto principal:** contagem de dias úteis sem preencher em `AlertaRhNaoPreenchimentoHoras.recuperarDiaVazio` — subtrair/ignorar os dias com folga (análogo ao uso de `PeriodoFeriasRh`). Fonte de folgas já disponível: `CreditoHoraDAO.getUsoCredito` (usada em `AtualizadorSaldo.lerFolgas`).
- **Verificar também** a marcação visual "não preenchida" no Registro de Atividades e a regra de **bloqueio de dias** (`getDiasUteisDesbloqueados` / `SolicitarDesbloqueioDias`), para que folga também não conte ali.

Vantagens: o débito do banco continua correto (mecanismo de Folga intacto), sem novo tipo/entidade, e resolve os 3 sintomas do chamado (notificação, bloqueio, marcação).

---

## 7. Impacto / arquivos candidatos

- `alerta/pessoa/AlertaRhNaoPreenchimentoHoras.java` — ignorar dias com folga na contagem.
- `dao/AtividadeExecutadaDAO.java` — possível ajuste na consulta de preenchimento (ou filtrar folgas no chamador).
- Regra de bloqueio de dias (a mapear: `PropriedadesRH.getDiasUteisDesbloqueados` e o fluxo `desbloquearDias`).
- Marcação de "não preenchida" na UI de Registro de Atividades (a confirmar).

Sem nova tabela/entidade. Sem migração de dados obrigatória.

---

## 8. Riscos e decisões em aberto

1. **Semântica do "banco de horas":** confirmar que o esperado é **debitar** o saldo acumulado (Folga) e não apenas "perdoar" o dia (Abono). O chamado indica compensação → débito → **Folga/B1**.
2. **Quem lança?** O chamado fala em *autonomia*. Hoje "Utilizar Folgas" exige `SistemaAtividadeRecursoHumanoAcessar`; "Lançar Abono" **não** permite o colaborador lançar para si (`!isRhChamadorIgualRhAlvo`). Definir se haverá self-service (e o impacto em governança/aprovação).
3. **Alçada de aprovação:** manter validação de saldo suficiente (`isCreditoValido`) e limite do acordo.
4. **Público-alvo / regime:** quem usa **ponto eletrônico CLT (Secullum)** vive no mundo `RepOcorrencia`, não no de atividades/folga — confirmar o regime da solicitante.

---

## 9. Verificação pendente (empírica)

Reproduzir em ambiente DEV (DEV02): pegar um RH, lançar uma folga via **"Utilizar Folgas"** num dia útil e checar se aquele dia **(a)** aparece como "não preenchida" e **(b)** entra na contagem do alerta.

- **Se sim** → B1 confirmado; o fix é pontual (tornar o alerta folga-aware).
- **Se já for tratado** → a dor é de **uso/acesso** (a solicitante não usa "Utilizar Folgas"), e a solução vira **treinamento/orientação**, não código.

---

## 10. Referências de código

| Assunto | Local |
|---|---|
| Cálculo diário do saldo (banco) | [`DiaAtividadeRecHumano.calcularSaldos`](../src/main/java/com/miranteinfo/alocpro/util/saldo/DiaAtividadeRecHumano.java#L54) |
| Fachada de recálculo | [`AtualizarSaldo`](../src/main/java/com/miranteinfo/alocpro/util/saldo/AtualizarSaldo.java) / [`AtualizadorSaldo`](../src/main/java/com/miranteinfo/alocpro/util/saldo/AtualizadorSaldo.java) |
| Ledger de folgas/crédito | [`CreditoHora`](../src/main/java/com/miranteinfo/alocpro/entity/CreditoHora.java) (`RH_HISTORICO_FOLGA`) |
| Usar folga (consumir banco) | [`UtilizarFolgasPage`](../src/main/java/com/miranteinfo/alocpro/wicket/rh/UtilizarFolgasPage.java) · [`CreditoHoraRhBean.lancarFolgas`](../src/main/java/com/miranteinfo/alocpro/bean/rh/CreditoHoraRhBean.java#L117) |
| Abono (justificativa) | [`LancarAbonoRhPage`](../src/main/java/com/miranteinfo/alocpro/wicket/rh/acoes/LancarAbonoRhPage.java) · tipos via [`TipoAtividadeDAO.retrieveAllAbono`](../src/main/java/com/miranteinfo/alocpro/dao/TipoAtividadeDAO.java#L46) |
| Alerta de não preenchimento | [`AlertaRhNaoPreenchimentoHoras`](../src/main/java/com/miranteinfo/alocpro/alerta/pessoa/AlertaRhNaoPreenchimentoHoras.java) · [`retrieveDtUltimoPreenchimento`](../src/main/java/com/miranteinfo/alocpro/dao/AtividadeExecutadaDAO.java#L479) |

---

*Investigação e diagnóstico realizados sobre a branch `desenv`. Nenhuma alteração de código foi feita — este documento embasa a decisão do caminho (B1 / A / B2) antes da implementação.*
