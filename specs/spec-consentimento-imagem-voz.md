# Spec — Campo de identificação de consentimento de uso de imagem e voz

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 754976** · Tipo: Solicitação · Solicitante: Luís Guilherme Rodrigues de Farias
> (Administrativo-Financeiro/Governança) · Abertura: 18/08/2026 00:36
> **Status em 01/09/2026:** o solicitante é o **Encarregado de Dados (DPO)** da empresa e já respondeu
> as 10 perguntas (§15). **Seis das oito definições dele estão implementadas**, compilando, na `desenv`
> local, no commit `ad0482a7a`, **sem push**. Falta validar em tela.
> **Em aberto:** edição em massa (depende do aval do Anderson e de decisão de desenho), menor de idade
> (depende do DP confirmar se há algum na base) e terceirizado, que **não existe no sistema**.
> **Nível de dificuldade:** 🟢 Baixa no campo em si, 🟡 Média na trilha de auditoria,
> 🔴 Alta no que depende de terceiros.
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

> ⚠️ **Duas viradas de desenho, nesta ordem. Leia §13, §14 e §15 antes de qualquer seção anterior.**
> As seções 3, 4, 7, 8 e 11 descrevem a **primeira** versão, que guardava o consentimento em
> `RefPropriedade` / `PRP_RH`, e foi **descartada** (§13). A §14 corrige dois defeitos do que chegou a
> ser publicado. A §15 é o estado atual, com as respostas do Encarregado.

---

## 1. Descrição do chamado (na íntegra)

> Prezados, bom dia!
>
> No contexto do programa de conformidade à LGPD, a Mirante coleta dos colaboradores o Termo de
> Autorização de Uso de Imagem e Voz, que pode ser concedido, recusado ou revogado a qualquer tempo.
> Hoje não temos, no cadastro, uma forma de identificar rapidamente a situação de cada colaborador
> antes de qualquer divulgação (site, redes sociais, materiais institucionais), o que é indispensável
> para evitar publicação sem autorização.
>
> Solicito, por isso, a criação de um campo indicando a situação da autorização, no mesmo estilo do
> campo já existente "Notificar aniversário por e-mail", exibido na aba Dados Gerais do cadastro.
>
> Especificação do campo:
>
> 1) Campo principal — "Autorização de uso de imagem e voz", com os valores: Sim / Não. Valor padrão
> "Sim" para novos cadastros.
>
> 2) Idealmente, para atender à exigência de consentimento específico da LGPD, desdobrar em dois
> campos independentes: "Uso interno" (Sim/Não) e "Uso externo — Redes sociais e materiais públicos"
> (Sim/Não), já que o colaborador pode autorizar um e recusar o outro. Valor padrão "Sim" para novos
> cadastros.
>
> 3) Se viável, um campo para anexar ou vincular o termo assinado eletronicamente, servindo de
> evidência.
>
> Quanto às permissões, sugiro que o campo seja editável apenas pelos perfis de DP e Compliance,
> mantendo-se visível (somente leitura) aos demais perfis que realizam divulgação, para consulta
> prévia.
>
> A finalidade é permitir a checagem obrigatória da situação antes de qualquer publicação e assegurar
> a rastreabilidade das autorizações e revogações, em atendimento à LGPD.
>
> Fico à disposição para detalhar os requisitos ou validar a proposta de tela.
>
> Atenciosamente,
> Luís Guilherme Rodrigues de Farias

## 2. Onde esse consentimento vive hoje: só em papel

Levantado no código. **Não existe hoje nenhum campo de consentimento de imagem e voz no sistema.**
A autorização existe apenas como cláusula em documento assinado:

| Onde | Situação |
|---|---|
| Termos admissionais **legados** (`termo_fabrica.docx`, `termo_sicoob.docx`) | seção `TERMO DE AUTORIZAÇÃO DE USO DE IMAGEM E VOZ` **dentro** do termo principal |
| Termos **novos aprovados** (ex. `CLT_SICOOB_Hibrido.docx`) | seção **removida**; virou documento separado `Termo_Autorizacao_Imagem_Voz.docx`, ainda **pendente** |
| Cadastro do colaborador | **nada** |

Ver `specs/feito-spec-termos-admissionais.md` §3 e §5.

Existe um fluxo de LGPD no sistema, mas é **de candidato, não de colaborador**: o Termo de
Participação em Processo Seletivo (`TermoParticipacaoSelecaoPage`, modelo `fichaLGPD` em
`SubProcessoSelecaoInstancia.criarFormularioTermoLGPD`). É consentimento de outra finalidade e não
serve para este chamado, mas é precedente útil de como a empresa já coleta consentimento.

**Consequência prática:** hoje, para saber se pode publicar a foto de alguém, é preciso abrir o
documento admissional da pessoa. É exatamente a dor descrita no chamado.

## 3. O padrão que ele citou, e que de fato é o certo

O campo "Notificar aniversário por e-mail" que ele usa de referência funciona assim:

