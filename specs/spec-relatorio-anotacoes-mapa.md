# Spec: Relatório "Anotações - Mapa" (matriz projeto x tipo de anotação)

> **Spec**: artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 577545** · Tipo: Solicitação · Solicitante: Daniel Bordin · Abertura: 02/11/2024 · Complemento "V2" em 03/11/2024
> **Status:** ⚙️ **Implementado na desenv LOCAL, sem commit** (29/10/2026): classe nova `RelatorioProjetoAnotacoesMapa` mais duas linhas em `ListarRelatoriosProjetoPage`. Compila, e os testes do catálogo de acesso seguem verdes (25). **Falta testar em tela**, no DEV02 com o Sg-MDA (175913) e período de 08/2025 a 02/2026. A figura comentada do chamado (§1, item 4) respondeu D2, D3 e D4; D1, D5 e D6 foram feitas pela proposta da §6.
> **Nível de dificuldade:** 🟢 Baixa. Uma classe de relatório nova que reaproveita a carga do relatório "Anotações/Anexos" (`AnotacoesBean.getAnotacoesProjeto`), o link de anexo que já existe (`ProvedorConteudoAnexoAnotacao`) e a página Detalhar Anotação. Mais uma linha na árvore de relatórios. Sem script SQL, sem direito novo, sem mexer em relatório existente.
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

## 1. Pedido

**Descrição (resumo dos prints do chamado):**
- Implementar o relatório novo "Anotação - Mapa", conforme a figura anexa, logo abaixo do relatório "Anotação" já existente na árvore do projeto. O objetivo é montar uma matriz das anotações para facilitar auditorias.
- Parâmetros:
  - **Período**: componente padrão, 6 meses por padrão.
  - **Tipos de Anotação**: todos por padrão. O chamado diz que este pode ficar para uma versão 2.
  - **Exibir apenas OS**: booleano, desmarcado por padrão.
  - **Exibir Encerrados**: booleano, marcado por padrão.
- **Complemento V2 (03/11/2024)**: parâmetro **"Exibir Anexos"**, booleano, desmarcado por padrão. Quando marcado, cada anotação mostra o nome de cada arquivo anexo, separados por ";", com link para abrir o arquivo.

**Anexo "Novo Relatorio Anotações - Mapa.xlsx"** (em Downloads, 108 KB):
1. **Figura** (imagem colada no topo da planilha): é um print do relatório atual de anotações, em lista, com as colunas Data, Projeto, Tipo, Autor, clipe e Resumo. São os dados de origem.
2. **Matriz** (linhas 24 a 31): o resultado esperado. Cada linha é um projeto e cada coluna é um tipo de anotação. Na célula vai uma linha por anotação, no formato `dd/mm/aa - resumo (A)`, com quebra de linha entre elas e a mais recente primeiro. Colunas da planilha: Projeto, Doc. do Projeto, E-mail Cliente, OS - Documento de Abertura, OS - Termo de Recebimento Definitivo, Nota Fiscal, Nota Fiscal (xml).
3. **Tabela de apoio** (linhas 48 a 62): a mesma lista da figura, com a fórmula `TEXT(Data,"dd/mm/aa") & " - " & Resumo` que gera o texto da célula. A data usada é a **data da anotação**, e não a de inclusão: por exemplo, a OS de 05/10/24 incluída em 01/11/24 aparece como "05/10/24 - Ordem de Serviço".

4. **Figura comentada da matriz** (fora da planilha, enviada pelo Aderbal em 29/10/2026), com seis balões:
   - "Projeto na forma de árvore de projeto": os filhos aparecem recuados embaixo do pai, por exemplo "OS112-2024-Subscrição - Ano 3" embaixo de "Sg-MAPA/2024-Licença". Quando o pai não tem anotação, a linha leva o caminho até o filho ("Sg-ICMBio-Detzel/2024-Licença/OS02-2024-Subscrição - Ano 1").
   - "Link para abrir o projeto (em outra aba)", no nome do projeto.
   - "As colunas são os tipos de documentos encontrados em ordem alfabética".
   - "Para cada anotação, o texto será a 'DATA - TITULO ANOTAÇÃO'" (o título é o resumo).
   - "Se houver múltiplas anotações do mesmo tipo no mesmo projeto, colocar todas na mesma célula separadas por quebra de linha".
   - "Colocar um link para abrir a anotação em outra aba. Pode ser o link em um texto simples '(A)'".