**Exibição**, em [ExibirPessoaDadosGerais.java:46](../src/main/java/com/miranteinfo/alocpro/view/pessoa/ExibirPessoaDadosGerais.java#L46):

```java
gerador.addColuna("Notificar aniversário por e-mail",
        getFacadePropriedade().getPropriedadesRH(getRecursoHumano()).isAlertarAniversario() ? "Sim" : "Não");
```

**Leitura da propriedade**, em [PropriedadesRH.java:36](../src/main/java/com/miranteinfo/alocpro/prp/PropriedadesRH.java#L36):

```java
public boolean isAlertarAniversario() {
    return getFacadePropriedade().getPropriedadeRH(refRh_,
            RefPropriedade.PessoaEnviarEmailAniversario).asBoolean(true);
}
```

**Edição**, em [EditarCadastroRhDadosCadastroPanel.java:101](../src/main/java/com/miranteinfo/alocpro/wicket/rh/cadastro/EditarCadastroRhDadosCadastroPanel.java#L101):

```java
add(new CheckBox("notificacaoAniversario", notificacaoAniversario)
        .setEnabled(podeEditarDadosCadastro || podeEditarFoto));
```

**Armazenamento:** enum `RefPropriedade`, definição em `PRP_DEFINICAO_PROPRIEDADE`, valor por pessoa
em `PRP_RH` (entidade `PropRh`).

### Por que esse padrão resolve o "valor padrão Sim" de graça

O `.asBoolean(true)` devolve `true` quando **não existe linha** em `PRP_RH` para aquela pessoa. Ou
seja: o padrão "Sim" pedido no item 1 sai sem escrever uma única linha para os colaboradores já
cadastrados, e sem migração de dados. Quem nunca mexeu no campo já nasce "Sim".

## 4. Design proposto

### Itens 1 e 2: um campo ou dois?

O item 1 pede um campo, o item 2 pede que ele seja desdobrado em dois "idealmente". **Os dois juntos
são contraditórios**, e implementar os três (geral, interno, externo) cria a possibilidade de estados
inconsistentes (geral "Não" com interno "Sim").

**Proposta: implementar apenas o item 2**, os dois campos independentes, e não criar o campo geral.
Motivos:

- é o que a LGPD de fato exige (consentimento específico por finalidade), e ele mesmo diz isso
- o campo geral vira redundante: "autorizado" passa a ser "interno E externo"
- evita o terceiro estado inconsistente

Se ele quiser mesmo os três, o geral tem que ser **derivado e somente leitura**, nunca editável.

### Propriedades novas

| Sigla (`RefPropriedade`) | Rótulo na tela | Default |
|---|---|:-:|
| `PessoaAutorizaImagemVozInterno` | Uso de imagem e voz: interno | `true` |
| `PessoaAutorizaImagemVozExterno` | Uso de imagem e voz: externo (redes sociais e materiais públicos) | `true` |

Em `PropriedadesRH`, no mesmo formato do `isAlertarAniversario()`:

```java
public boolean isAutorizaImagemVozInterno() {
    return getFacadePropriedade().getPropriedadeRH(refRh_,
            RefPropriedade.PessoaAutorizaImagemVozInterno).asBoolean(true);
}
```

### Item 3: o termo assinado como evidência

O mecanismo natural já existe: **anotação com anexo**. É o que o fluxo de contratação já usa em
`gerarEAnexar` para anexar o termo admissional gerado.

Proposta: um **tipo de anotação próprio** (`ARQ_TIPO_ANOTACAO`), por exemplo
`Termo de Autorização de Imagem e Voz`, e o campo da tela vira um link para as anotações desse tipo
da pessoa. Não é um campo de upload novo, é reuso do que já existe.

⚠️ Cuidado: `ARQ_TIPO_ANOTACAO` está sendo alterada agora pela branch `feat/resumo-anotacao-obrigatorio`
(coluna `se_resumo_obrigatorio`, script v318). Coordenar para não colidir.

## 5. Permissões

O padrão da base é `acs_funcionalidade` mais o enum `DireitoProjeto`, checado por
`possuiAcessoPorLotacao(...)`. Exemplo real em
[ExibirPessoaContatosEmergencia.java:22](../src/main/java/com/miranteinfo/alocpro/view/pessoa/ExibirPessoaContatosEmergencia.java#L22):

```java
return isRhChamadorIgualRhAlvo() || possuiAcessoPorLotacao(DireitoProjeto.PessoaLotadaContatoEmergenciaVer);
```

Proposta, seguindo exatamente esse desenho:

| Funcionalidade | Efeito |
|---|---|
| `PessoaImagemVozVer` | vê o campo (somente leitura). Quem faz divulgação recebe esse |
| `PessoaImagemVozEditar` | edita. Só DP e Compliance recebem |

**Sem o direito de ver, o campo não aparece.** Isso é mais seguro que exibir para todo mundo, e ainda
atende o pedido dele, que é dar consulta prévia a quem publica.

Atribuir os direitos aos perfis é configuração, não código. Precisa saber quais perfis são "DP" e
"Compliance" hoje (§9).

## 6. O que ele pediu e o framework atual NÃO entrega

Ele encerra pedindo **"assegurar a rastreabilidade das autorizações e revogações"**. Esse é o
requisito mais importante do chamado, porque é ele que sustenta a defesa jurídica, e é o único que o
desenho acima **não cumpre**.

Verificado no código: existe `PrpProjetoHistorico` (tabela `PRP_PROJETO_HISTORICO`), mas o histórico
é **só para propriedade de projeto**. Para pessoa existe apenas `PropRh` (`PRP_RH`), que guarda o
**valor corrente**. Uma revogação sobrescreve o valor anterior e não deixa rastro de quem mudou,
quando, nem qual era o valor antes.

Ou seja: implementar só os itens 1 a 3 entrega a **consulta antes de publicar**, que já é a metade
prática, mas **não entrega a rastreabilidade**.

### Três saídas, da mais barata à mais correta

| # | Saída | Custo | Entrega rastreabilidade? |
|:-:|---|:-:|:-:|
| A | Aceitar valor corrente, e tratar as anotações do item 3 como a evidência | 🟢 | parcial: só o que foi assinado, não as mudanças na tela |
| B | Gerar uma **anotação automática** a cada mudança do campo, com autor, data e valores | 🟡 | sim, e reusa mecanismo existente |
| C | Criar `PRP_RH_HISTORICO` espelhando `PrpProjetoHistorico` | 🔴 | sim, e serve para toda propriedade de pessoa |

**Recomendação: B.** Entrega o que a LGPD exige, reusa o mecanismo de anotação que o item 3 já vai
precisar, e não cria tabela nova nem mexe no framework de propriedades, que é usado pelo sistema
inteiro.

## 7. Arquivos que serão tocados

> ⚠️ Lista superada pela §13.

| Arquivo | Mudança |
|---|---|
| `prp/RefPropriedade.java` | duas entradas novas no enum |
| `prp/PropriedadesRH.java` | dois getters, padrão do `isAlertarAniversario()` |
| `view/pessoa/ExibirPessoaDadosGerais.java` | duas colunas, com guarda de permissão |
| `wicket/rh/cadastro/EditarCadastroRhDadosCadastroPanel.java` + `.html` | dois `CheckBox` |
| `wicket/rh/cadastro/EditarCadastroRhPage.java` | passar os dois `IModel<Boolean>` para o painel |
| `acs/DireitoProjeto.java` | duas funcionalidades novas |
| `sql/update_v318.sql` | ver §8 |

## 8. SQL: este chamado é o **v318**

> ⚠️ A numeração e a análise de colisão continuam valendo. O **conteúdo** do script mudou, ver §13.

A numeração segue a **ordem de subida das queries**, não a ordem em que as branches foram criadas.
Como esta feature sobe primeiro, ela fica com o próximo número livre depois do último commitado na
`desenv`, que é o `update_v317.sql`. Logo: **`sql/update_v318.sql`**.

⚠️ **Isso colide com branches que já commitaram script.** Três features ainda não subidas carregam
números que agora vão ter que mudar:

| Chamado | Feature | Branch | Script hoje | Ação |
|---|---|---|---|---|
| 577701 | `se_resumo_obrigatorio` em `ARQ_TIPO_ANOTACAO` | `feat/resumo-anotacao-obrigatorio` | `update_v323.sql` (era v321, depois v324; trocado com o 569401 em 01/10) | ✅ resolvido, commit `35e3661c6` |
| 569401 | tabela `prj_projeto_area_empresa` | `feat/tarefas-agosto` | `update_v319.sql` | sem colisão; renumerar só se a ordem mudar |
| 712036 | auxílio home office na contratação Híbrida | `desenv` (veio da `feat/termos-admissionais`, apagada em 14/09) | `update_v319.sql` | renumerado em 10/09: sobe primeiro, tomou o `v319` |

A do 577701 colidia de frente: tinha um `update_v318.sql` commitado com conteúdo diferente, e no
merge o git veria dois arquivos no mesmo caminho. Foi renumerada para `v321`, número livre em todas
as branches, no commit `d183f40d8`; em 30/09 virou v324 (`d85fc85fc`), porque a produção está na v322, e em 01/10 trocou de número com o vínculo e ficou v323 (`35e3661c6`, o script já nasce com esse número), porque sobe primeiro. Era a segunda renumeração dela, depois de já ter saído de v319
para v318 em 17/08.

O rollback de desenvolvimento fica em `sql/_ROLLBACK_dev_v318.sql`, que lista as quatro features e
marca quais ainda estão sem número definitivo.

Conteúdo, no padrão de [update_v316.sql:13](../sql/update_v316.sql#L13):

```sql
INSERT INTO PRP_DEFINICAO_PROPRIEDADE(
       sigla, nome, descricao, cod_algoritmo_resolucao, classe_java_tipo_propriedade,
       texto_atributos_propriedade, se_lista, valor_default, cod_categoria_propriedade)
VALUES ('PessoaAutorizaImagemVozInterno', ..., 3, 'TipoParametroBoolean', null, false, 'true', null);
-- idem para PessoaAutorizaImagemVozExterno

INSERT INTO dbo.acs_funcionalidade(cod_dominio_perfil, sigla, nome, descricao, se_deve_possuir_para_delegar)
VALUES (2, 'PessoaImagemVozVer', ..., false);
-- idem para PessoaImagemVozEditar
```

`valor_default` fica `'true'` para casar com o `.asBoolean(true)` do Java. Os dois têm que concordar.

## 9. Perguntas ao solicitante

1. ~~**Um campo ou dois?**~~ **Resolvido, não precisa perguntar.** O item 2 diz "desdobrar em dois
   campos independentes", ou seja, o item 1 descreve a intenção e o item 2 a forma. São dois campos,
   sem campo geral. Um geral além deles ficaria redundante e permitiria estado inconsistente, por
   exemplo geral "Não" com interno "Sim".
2. **Quais perfis são "DP" e "Compliance"** hoje no AlocPro? Proposta feita e implementada no script:
   editam os mesmos perfis que já editam o cadastro (DP), mais 121 Singular - Compliance Delivery,
   124 Analista de Governança e 126 Gerente de Governança; só consultam 80 Gerente de Marketing,
   88 Assistente de Marketing e 89 Agência Marketing. **Aguardando o aval dele sobre a lista.**
3. ~~**A rastreabilidade é requisito de entrega ou desejável?**~~ **Resolvido pela anotação de
   auditoria (§13).** Deixou de ser trade-off de escopo: a trilha completa saiu junto com a entrega.
   Sobra confirmar se ele precisa disso em relatório.
4. ~~**A contratação deve perguntar o consentimento?**~~ **Implementado.** O candidato responde na
   ficha admissional, nas três modalidades, e o DP confirma na transição "Cadastrar Colaborador",
   que grava o valor e abre a trilha de auditoria. Ninguém mais nasce com "Sim" sem alguém decidir.

## 10. Riscos e pontos de atenção

**O padrão "Sim" é uma decisão jurídica, não técnica.** Presumir autorização concedida para quem
nunca assinou nada é o oposto do que a LGPD pede (consentimento é opt-in, não opt-out). Ele pediu
explicitamente o padrão "Sim", e é o que está especificado aqui, mas **vale ele confirmar isso com o
jurídico**, porque o campo passa a afirmar que alguém autorizou sem que exista termo assinado.
Alternativa: um terceiro estado "Não informado" como padrão, que força a checagem em vez de liberar.

**O campo e o documento podem divergir.** O termo assinado é um retrato do momento da admissão; o
campo é editável a qualquer tempo. Isso é correto e desejado, porque a revogação existe, mas
significa que **o campo passa a ser a fonte de verdade operacional** e o documento vira evidência
histórica. Quem publica tem que olhar o campo, não o documento.

**Coordenação com a `feat/resumo-anotacao-obrigatorio`.** Se o item 3 for pelo caminho de anotação,
as duas frentes mexem em `ARQ_TIPO_ANOTACAO`.

## 11. O que foi implementado (27/08/2026)

> ⚠️ O armazenamento descrito aqui foi substituído em 28/08/2026, ver §13. As telas, permissões e
> validações em tela continuam valendo.

Itens 1 e 2 do chamado, na forma do item 2: **dois campos independentes**, sem campo geral, porque o
geral seria redundante e permitiria estado inconsistente (§4).

### Arquivos alterados

| Arquivo | Mudança |
|---|---|
| `acs/DireitoProjeto.java` | `PessoaLotadaImagemVozVer`, `PessoaLotadaImagemVozEditar` |
| `prp/RefPropriedade.java` | `PessoaAutorizaImagemVozInterno`, `PessoaAutorizaImagemVozExterno` |
| `prp/PropriedadesRH.java` | dois getters com `.asBoolean(true)`, padrão do `isAlertarAniversario()` |
| `view/pessoa/ExibirPessoaDadosGerais.java` | duas colunas na aba Dados Gerais, sob o direito `...ImagemVozVer` |
| `wicket/rh/cadastro/EditarCadastroRhDadosCadastroPanel.java` | dois `CheckBox` e construtor novo |
| `wicket/rh/cadastro/EditarCadastroRhDadosCadastroPanel.html` | `wicket:enclosure` com as duas linhas |
| `wicket/rh/cadastro/EditarCadastroRhPage.java` | carga, permissão, gravação e o helper `salvarPropriedadeBooleana` |
| `sql/update_v318.sql` | **novo**: duas propriedades, duas funcionalidades, concessão de perfis |

### Decisões de implementação

**O construtor antigo do painel foi mantido.** Ele passou a delegar para o novo, com `null` nos dois
modelos. Assim `FormularioCadastrarColaborador` (fluxo de contratação, que cria RH ainda sem código)
continua funcionando sem alteração, e lá os dois checkbox ficam invisíveis, exatamente como já
acontece com o de aniversário.

**Grava só quando o valor muda.** O helper `salvarPropriedadeBooleana` compara com
`asBoolean(true)` antes de escrever. Isso preserva a propriedade do `.asBoolean(true)`: quem está no
padrão não ganha linha em `PRP_RH`, e o "Sim" para novos cadastros sai sem migração de dados.

**Quem não pode editar não grava.** Com o direito ausente o `CheckBox` sai desabilitado, o Wicket não
atualiza o modelo, o valor carregado permanece igual ao gravado e o helper não escreve nada.

**O bloco de gravação do aniversário não foi tocado.** O helper novo é usado só pelas duas
propriedades novas, para não alterar comportamento que já funciona.

### Verificação feita

| O que | Resultado |
|---|---|
| Compilação (`mvn compile`) | `BUILD SUCCESS` |
| Codificação dos fontes | ISO-8859-1 com CRLF preservado, zero mojibake |
| Sintaxe e colunas do `update_v318.sql` | 5 comandos validados por `EXPLAIN` contra o banco de homologação, dentro de transação com `ROLLBACK`. Nada foi gravado |
| Siglas novas já existiam? | não, as quatro estão livres no banco |
| `cod_dominio_perfil` das funcionalidades de pessoa | `2`, confirmado em 95 funcionalidades `PessoaLotada*` |
| Padrão da propriedade booleana de pessoa | `cod_algoritmo_resolucao=10`, `TipoParametroBoolean`, igual ao `PessoaEnviarEmailAniversario` |

### Validado em tela no DEV02 (27/08/2026)

Logado como `lucas.lopes` (perfil Master), alvo `Italo Ruann` (cod 5497, lotado em Presidência).

| # | Teste | Resultado |
|:-:|---|---|
| 1 | Campo aparece em Dados Gerais com "Sim" | ✅ e `PRP_RH` **vazia**, provando que o padrão vem do `valor_default` |
| 2 | Desmarcar só o interno | ✅ externo continuou "Sim"; **uma única linha** gravada, interno=false |
| 3 | Salvar outro cadastro sem mexer | ✅ nenhuma linha nova |
| 4 | Fluxo de Contratação, tela "Cadastrar Colaborador" | ✅ abre sem erro e os checkbox **não aparecem** |

O teste 2 prova as duas exigências centrais de uma vez: a independência entre interno e externo, que
é o consentimento específico da LGPD, e o "grava só quando muda", que evita encher a `PRP_RH` com o
valor padrão de todo colaborador que alguém salvar.

O teste 4 exigiu percorrer uma contratação real: instância `mbpm.Contratacao.701083`, preenchendo a
Ficha Admissional em **janela anônima**, porque a regra
`<intercept-url pattern="/wicket/fichaAdmissional/fichaAdmissional" access="not isAuthenticated()"/>`
barra usuário logado. A ficha é feita para o candidato, que não tem login.

### Rótulos encurtados depois dos testes

Na exibição o rótulo longo quebrava linha e apertava a grade de duas colunas. Ficou
`Uso de imagem e voz: interno` e `Uso de imagem e voz: externo` (commit `f9971d0f8`). Na tela de
**edição** o texto longo foi mantido de propósito: é ali que a pessoa decide o que está marcando, e o
espaço é uma linha inteira.

### O que NÃO foi implementado

**Item 3, anexar o termo assinado.** Ele condicionou a "se viável". O caminho é tipo de anotação
próprio, mas `ARQ_TIPO_ANOTACAO` está sendo alterada agora pela branch `feat/resumo-anotacao-obrigatorio`
(script v318). Fazer as duas coisas ao mesmo tempo na mesma tabela é pedir conflito. Melhor entrar
depois que aquela branch for mesclada.

**Rastreabilidade das revogações**, pelo motivo da §6: não existe histórico de propriedade de pessoa.
A saída recomendada é a B, anotação automática a cada mudança, e ela depende da mesma tabela do item 3.

### Configuração pendente antes de usar

O script concede os dois direitos **apenas a quem já pode editar cadastro** (hoje Master, Gerente
Administrativo Financeiro e Assistente DP). Isso é deliberado: ninguém passa a ver dado pessoal que
já não via.

Falta decidir e conceder `PessoaLotadaImagemVozVer` aos perfis de Compliance, Governança e de quem
faz divulgação. Os candidatos encontrados no banco estão no fim do `update_v318.sql`, comentados:
`121` Singular - Compliance Delivery, `124` Analista de Governança, `126` Gerente de Governança.

## 12. O fluxo em três lugares, espelhando o chamado 712013

O campo sozinho no cadastro não basta, e o motivo está no próprio texto do chamado: a autorização
"pode ser concedida, recusada ou revogada a qualquer tempo". **Concedida** acontece na admissão,
**revogada** acontece depois. Só o segundo momento estava contemplado.

O precedente certo é o chamado **712013**, do mesmo solicitante, que implementou os contatos de
emergência em três partes. Foi verificado no código antes de decidir:
`FormularioCadastrarColaborador.addSecaoContatosEmergencia` recebe
`ficha.getValor("dados_pessoais")` e já chega preenchido com o que o candidato respondeu.

### O que cada parte faz

| Parte | Onde | Quem responde | O que resolve |
|---|---|---|---|
| **1** | Cadastro do colaborador, aba Dados Gerais e tela de edição | DP e Compliance, a qualquer tempo | consultar antes de publicar e **revogar** |
| **2** | Tela "Cadastrar Colaborador", aba Parâmetros | DP, no ato da admissão | registrar a decisão junto com o resto do cadastro |
| **3** | Ficha Admissional | **o próprio candidato** | tornar o valor uma **declaração da pessoa**, e não uma suposição do sistema |

### Por que a parte 3 é a que dá valor jurídico

Sem ela, o "Sim" do padrão é uma afirmação que o sistema faz sozinho. Com ela, o valor vem do mesmo
formulário em que a pessoa assina o Termo de Autorização de Uso de Imagem e Voz, que é onde o
consentimento de fato existe. É a diferença entre o sistema **presumir** e a pessoa **declarar**.

### Situação

| Parte | Situação |
|---|---|
| 1 | ✅ feita e validada em tela no DEV02, 4 de 4 testes (§11) |
| 2 | ✅ feita, compilando, **falta validar em tela** |
| 3 | ✅ feita **para os três tipos** (CLT, Estágio e PJ), compilando, **falta validar em tela** |

### Detalhes da parte 2

Dois campos na aba **Parâmetros**, que já existia, e a gravação entra no `completarTransicao`
**logo depois do `salvarContatosEmergencia`**, no mesmo ponto e pelo mesmo motivo: ali o
`salvarAtualizarRH(rh)` já rodou e o RH tem `cod`.

Correção de um engano anterior desta spec: eu havia afirmado que não daria para gravar a propriedade
nessa tela porque o RH não teria código. Isso vale só enquanto o formulário é **renderizado**; na
hora de salvar o RH já existe.

A escrita usa `FacadePropriedade.setPropriedadeRH`, que é a API própria do framework: monta o
`PropRh`, trata valor nulo como remoção e limpa o cache. É melhor que montar a entidade na mão, como
a tela de edição do cadastro ainda faz.

E grava **apenas o que diverge do padrão**, mesma regra da parte 1. Sem isso, cada admissão criaria
duas linhas em `PRP_RH` só para repetir o valor default.

### Por que a parte 3 está em espera

Depende de uma decisão de escopo que só o grupo responde: **vale para CLT, Estágio e PJ, ou só CLT?**

Não é detalhe. Os contatos de emergência precisaram de componentes separados por tipo
(`DadosPessoaisCltField`, `DadosPessoaisEstagioField`) mais um `TipoParametro` de exibição para cada.
O tamanho da parte 3 dobra ou triplica conforme a resposta.

Há também a questão de negócio: o Termo de Autorização de Uso de Imagem e Voz existe nos termos CLT.
Se PJ não assina esse termo, perguntar na ficha de PJ não faz sentido.

### A parte 3 saiu do bloqueio (27/08/2026)

A pergunta de escopo foi respondida pelo próprio código, não pelo grupo: os contatos de emergência do
712013 estão na classe **base** `DadosPessoaisDTO` e são renderizados nos **três** componentes de
ficha, `DadosPessoaisCltField`, `DadosPessoaisEstagioField` e `DadosPessoaisField` (este último é o da
Prestação de Serviços, usado no PJ). Como o precedente vale para todos, o consentimento também vale.

Implementado com **radio Sim/Não**, e não checkbox. Checkbox marcado por padrão reproduz o problema do
"Sim" automático: a pessoa passa direto sem decidir. O radio força a escolha, e é o estilo que a ficha
já usa em "Possui Filhos" e "Primeiro Emprego".

Tipo `Boolean` e não `boolean`: fichas antigas ficam nulas e o `TipoParametro` exibe `-`. Só quem
responder gera valor.

| Camada | Arquivo |
|---|---|
| DTO | `DadosPessoaisDTO` |
| Tela da ficha | os três `DadosPessoais*Field` |
| Exibição somente leitura | os três `TipoParametroDadosPessoais*` |
| Tela do DP | `FormularioCadastrarColaborador`, abre com o que o candidato declarou |

## 14. Os dois defeitos do três estados (corrigidos em 31/08/2026)

A análise das respostas do Luís encontrou dois defeitos no que já estava commitado, e os dois vinham
da mesma raiz: **o dado tem três estados, mas as duas telas tratavam como dois.**

### Defeito 1: o sistema fabricava consentimento

`FormularioCadastrarColaborador` inicializava os dois campos com `true` e chamava
`definirAutorizacaoImagemVoz` **incondicionalmente**:

```java
private boolean autorizaImagemVozInterno = true;
...
dadosPessoais.map(...::getAutorizaImagemVozInterno).ifPresent(v -> autorizaImagemVozInterno = v);
```

O `ifPresent` só sobrescreve quando a ficha **tem** a resposta. Sem resposta, gravava `Sim` explícito
no banco, ou seja, **o cadastro passava a afirmar uma autorização que ninguém deu**. Vale para menor
de idade também.

Não é hipótese: **seis modalidades de contratação nunca passam por ficha admissional** (PJ Carga
Horária, Sócio, HT, Diretor Estatutário, Sócio Mirante e RPA), porque
`ContratacaoProcess.getFichaAdmissional` devolve `null` para elas.

### Defeito 2: o DP não conseguia registrar um "Sim"

`EditarCadastroRhPage` detectava mudança comparando com `isImagemVozInternoAutorizado()`, que
devolvia `true` para `null`. Para um colaborador antigo (coluna nula), o DP marcar "Sim" e salvar dava
`true != true`: nenhuma mudança detectada, **nada gravado e nada logado**. O cadastro ficava "Não
informado" para sempre.

Juntos, os dois formavam a pior combinação: gravava "Sim" para quem não respondeu, e não gravava
"Sim" para quem de fato assinou.

### A correção

O campo passou a ser de três estados de ponta a ponta:

| Arquivo | Mudança |
|---|---|
| `entity/RecursoHumano.java` | `definirAutorizacaoImagemVoz(Boolean, Boolean)`; **removidos** os dois getters que achatavam `null` em `Sim` |
| `processo/.../FormularioCadastrarColaborador.java` | campos viram `Boolean` sem valor inicial; só grava e só anota quando **alguém respondeu**; a seção virou radio Sim/Não |
| `wicket/.../EditarCadastroRhPage.java` | carrega o valor **cru** e compara com `Objects.equals` contra o valor cru |
| `wicket/.../EditarCadastroRhDadosCadastroPanel.java` + `.html` | os dois checkbox viraram `RadioChoice<Boolean>`, e nada marcado é o "Não informado" |
| `bean/rh/CadastroRhBean.java` | o log distingue "Não informado" de "Não" |

O checkbox era o erro de origem: desmarcado é indistinguível de recusa. O radio com nada selecionado
é o único controle que representa os três estados sem mentir. É o mesmo idioma que a ficha
admissional já usava, com `BooleanChoiceRenderer("Sim", "Não")`.

`mvn compile`: `BUILD SUCCESS`.

---

## 13. Revisão do dev: saiu do framework de propriedades (28/08/2026)

Esta seção **substitui** o armazenamento descrito nas §3, §4, §7, §8 e §11. O resto da spec continua
valendo.

### O que o dev do projeto apontou

> Propriedades devem ser usadas apenas para contextos específicos, quando é necessário ter algum tipo
> de herança de informações. Quando por exemplo uma pessoa herda determinada propriedade por conta do
> seu tipo de contratação, ou por conta do projeto lotado. Por isso que lá tem o algoritmo de
> resolução da propriedade. Esses dois atributos criados nada mais são do que atributos do
> colaborador, assim como um CPF, e-mail. Desta forma era só ter criado campos na tabela do RH.

E deixou três coisas para levantar: se precisa de histórico, se basta o campo com log de quando e por
quem foi alterado, e se alguém precisa ser notificado.

### Por que ele está certo

O critério é **resolução**, não a natureza do dado. O enum `EstrategiaPropriedade` existe para isso:
`RHTipoContratacao` cai no tipo de contratação quando a pessoa não tem valor próprio,
`RHProjetoArvore` e `RHLeituraProjetoArvore` sobem a árvore de projetos. As duas propriedades que eu
tinha criado usavam a estratégia `RH` pura, que não resolve nada: lê `PRP_RH` ou devolve o
`valor_default` da definição. Ou seja, era uma tabela de chave e valor vestida de framework.

O argumento que eu tinha usado antes, de que propriedade guarda configuração e não fato da pessoa,
**não se sustenta**: `PessoaEnviarEmailAniversario` é escolha da pessoa, mora em `PRP_RH` e é editada
no mesmo painel. Ela é um precedente ruim, não uma justificativa.

### O que o desenho antigo carregava, confirmado no código

| Problema | Onde |
|---|---|
| A permissão específica era contornável | `RelatorioRhPropriedades` expõe toda propriedade sob `PessoaLotadaPropriedadeVer`, e `AdministrarPropriedadesPage` + `EditarValorPropriedadePage` editam qualquer propriedade de RH sob `SistemaPropriedadesAdministrar` |
| Sem data e sem autor | `PropRh` tem três colunas: `codRh`, `codDefinicaoPropriedade`, `valor` |
| Sem prova de quem autorizou | só gravava o "Não"; quem autorizou não deixava linha, e "autorizou" ficava indistinguível de "nunca foi perguntado" |
| Padrão global frágil | `valor_default='true'` fica na definição; mudar aquele registro invertia o consentimento de todo mundo sem linha |

### Novo armazenamento

**Estado atual**, em duas colunas de `CAD_RECURSO_HUMANO`, no molde do `se_possui_filhos`:

| Coluna | Tipo | Papel |
|---|---|---|
| `se_autoriza_imagem_voz_interno` | `boolean` nullable | uso interno |
| `se_autoriza_imagem_voz_externo` | `boolean` nullable | uso externo |

`null` significa "ninguém respondeu ainda". É a informação que o desenho anterior perdia.

**Trilha**, na estrutura de anotações que o projeto já usa como log, indicada pelo dev na segunda
rodada de revisão: `RHAnotacao` com tipo `ArqTipoAnotacao.AUDITORIA_ALOCPRO` (código 85), no padrão
de [ProjetoBean.java:280](../src/main/java/com/miranteinfo/alocpro/bean/prj/ProjetoBean.java#L280).
Ela já carrega data, autor, RH e texto, então **cada alteração vira uma linha nova**, e o histórico
que ele tinha levantado como dúvida sai de graça.

> "Existe uma estrutura de anotações que a gente aproveita pra usar como log, pode usar essa estrutura
> genérica que registra como Auditoria Alocpro. Esse exemplo tá mais complexo, você só vai precisar
> registrar qual valor tá sendo incluído."
>
> "Tem muita coisa no sistema que não tem log, mas aí essas novas a gente tem que passar a ir
> colocando, principalmente com LGPD."

Por isso não existem colunas `data_` e `cod_pessoa_` na tabela: seriam uma segunda fonte da verdade
sobre a mesma coisa, e só guardariam a última alteração.

### Regras

Na entidade, só o valor, e ele tem **três estados**: `true`, `false` e `null`.

```java
public void definirAutorizacaoImagemVoz(Boolean interno, Boolean externo) {
    autorizaImagemVozInterno = interno;
    autorizaImagemVozExterno = externo;
}
```

> ⚠️ **Existiam `isImagemVozInternoAutorizado()` e `isImagemVozExternoAutorizado()`**, que devolviam
> `!Boolean.FALSE.equals(campo)`, ou seja, tratavam `null` como autorizado. **Foram removidos em
> 31/08/2026 porque causaram os dois defeitos da seção "Os dois defeitos do três estados".** Não
> recriar: `null` não é `Sim`, é ausência de resposta, e qualquer código que achatar isso volta a
> afirmar consentimento que ninguém deu.

No `CadastroRhBean`, o log:

```java
// consentimento de LGPD precisa de trilha, então cada mudança vira uma anotação de auditoria
public void registrarAutorizacaoImagemVoz(RecursoHumano rh) {
    RHAnotacao anotacao = new RHAnotacao();
    anotacao.setTipo(GenericService.retrieve(ArqTipoAnotacao.class, ArqTipoAnotacao.AUDITORIA_ALOCPRO));
    anotacao.setResumo("Autorização de uso de imagem e voz");
    anotacao.setDetalhado(...);
    ...
    getBean(AnotacoesBean.class).criarAnotacao(anotacao, null, null, getUser().getPessoa());
}
```

**Quem decide se anota é a tela**, e cada uma decide diferente:

- `FormularioCadastrarColaborador` **sempre** anota, porque ali é o momento da declaração do
  colaborador. A trilha começa com valor explícito, data e autor.
- `EditarCadastroRhPage` só anota quando o valor realmente mudou, para uma edição de telefone não
  virar registro de consentimento com a data de hoje.

Os dois campos não têm setter. Hibernate usa acesso por campo, então o único caminho de escrita é o
`definirAutorizacaoImagemVoz`, chamado antes do `salvarAtualizarRH`; a anotação vem depois do save,
para não registrar uma mudança que não persistiu.

### Arquivos alterados nesta revisão

| Arquivo | Mudança |
|---|---|
| `sql/update_v318.sql` | saem as duas `PRP_DEFINICAO_PROPRIEDADE`, entram dois `ALTER TABLE`. Funcionalidades e concessões de perfil continuam iguais |
| `sql/_ROLLBACK_dev_v318.sql` | drop das colunas, delete das anotações de auditoria, e mantém o delete das propriedades para limpar o DEV02, onde os inserts antigos já rodaram |
| `prp/RefPropriedade.java` | removidas as duas constantes |
| `prp/PropriedadesRH.java` | removidos os dois getters |
| `entity/RecursoHumano.java` | dois campos, dois getters, dois efetivos e o `definirAutorizacaoImagemVoz` |
| `bean/rh/CadastroRhBean.java` | **novo** `registrarAutorizacaoImagemVoz(rh)`, que cria a `RHAnotacao` de auditoria |
| `view/pessoa/ExibirPessoaDadosGerais.java` | lê da entidade e exibe três estados: Sim, Não, Não informado |
| `wicket/rh/cadastro/EditarCadastroRhPage.java` | lê e grava na entidade, anota quando muda; o bloco do aniversário voltou **exatamente** ao original |
| `processo/mbpm/rh/contratacao/FormularioCadastrarColaborador.java` | define na entidade antes do `salvarAtualizarRH` e anota depois, em vez de `setPropriedadeRH` |

Não foram tocados: os três `DadosPessoais*Field`, `DadosPessoaisDTO`, os três
`TipoParametroDadosPessoais*`, `DireitoProjeto`, `EditarCadastroRhDadosCadastroPanel` e o `.html`. O
fluxo em três partes da §12 continua idêntico; só mudou onde o valor pousa no fim.

### Mudança de comportamento visível

Colaborador antigo, que nunca respondeu, passa a aparecer como **"Não informado"** em Dados Gerais, e
não mais como "Sim". O padrão autorizado continua valendo onde o sistema precisa de um booleano, mas
a tela deixa de afirmar um consentimento que ninguém deu. Se o Luís preferir voltar a exibir "Sim",
é uma linha no `descreverAutorizacao`.

### Verificação

| O que | Resultado |
|---|---|
| `mvn compile` | `BUILD SUCCESS`, as quatro classes recompiladas |
| Codificação | ISO-8859-1 com CRLF preservado nos `.java` e no `.sql`, zero mojibake |
| Referência órfã a propriedade | nenhuma; `grep` por `PessoaAutorizaImagemVoz` só acha o rollback |
| Anotação de auditoria | mesmo formato do `ProjetoBean.registrarAlteracaoAcesso`: tipo 85, `data`, `autor`, `rh`, `visivelParaPessoa = false` |

Falta validar em tela, porque o `config.properties` ainda aponta para homologação.

### Antes de rodar em qualquer banco

O DEV02 já tem os inserts do desenho antigo. Rodar o `_ROLLBACK_dev_v318.sql` primeiro, depois o
`update_v318.sql` novo. Homologação e produção nunca receberam os inserts antigos, então lá é só o
script novo.

### Pendências que o dev levantou e o Luís precisa responder

1. ~~**Precisa de histórico?**~~ **Resolvido pela anotação de auditoria.** Cada alteração vira uma
   `RHAnotacao` nova, com data e autor, então a linha do tempo completa existe sem tabela adicional.
   Falta só confirmar com o Luís se ele precisa disso exposto em relatório, ou se consultar a aba de
   anotações do colaborador já atende.
2. **Alguém precisa ser notificado?** Uma revogação de uso externo interessa ao Marketing, que pode
   ter material publicado. Nada foi implementado.

   O gancho já existe e é barato: `AnotacoesBean.criarAnotacao` chama `avisarEnvolvidosAnotacaoRH`
   quando o tipo da anotação é `visivelNoHistoricoAtvRH`. Hoje a anotação entra com
   `visivelParaPessoa = false`, seguindo o `ProjetoBean`. Se o Luís disser quem precisa ser avisado, a
   decisão é entre configurar isso pelo tipo ou disparar um e-mail direto no
   `registrarAutorizacaoImagemVoz`.

---

## 15. As respostas do Encarregado, e o que foi implementado (01/09/2026)

O Luís respondeu as 10 perguntas. Ele é o **Encarregado pelo Tratamento de Dados Pessoais (DPO)** da
empresa, o que aparece no próprio Termo de Autorização gerado pelo chamado 712036.

Três decisões nossas foram confirmadas por ele: o padrão **"Não informado"**, a **edição restrita** a
DP e Compliance, e a **lista de perfis** proposta.

### O que ele definiu, e como ficou

| Resposta | Definição dele | Implementação |
|:-:|---|---|
| 1 | Anexo do termo: preferência por anexar no cadastro, quem anexa é o DP, **pode ser chamado posterior** | fora de escopo por decisão dele |
| 2 e 9 | "Não informado" confirmado. Pediu **edição em massa** para o DP | valor confirmado; edição em massa **em aberto** |
| 3 | Guardar por **6 anos** do fim do vínculo ou da revogação, o que ocorrer por último | guarda protegida pelo log imutável; eliminação automática é item separado, combinado com ele |
| 4 | Titular consulta a própria autorização e o histórico | histórico na aba Dados Gerais, `visivelParaPessoa = true` na anotação |
| 5 | Edição restrita confirmada. Revogação entra por e-mail ao DP ou ao Encarregado, registrada em até 2 dias úteis, com a **data da revogação** gravada | campo de data na tela de edição, gravado na anotação |
| 6 | Avisar **ele, DP e Marketing** na revogação do uso externo | direito `PessoaLotadaImagemVozMonitorar` + e-mail |
| 7 | **Não continua valendo** após o desligamento, com status próprio "Encerrada por desligamento" automático | derivado de `isAtivo()` e do fim do acordo |
| 8 | Vale para todos os vínculos, inclusive terceirizados. Menor de 18 exige responsável legal | parcial, ver abaixo |
| 10 | Perfis confirmados. Log com quem, quando, **valor anterior e valor novo**, sem edição | tipo de anotação próprio, log no formato "de X para Y", `AnotacaoUtil` corrigido |

### O titular passa a editar a própria autorização (17/09/2026)

Em produção chegou reclamação: em Meus dados, Editar Cadastro, o colaborador via a autorização mas não conseguia mudar, porque só `PessoaLotadaImagemVozEditar` editava (resposta 5). O Aderbal decidiu que o próprio titular define se autoriza, o que também atende a revogação "por procedimento gratuito e facilitado" (LGPD, art. 8º, §5º). O DP continua registrando pedidos que chegam por e-mail, e só ele informa a **data da revogação**; quando o titular muda, a data é a do dia. A mudança gera a anotação de auditoria e o aviso como qualquer outra. `EditarCadastroRhPage`. Vale confirmar com o Luís.

### Sem alerta de primeira resposta (17/09/2026)

Chegou a ser feito um alerta diário (`AlertaAutorizacaoImagemVoz`) que levava todo colaborador sem resposta para Editar Cadastro no login. O Aderbal testou e decidiu tirar por excesso de complexidade: fica só a possibilidade de o próprio colaborador alterar em Meus dados. O commit local foi descartado antes de qualquer push. Os 286 alertas criados no teste no DEV02 são removidos por `specs/limpeza-alertas-imagem-voz-dev02.sql`, **antes** de subir build sem a classe (alerta no banco sem a classe quebra as listas de alertas).

### A data da revogação não virou coluna

Cheguei a criar `data_revogacao_imagem_voz_interno` e `_externo`. **O dev do projeto derrubou, e com
razão:** `ArqAnotacao` já tem dois campos de data independentes.

- `data` recebe **quando o titular se manifestou**, informado pelo DP.
- `dataInclusao`, gravado pelo `criarAnotacao`, é **quando o registro foi feito**.

Era exatamente essa distinção que motivou as colunas, e a revogação chega por e-mail e é lançada dias
depois, então as duas datas realmente diferem. Sem coluna nova, e resolve melhor o caso de interno e
externo revogados em datas diferentes: viram duas anotações, cada uma com a sua data.

### Tipo de anotação próprio, e não o `AUDITORIA_ALOCPRO`

O tipo 85 é compartilhado com `ProjetoBean.registrarAlteracaoAcesso`, que grava inclusão e remoção de
perfis e anexa o XML de backup dos acessos. Como o Compliance precisa enxergar a trilha do
consentimento, abrir visibilidade no 85 entregaria aquilo junto. O dev concordou com o tipo próprio.

**Código 129.** O 124, escolhido primeiro, já estava ocupado: as constantes Java param em 123, mas o
banco vai até 128, com cinco tipos que existem sem constante correspondente. **A fonte de verdade dos
códigos é o banco, não o enum Java.**

### O aperto no `AnotacaoUtil` é restrito ao tipo 129

> Corrigido em 03/09. Esta seção afirmava que a alteração invertia a ordem das linhas e valia
> para todo tipo de uso interno. Está errado, e o diff prova: são **4 linhas acrescentadas**,
> nenhuma alterada.

As linhas `&= !isUsoInternoAplicacao()` e `|= user.isMaster()` **já existiam nessa ordem**, então
o master já apagava log de uso interno nesse caminho antes desta demanda. O que foi acrescentado
é só o recorte final:

```java
podeExcluir &= !ArqTipoAnotacao.CONSENTIMENTO_IMAGEM_VOZ.equals(anotacao.getTipo().getCod());
```

Os outros 17 tipos de uso interno seguem exatamente como estavam. Nada a confirmar com o dev.

**E há uma segunda barreira, independente desta.** A aba ANOTAÇÕES do RH (`RelatorioRhAnotacoes`)
**não usa** o `AnotacaoUtil`:

| Caminho | Quem usa | Regra |
|---|---|---|
| `ExcluirAnotacaoRhPage` / `EditarAnotacaoRhPage` | aba ANOTAÇÕES do RH | barra **todo** uso interno, sem exceção para master |
| `AnotacaoUtil.podeExcluir` | anotação de candidato e de empresa | master apaga uso interno, menos o 129 |

O consentimento fica protegido nos dois, contra exclusão **e** contra edição.

### O que continua aberto, e por quê

- **Edição em massa (2 e 9).** Depende do aval do Anderson, que ainda não veio, e de uma decisão de
  desenho: tela permanente no menu, script único, ou tela que exige lista nominal. Recomendação:
  a terceira. Uma tela permanente de gravar consentimento em massa é o oposto do que um DPO quer
  deixar disponível, porque o próximo uso não terá aval nenhum por trás.
- **Menor de idade (8).** Ele mesmo condicionou: precisa o DP confirmar se há estagiário ou aprendiz
  menor na base hoje.
- **Terceirizado (8).** ⚠️ **Não existe no sistema.** Não há entidade, não há tipo de contratação, e o
  `grep` só encontra a palavra em dois endereços de e-mail. CLT, estágio, aprendiz e PJ já estão
  cobertos porque são `RecursoHumano`. Terceirizado não tem onde morar, e nenhuma coluna resolve isso.
  Precisa voltar como pergunta para ele.

---

## 16. Validação em tela (03/09/2026)

Roteiro completo em [validacao-consentimento-imagem-voz.md](validacao-consentimento-imagem-voz.md).

**Ambiente:** DEV02 (`10.0.0.16/Mirante_DEV02`), commit `27c3631a6` na `desenv`, local, sem push.
**Sessão:** `lucas.lopes` (Master, confirmado no banco: perfil 6, projeto Mirante, sem data limite).

### Resultado

Os 8 testes passaram e as 10 respostas do Encarregado estão cobertas.

| # | O que provava | Resultado |
|:-:|---|---|
| 1 | "Não informado" para quem nunca respondeu | passou |
| 2 | "Encerrada por desligamento" em vermelho, uma vez | passou |
| 3 | Seção própria, radios sem nada marcado | passou |
| 4 | Gravar "Sim" sobre `null` | **falhou na 1ª**, passou na 2ª |
| 5 | Data de manifestação ≠ data de registro | passou, com 2 ajustes |
| 6 | Editar telefone não vira consentimento | passou |
| 7 | Log não pode ser apagado nem editado | passou |
| 8 | Titular vê, mas não edita | passou (substituído em 17/09/2026: o titular edita a própria autorização) |

### Três defeitos que só o teste achou

Nenhum deles era visível olhando a tela. Os dois primeiros exigiram conferir o banco; o terceiro,
o console.

**1. `salvarAtualizarRH` não persistia as colunas novas.** Para RH existente o método não faz
merge: busca uma instância nova do banco e copia campo a campo, numa lista explícita de 25 setters.
Os dois campos novos não estavam na lista. A anotação, montada a partir do objeto em memória,
gravava `de "Não informado" para "Sim"` enquanto as colunas ficavam nulas — o log afirmando um
consentimento que o banco não guardou. **Alcance:** também atingia o `FormularioCadastrarColaborador`,
então uma contratação de alguém que já existe como pessoa perderia a resposta.

> **Regra para campo novo em `RecursoHumano`:** coluna e setter na entidade não bastam. Se o campo
> não entrar na lista de cópia do `salvarAtualizarRH`, ele não persiste em edição, e falha calada.

**2. Apresentação do histórico.** O rótulo "Histórico da autorização" se repetia uma vez por
anotação, e o log trazia linha de campo que não mudou (`Uso interno: de "Sim" para "Sim"`).
Decisão: só entra no log o que mudou, porque num log de LGPD linha inerte é ruído que o
Encarregado teria de filtrar na mão.

**3. A notificação de revogação nunca saía.** `LazyInitializationException` em
`RecursoHumano.acordosTrabalho`: o `rh` chega destacado da página Wicket, o `appendFoto` chama
`isAtivo()`, que percorre a coleção lazy, e sem sessão viva estoura. A anotação era criada
normalmente na mesma chamada porque não toca coleção lazy nenhuma. Corrigido com
`RecursoHumano alvo = retrieveRh(rh.getCod())`, como a linha 127 do próprio arquivo já fazia.

> **O desenho se provou no meio do estrago.** O `try/catch` isolado impediu que a falha de e-mail
> derrubasse o salvamento, e o `SEVERE` foi o que permitiu achar. Sem ele a notificação sumiria calada.

### A notificação, depois da correção

Entrou na `email_fila_envio` (`cod=852548`) com o assunto
`[LGPD] Uso externo de imagem e voz revogado: Aderbal Neto`, para **17 destinatários** de
Governança, DP, Marketing e Compliance, **sem** o titular — o `removePessoa(rh)` funcionou.
Um e-mail só: o salvamento `Não→Sim` não notificou, o `Sim→Não` sim.

⚠️ **Enfileirado não é enviado, e nada foi entregue a ninguém.** O SMTP do DEV02 aponta para
`correio.mirante.net.br_DESABILITADO`, que não resolve em DNS. A prova está na própria fila:
**872 e-mails presos desde 18/02**, um único dia (10/08) com 411. Se o envio funcionasse nesse
ambiente, a fila teria drenado.

> **Aviso para quem cuida do ambiente:** o DEV02 é cópia da produção e a fila guarda endereços
> reais. Apontar esse ambiente para um SMTP que funcione pode disparar sete meses de e-mails
> para gente de verdade.

### Limite do histórico na aba Dados Gerais

Depois dos testes, o histórico passou a mostrar **as 3 entradas mais recentes**, com uma linha
"e mais N anteriores" quando houver corte. Antes listava tudo.

Não é só estética: **o titular não tem a aba ANOTAÇÕES**, então esta é a única via pela qual ele
consulta o próprio consentimento — a resposta 4. Cortar para uma entrada só o deixaria sem
histórico. Três cobre o uso real (uma ou duas mudanças por pessoa numa carreira) e ainda impede a
tela de crescer sem limite. A trilha completa continua inteira no banco e na aba ANOTAÇÕES, para
DP e Compliance.

Conferido em tela em 03/09 com 4 registros: apareceram 3 e a linha `e mais 1 registro anterior`,
com a 4ª visível na aba ANOTAÇÕES.

### Revisão adversarial, e o defeito da contratação

Depois dos 8 testes, uma revisão em 5 dimensões com 3 refutadores por achado: **39 brutos, 16
sobreviveram**. O grave: os 8 testes rodaram **todos pela tela de edição**, e a **contratação**
passava `(null, null)` como valores anteriores. Combinado com a correção do `salvarAtualizarRH`
— que passou a copiar os dois campos incondicionalmente — um readmitido que tinha recusado o uso
externo **perdia a recusa, sem uma linha de log**.

Corrigido: a contratação lê os valores reais do RH e preserva o campo que a ficha não respondeu.
Também foi fechado um vazamento na tela de edição, onde `podeEditarImagemVoz || valor != null`
mostrava o consentimento a quem tinha `PessoaLotadaCadastroEditar` sem `PessoaLotadaImagemVozVer`;
agora exige `podeVerImagemVoz`.

Detalhamento em [validacao-consentimento-imagem-voz.md](validacao-consentimento-imagem-voz.md).

> **Lição:** a correção do teste 4 consertou o "não persiste" e abriu o "sobrescreve com null".
> Correção de persistência precisa ser olhada nos dois sentidos.

⚠️ **Reportar ao Lucas, fora deste chamado:** `AnotacaoUtil.DetalharAnotacaoBehavior` lê
`codAnotacao` do parâmetro da requisição e renderiza a anotação sem checar direito de
visualização. É pré-existente (último a tocar: `159da6cc6`), não entra neste commit.

### Estado do banco ao fim

Dados de teste removidos. Baseline restaurado: **3922** RHs sem resposta, **0** anotações do
tipo 129, **0** órfãs em `rh_anotacao`. O tipo 129 e os 3 direitos seguem instalados.

## Destinatários do aviso: lista no config em vez de direito de acesso (18/09/2026)

A lista definitiva veio do Luís em 18/09/2026 e misturou pessoas com caixas coletivas:

| Área | Destinatário |
|---|---|
| Compliance | luis.farias@ e carlos.guimaraes@ (Carlos Eduardo Nascimento Guimaraes, confirmado pelo Aderbal) |
| Marketing | julia.dalava@ |
| DP | **dp@mirante.net.br** |
| Governança | **privacidade@mirante.net.br** |
| Gestão de Talentos | **comunicacao@mirante.net.br** ("Mirante Comunica") |

As três caixas coletivas não existem como pessoa no AlocPro, então perfil nenhum as alcança. Por isso
o desenho anterior (perfil próprio "Aviso LGPD - Imagem e Voz" com `prj_papel`) foi abandonado antes
de chegar em produção.

**Como ficou:** os destinatários saem da lista `usuarios-lgpd-imagem-voz` do `config.properties`,
lida por `PropriedadesSistema.getIntegrantesListaEmail`, o mesmo mecanismo do `usuarios-colegas`.
`EmailAlocpro2.GrupoEndereco` ganhou o método público `addLista`, que reusa o `addAlias` existente.
Lista vazia ou ausente não estoura o envio, só registra no log.

**Consequência:** o direito `PessoaLotadaImagemVozMonitorar` existia apenas para escolher quem recebia
esse aviso, e um `git grep` confirmou que não era usado em mais nenhum lugar. Ele saiu do enum
`DireitoProjeto` e o `update_v322.sql` virou um script de limpeza, removendo o direito das seis tabelas
que referenciam `acs_funcionalidade` e depois a própria funcionalidade. Em 18/09/2026 o DEV02 já não
tinha o direito em nenhum perfil, e em produção o Lucas já havia rodado o DELETE das associações.

**Vantagem e custo:** mudar a lista deixa de exigir script e cadastro de pessoa, mas passa a exigir o
Lucas editando o `config.properties` e reiniciando. Foi a troca aceita pelo Aderbal, porque a lista já
mudou uma vez em duas semanas e metade dela nunca vai ser pessoa no sistema.

## O termo gerado segue as respostas da ficha (30/09/2026)

Achado no teste de ponta a ponta do 564250 (contratação 701299 no DEV02): a candidata respondeu uso externo
**Não** e o termo gerado autorizava redes sociais, LinkedIn, Instagram e YouTube. O gerador usava o texto
completo para todo mundo (`Collections::emptySet` nas condições). Uma autorização assinada contradizendo
a resposta da própria ficha é risco de LGPD.

**O que mudou (local, sem commit):**

- `GeradorTermoAdmissional`: condições `imagemInterno` e `imagemExterno`, lidas de
  `autorizaImagemVozInterno` e `autorizaImagemVozExterno` da ficha (`condicoesImagemVoz`). Com os dois
  **Não** o termo de imagem e voz **não é gerado** e o histórico registra "Termo de Autorização de Imagem
  e Voz não gerado"; o Termo Admissional segue normal.
- `Termo_Autorizacao_Imagem_Voz.docx`: só dois parágrafos ganharam três versões (`{{SE ...}}`), o resto é
  igual para todos (compartilhamento limitado ao item 1, vedação de IA, vigência, revogação):

| Resposta | Finalidades | Meios e canais |
|---|---|---|
| Interno e externo | texto original, (a) a (e) | texto original |
| Só interno | comunicação interna e treinamentos corporativos | "Intranet e materiais impressos e audiovisuais de uso interno." |
| Só externo | (a) site e comunicação corporativa, (b) redes sociais, (c) recrutamento e employer branding, (d) apresentações a clientes e parceiros | original sem "intranet", com a transferência internacional (art. 33) |

- `GeradorTermoMergeTest`: as três combinações e a regra das condições; 12 de 12 verdes.

**Levar ao Luís (encarregado):** a classificação é minha. Pontos a confirmar: (a) "materiais de
comunicação corporativa" e (d)/(e) "apresentações a clientes" como **externo**; o texto de meios da versão
só interna é novo; se a intranet roda em nuvem fora do país, a versão só interna também precisaria da
frase do art. 33; e se, com dois Não, ele prefere um termo de recusa assinado em vez de nenhum.

## Histórico em seção própria (09/10/2026)

A pedido do Aderbal, os Dados Gerais mostram só a autorização vigente (`Interno: Sim | Externo: Sim`, ou
"Encerrada por desligamento"). O histórico saiu para a seção **"Privacidade e Consentimentos"**
(`view/pessoa/ExibirPessoaPrivacidade`), com o subtítulo "Autorização de imagem e voz", logo depois de **Dados Restritos** nas duas telas da pessoa
(`ExibirRHPage` e `ExibirCadastroPessoaPage`): fica junto do outro bloco de dado sensível, sem empurrar
Contatos e Endereços, que são de uso diário. Cada linha mostra a data no rótulo e "por <autor>" com o que mudou.

Regras mantidas, só mudaram de lugar:

- **Quem vê:** o próprio titular ou quem tem `PessoaLotadaImagemVozVer` na lotação, a mesma regra da linha de autorização.
- **Quando aparece:** só se houver pelo menos um registro; sem histórico, a seção some.
- **Limite:** as 3 mudanças mais recentes, com "e mais N registros anteriores"; a trilha inteira fica na aba
  Anotações, que o titular não alcança.
- **O que vira registro:** só mudança de fato no valor (edição do cadastro e cadastro na contratação comparam o
  valor anterior com o novo). Salvar o cadastro por outro motivo não grava nada.

**Nome da seção:** "Privacidade e Consentimentos" em vez de algo como "Governança e Compliance", que é mais amplo
(conduta, denúncias, conflito de interesse) do que o conteúdo. A seção nasce para juntar os registros de LGPD do
colaborador: hoje o histórico de imagem e voz; depois, aceite de termos e políticas, cada um com o seu subtítulo.

**Mesmo dia, segunda rodada:** a seção passou a juntar todas as escolhas de privacidade do colaborador, e os Dados
Gerais ficaram só com identificação. Dentro de "Privacidade e Consentimentos":

1. **Autorização de uso de imagem e voz** (vigente, saiu dos Dados Gerais) e o histórico logo abaixo, no formato de
   antes ("Histórico da autorização", data, autor e o que mudou, até 3).
2. **Divulgar aniversário por e-mail** (era "Notificar aniversário por e-mail", ao lado do Aniversário). O rótulo
   mudou porque o que a torna escolha de privacidade é a divulgação: no dia, o e-mail de parabéns vai para a pessoa
   com cópia oculta para o e-mail geral da equipe do projeto (`VerificarAniversario`), e a opção decide se ela entra
   no relatório de aniversariantes (`RelatorioProjetoEquipeAniversario`). "Notificar" sugeria um lembrete só para a
   própria pessoa. Na tela de edição, a
   caixa ganhou o mesmo texto e uma dica explicando isso.

Cada linha mantém a regra de quem vê que tinha nos Dados Gerais: imagem e voz para o titular ou `PessoaLotadaImagemVozVer`;
aniversário para o titular ou `PessoaLotadaCadastroEditar`, e só se houver data de nascimento. A seção aparece se o
usuário alcança alguma das duas.