## 2. Regras

| # | Regra |
|---|---|
| R1 | Relatório novo na árvore de relatórios do projeto, nó **Andamento**, logo abaixo de "Anotações - Qtd. por Período" (é o único de anotações na árvore). |
| R2 | Mesmo direito do relatório de anotações (`ProjetoAnotacaoVer`). Cada anotação passa pela mesma checagem de visibilidade (`possuiDireitoVisualizacao`). |
| R3 | Considera as anotações do projeto escolhido e de todos os filhos, com a data da anotação dentro do período (padrão: 6 meses até o fim do mês atual). |
| R4 | Linhas em **forma de árvore de projeto**: um projeto por linha, com os filhos recuados embaixo do pai. Só ficam os projetos com anotação e os pais necessários para chegar a eles; a cadeia de pais sem anotação vira caminho compactado na linha do filho, como no "Anotações - Qtd. por Período". O nome do projeto é **link para o painel do projeto, em outra aba**. |
| R5 | Colunas: uma por tipo de anotação que aparece no resultado, em **ordem alfabética**. Tipo sem anotação não vira coluna. |
| R6 | Célula: só as anotações do próprio projeto (as dos filhos ficam nas linhas deles), uma por linha, `dd/mm/aa - resumo`, a mais recente primeiro, separadas por quebra de linha. Depois do texto vem o link "(A)", que abre o Detalhar Anotação **em outra aba**. Se a anotação não tiver resumo (o resumo é opcional por tipo desde o 577701), mostra só a data. |
| R7 | **Exibir apenas OS** (padrão desmarcado): marcado, entram só as anotações de OS e dos itens abaixo dela. |
| R8 | **Exibir Encerrados** (padrão marcado): desmarcado, saem as anotações de projeto finalizado ou cancelado (o que não está aberto, como o "Exibir Projetos Inativos" do relatório irmão). |
| R9 | **Exibir Anexos** (padrão desmarcado): marcado, cada linha da célula ganha os nomes dos arquivos anexos, separados por "; ", cada um com link para abrir o arquivo. |
| R10 | Tipos de Anotação fica fora desta entrega, como o próprio chamado permite (todos os tipos sempre). |
| R11 | Agendamento e e-mail funcionam como nos demais relatórios. No e-mail, os links usam o endereço da aplicação. |

## 3. O que existe no código

| Peça | Onde | Uso aqui |
|---|---|---|
| Relatório irmão "Anotações - Qtd. por Período" | [`RelatorioProjetoAnotacoesPeriodo.java`](../src/main/java/com/miranteinfo/alocpro/view/projeto/RelatorioProjetoAnotacoesPeriodo.java) | Padrão de colunas dinâmicas no `GeradorHtmlArvore` e de link do projeto para a aba de anotações. O Lucas o renomeou de "Qtd. Anotações por Período" para "Anotações - Qtd. por Período" em 02/11/2024 (`04f0c30bcc`), no mesmo dia do chamado, e o subiu na lista de Andamento. É a família "Anotações - ..." onde o Mapa entra. |
| Relatório "Anotações/Anexos" (aba do painel, é a figura do anexo) | [`RelatorioProjetoAnotacoes.java`](../src/main/java/com/miranteinfo/alocpro/view/projeto/RelatorioProjetoAnotacoes.java) | Mesma carga: `anotacoesPrjBean.getAnotacoesProjeto(arvore, codPrj, true, periodo, filtro)`, com o filtro de visibilidade. |
| Árvore de relatórios | [`ListarRelatoriosProjetoPage.java`](../src/main/java/com/miranteinfo/alocpro/wicket/projeto/relatorios/ListarRelatoriosProjetoPage.java), linha 136 | `node.addItem(RelatorioProjetoAnotacoesMapa.class)` logo depois do irmão. |
| Link do anexo | `ProvedorConteudoAnexoAnotacao(ref).gerarHtmlReferencia(vOut, ref.getDescricao())`, já usado em [`RelatorioProjetoEquipeDIRF.java`](../src/main/java/com/miranteinfo/alocpro/view/projeto/RelatorioProjetoEquipeDIRF.java) linha 90 | Link de cada arquivo (R9). |
| Link do projeto | `AlocproLink.forPrj(ctx, prj).getPainelPrj()` com `"_blank"`, já usado em [`RelatorioProjetoValorAgregadoDesatualizado.java`](../src/main/java/com/miranteinfo/alocpro/view/projeto/RelatorioProjetoValorAgregadoDesatualizado.java) linha 88 e em `RelatorioOSSaldoContrato` | Nome do projeto abrindo o painel em outra aba (R4). |
| Detalhar anotação | [`DetalharAnotacaoPrjPage.java`](../src/main/java/com/miranteinfo/alocpro/wicket/projeto/anotacoes/DetalharAnotacaoPrjPage.java) (`ActionOf<PrjAnotacao>`) | Link "(A)" (R6), via `RegistroAcao.criarAcao(anotacao, user, DetalharAnotacaoPrjPage.class)`. |
| Situação e tipo do projeto | `InfoProjeto.isAberto()` e `isFilhoOS()` | R8 e R7. `isFilhoOS()` é verdadeiro para a OS e os itens abaixo dela, e para no contrato e na vigência. |

## 4. Solução

1. **Classe nova** `view/projeto/RelatorioProjetoAnotacoesMapa extends RelatorioAbstratoProjeto`, título "Anotações - Mapa", `possuiDireitoAcesso` com `ProjetoAnotacaoVer`.
2. **Parâmetros**, nesta ordem: `conf().addParametroPeriodo(new PeriodoRelativo(6, Mensal, FIM_PERIODO_ATUAL)).withObrigatorio()`, `addParametroBoolean("apenasOS", "Exibir apenas OS").setValor(false)`, `addParametroBoolean("exibirEncerrados", "Exibir Encerrados").setValor(true)`, `addParametroBoolean("exibirAnexos", "Exibir Anexos").setValor(false)`, todos com `exibirParametro()`. Leitura com `getValorBoolean(nome, padrão)`, para agendamento salvo sem o parâmetro.
3. **Carga**: `getAnotacoesProjeto(arvore, getCodProjeto(), true, periodo, filtro)`, com o filtro de visibilidade mais R7 e R8 sobre `arvore.get(anotacao.getCodPrj())`.
4. **Matriz**: agrupar por projeto (`codPrj`) e, dentro dele, por tipo. Colunas = tipos presentes, em ordem alfabética da descrição.
5. **Árvore**: mesmo caminho do relatório irmão. Pai sem anotação e com um filho só é absorvido pelo filho, que leva o caminho ("pai/filho"); com vários filhos, o pai fica como linha vazia. `arvore.eliminarItensArvore(arvore.get(getProjeto()), filtro)` mantém útil só o projeto que está no mapa e deixa os pais como sombra. O `GeradorHtmlArvore` usa `setLeitorArvore` e `getNomeCompactado()`.
6. **Geração**: coluna "Projeto" `tpString` com `setLink(AlocproLink.forPrj(ctx, prj).getPainelPrj(), "_blank")` e uma coluna `tpHtml` por tipo. Resumo e nome do arquivo escapados com `AlocproStrings.plainTextToHtml` (o `Lnk.getHrefBlank` não escapa), linhas unidas por `<br/>`. O "(A)" usa `AlocproLink.forView(vOut).getDetalharAnotacaoPrj(...)`, o mesmo link da busca textual, em vez de `RegistroAcao.criarAcao`, que instancia uma página Wicket por anotação. O anexo usa `ProvedorConteudoAnexoAnotacao.getLnk(vOut)` só com o nome; o `gerarHtmlReferencia` traria ícone e tamanho, pesado para a lista com "; ". Ordem alfabética com `AlocproToolkit.compareToIgnoreAcentos`; resultado vazio mostra a mensagem padrão "Sem dados no período.".

Sem script SQL, sem direito novo, sem alteração nos relatórios que já existem.

## 5. Arquivos

| Arquivo | Ação |
|---|---|
| `view/projeto/RelatorioProjetoAnotacoesMapa.java` | novo |
| `wicket/projeto/relatorios/ListarRelatoriosProjetoPage.java` | uma linha na árvore (Andamento) |

Os dois são latin1/CRLF: editar com script node.

## 6. Dúvidas (detalhes que faltam no chamado, com proposta)

| # | Dúvida | Proposta |
|---|---|---|
| D1 | Nome: o chamado diz "Anotação - Mapa", mas o relatório irmão foi renomeado para "Anotações - Qtd. por Período" no dia do chamado. | **"Anotações - Mapa"**, no padrão da família. |
| D2 | Ordem das colunas de tipo. | ✅ **Decidida pela figura:** ordem alfabética. A planilha de exemplo põe as de OS antes das de Nota Fiscal, mas o balão manda a ordem alfabética. |
| D3 | O que é o "(A)" no fim de cada anotação. | ✅ **Decidida pela figura:** link para abrir a anotação em outra aba. Os anexos vêm à parte, pelo "Exibir Anexos". |
| D4 | Nome do projeto na linha. | ✅ **Decidida pela figura:** forma de árvore de projeto, com link para o projeto em outra aba (R4). |
| D5 | "Exibir apenas OS": só a OS em si ou também os itens dela? | OS **e** os itens abaixo dela (`isFilhoOS`). |
| D6 | "Encerrado": finalizado, cancelado ou os dois? | Os dois (o que não está aberto). |

O filtro **Tipos de Anotação** fica para a versão 2, como o chamado permite (R10). Se o Daniel quiser já nesta entrega, o componente atual escolhe um tipo só; com vários, é um componente novo, e aí a dificuldade sobe para 🟡.

## 7. Testes

**Em tela no DEV02.** A última anotação de projeto no DEV02 é de 02/2026, então é preciso escolher um período até essa data, por exemplo 08/2025 a 02/2026. Os arquivos do DEV02 são mascarados (5 bytes), então o link do anexo abre, mas o conteúdo não. Para conferir um arquivo de verdade, anexe um novo.

1. Na raiz de um contrato com OS e notas fiscais: abrir Relatórios > Andamento e ver "Anotações - Mapa" logo abaixo de "Anotações - Qtd. por Período".
2. Gerar com os padrões: projetos em árvore (filho recuado embaixo do pai), colunas por tipo presente em ordem alfabética, células `dd/mm/aa - resumo (A)` com a mais recente em cima. O nome do projeto abre o painel em outra aba e o "(A)" abre a anotação certa em outra aba.
3. Comparar com a aba "Anotações/Anexos" do mesmo projeto e período, com "Exibir Anotações Projetos Filhos" marcado: mesma quantidade de anotações.
4. Marcar "Exibir apenas OS": somem as anotações do contrato e da vigência e ficam as da OS e dos itens dela.
5. Desmarcar "Exibir Encerrados": somem as de projeto finalizado ou cancelado.
6. Marcar "Exibir Anexos": aparecem os nomes dos arquivos separados por "; ", cada um com link.
7. Usuário sem `ProjetoAnotacaoVer` não vê o relatório. Anotação com visibilidade restrita não aparece para quem não pode vê-la.
8. Agendar com envio por e-mail: links com o endereço da aplicação.
