# Spec — Exibir arquivo .MSG (email do Outlook) no browser

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 577540** · Tipo: Solicitação · Solicitante: Daniel Bordin (Grupo Mirante) · Abertura: 02/11/2024
> **Status:** Design — **não implementado**. ⚠️ **Não é um quick win** (feature multi-classe) e tem **bloqueio de dependência POI a resolver antes** — ver §7.3.
> **Nível de dificuldade:** 🟡 Média — parser MSG (poi-scratchpad) e viewer de email já existem, mas reaproveitar exige conversão MSG→MIME e interceptar vários pontos de anexo.
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

## 1. Descrição do chamado

Transcrição literal do chamado:

> "Arquivo de anotação do tipo email exportado a partir do Outlook (extensão MSG) são baixados para serem salvos para depois abrir como arquivo local. Alterar para exibir o conteúdo do arquivo e permitir ver os anexos na aba que é aberta no browser (conforme ocorre com PDF e outros tipos de arquivos). Havia um visualizador de email que fazia essa lógica para email lidos via IMAP. Talvez o mesmo visualizador funcione ou possa ser adaptado."

Hoje, um `.msg` anexado a uma anotação é entregue como **download** (o navegador salva o arquivo), enquanto PDF/JPG/PNG/XML abrem *inline* na aba. O solicitante quer que o `.msg` também abra na aba, exibindo corpo + anexos do email, reaproveitando (se possível) o visualizador de email já existente para caixas lidas via IMAP.

## 2. Objetivo

Ao clicar num anexo `.msg` de anotação, abrir uma nova aba que **renderiza o conteúdo do email** (assunto, remetente/destinatários, corpo HTML/texto, imagens inline) e **lista os anexos internos do email** com links próprios de visualização/download — comportamento equivalente ao já existente para emails capturados via IMAP (`ViewArqEmail`), em vez de forçar o download do arquivo bruto.

## 3. Análise do sistema atual (refs `arquivo:linha`)

### 3.1 Como o `.msg` é entregue hoje (fluxo de download)

Anexos de anotação são entidades `ArqReferencia` (metadados: nome, hash, bytes) + `ArqConteudo` (conteúdo deflacionado no banco). A listagem de anexos monta, para cada arquivo, um link que aponta para o provedor de streaming:

- [`ArquivosPanel.java:30-38`](../src/main/java/com/miranteinfo/alocpro/wicket/projeto/anotacoes/ArquivosPanel.java) — para cada `ArqReferencia` gera um `<a href>` via `ProvedorConteudoArquivo.of(item.getModelObject()).getLnk(...)`. **Nenhum tratamento especial por extensão** — todo arquivo vira link de conteúdo bruto.
- [`ProvedorConteudoArquivo.java:80-128`](../src/main/java/info/mirante/develox/view/provedor/ProvedorConteudoArquivo.java) — devolve o `DataSourceComTamanho` com os bytes de `ArqConteudo`.
- [`ServletProvedorConteudoFuturo.java:211-215`](../src/main/java/info/mirante/develox/view/web/ServletProvedorConteudoFuturo.java) — `isExibirInline()` decide o `Content-Disposition`. Só abrem *inline*: **`.pdf`, `.xml`, `.jpeg`, `.jpg`, `.png`** (e `.mp3` em caso especial, linha 133). **`.msg` cai em `attachment` → download.** Este é exatamente o comportamento reclamado.
- Caminho alternativo (anexos de anotação de projeto): [`DownloadArquivoLink.java:84-91`](../src/main/java/com/miranteinfo/alocpro/wicket/arq/DownloadArquivoLink.java) — `onClick()` só marca `ContentDisposition.INLINE` para `.pdf`; demais extensões (incl. `.msg`) baixam.

> Observação importante: para PDF/imagem, "abrir na aba" funciona porque **o próprio browser sabe renderizar** esses tipos. O `.msg` **não é um tipo nativo do browser** — não basta trocar o `Content-Disposition` para `inline`; é preciso **converter o `.msg` em HTML no servidor** e servir esse HTML. É aí que entra o viewer de email existente.

### 3.2 O viewer de email existente (IMAP) — candidato a reúso

Existe um visualizador completo de email que renderiza um `javax.mail.internet.MimeMessage` como HTML:

- [`ViewArqEmail.java:49-82`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ViewArqEmail.java) — `gerar(ViewOutput)` monta cabeçalho (assunto, de/para/cc, foto do remetente), corpo e imagens *related*. Métodos-chave: `exibirCorpo()` (linha 115: corpo `text/html` reescreve `src="cid:..."` para URL do provedor, `text/plain`, `image/*`) e `gerarAnexos()` (linha 176: cada anexo vira link via `ProvedorConteudoArqEmail`).
- [`EstruturaEmail.java:97-165`](../src/main/java/com/miranteinfo/alocpro/bean/arq/EstruturaEmail.java) — `of(MimeMessage)` percorre recursivamente o MIME e classifica cada `Part` em **corpo / related (imagens inline) / anexo**. Detecta tipos com Tika ([`EstruturaEmail.java:32,42-95`](../src/main/java/com/miranteinfo/alocpro/bean/arq/EstruturaEmail.java)). **Toda a lógica de renderização depende de um `MimeMessage`.**
- [`ProvedorConteudoArqEmail.java:99-215`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ProvedorConteudoArqEmail.java) — provedor que faz *stream* de: (a) uma `Part` individual do email (anexo/imagem inline, linha 144) ou (b) o `.eml` inteiro (linha 188). Localiza a `Part` por nome (`findPartByName`, linha 109).
- Página que hospeda um viewer de email num painel Wicket (modelo de referência para a nova página): [`DetalharEmailPage.java:74-133`](../src/main/java/com/miranteinfo/alocpro/wicket/admin/DetalharEmailPage.java) — usa `AbstractGeradorHtmlPanel` + `ViewOutput` para montar o HTML do email e um behavior de download por anexo.
- Origem IMAP dos emails: [`CrawlerEmail.java:407-508`](../src/main/java/com/miranteinfo/alocpro/crawler/email/CrawlerEmail.java) — `LeitorInBox` conecta via `javax.mail` (`Store`/`Folder`, protocolo IMAP) e produz `MimeMessage`s consumidos pela mesma `EstruturaEmail`. Confirma o "visualizador de email para IMAP" citado no chamado.

### 3.3 Existe parser de `.msg` no classpath?

- **`javax.mail` (com.sun.mail:javax.mail 1.6.2, [`pom.xml:969-974`](../pom.xml))** lê **EML/MimeMessage**, formato RFC 822. **Não lê `.msg`** — `.msg` é formato proprietário OLE2/CFB do Outlook.
- **poi-scratchpad 3.17 ESTÁ disponível** no repositório local (`~/.m2/.../poi-scratchpad/3.17/poi-scratchpad-3.17.jar`), trazido transitivamente por **`tika-parsers` 1.18** ([`pom.xml:139-141,1278`](../pom.xml)). Esse jar contém `org.apache.poi.hsmf.MAPIMessage`, **o parser de arquivos MSG do Outlook**. O Tika 1.18 (já usado no projeto, ex.: `EstruturaEmail` linha 32 e `TextExtractor`) também parseia MSG via `OutlookExtractor`.
- **Porém**: `MAPIMessage` produz um objeto POI (assunto, remetente, corpos texto/HTML/RTF, anexos), **não um `javax.mail.MimeMessage`**. O viewer existente (`EstruturaEmail`/`ViewArqEmail`) opera **exclusivamente sobre `MimeMessage`**. Logo, reusar o viewer exige uma **camada de conversão MSG → MimeMessage** (ou uma reimplementação do render sobre `MAPIMessage`).

### 3.4 Idioma de construção de MimeMessage já usado no código

Já existe o padrão de criar `MimeMessage` a partir de um `InputStream` de bytes EML — útil para a Alternativa A:

- [`EmailSenderSMTP.java:223`](../src/main/java/info/mirante/develox/email/EmailSenderSMTP.java) — `new MimeMessage(null, anexo.getInputStream())`.
- `ArqEmail.getConteudoAsMessage()` (usado em [`ViewArqEmail.java:92`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ViewArqEmail.java), [`ProvedorConteudoArqEmail.java:110`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ProvedorConteudoArqEmail.java), [`FacadeIndexador.java:707`](../src/main/java/com/miranteinfo/alocpro/index/FacadeIndexador.java)) — mesma ideia: bytes → `MimeMessage`.

## 4. Solução proposta (design + pontos de alteração)

**Estratégia recomendada: Alternativa A (converter MSG → MimeMessage e reusar o viewer existente).** Maximiza reúso do render já testado (`EstruturaEmail` + `ViewArqEmail`) e mantém consistência visual com o viewer IMAP.

### 4.1 Fluxo alvo

1. Usuário clica num anexo `.msg` na listagem de anotações.
2. Em vez de baixar, abre nova aba numa **página Wicket nova** (ex. `VisualizarMsgPage`), passando o hash/`cod` do `ArqReferencia`.
3. A página carrega os bytes do `ArqConteudo`, **converte o `.msg` em `MimeMessage`**, instancia `ViewArqEmail`-equivalente e renderiza corpo + anexos via `ViewOutput` (espelhando [`DetalharEmailPage.java:74-133`](../src/main/java/com/miranteinfo/alocpro/wicket/admin/DetalharEmailPage.java)).
4. Anexos internos do email são servidos por um provedor (novo ou adaptado de `ProvedorConteudoArqEmail`), abrindo *inline* quando o browser suporta (PDF/imagem) ou baixando caso contrário.

### 4.2 Pontos de alteração

| # | Ponto | Ação | Arquivo (referência, **não editar `.java`**) |
|---|-------|------|------|
| 1 | **Conversor MSG→MimeMessage** | Nova classe utilitária. Ler `.msg` com `org.apache.poi.hsmf.MAPIMessage` (poi-scratchpad, já no classpath) e montar `MimeMessage` (assunto, from/to/cc, corpo HTML ou texto, anexos com `Content-ID` para imagens inline). Alternativamente, avaliar lib dedicada (ver §5). | nova, ex. `com/miranteinfo/alocpro/bean/arq/ConversorMsgParaMime.java` |
| 2 | **Adaptar viewer para fonte "avulsa"** | `ViewArqEmail`/`EstruturaEmail` hoje partem de `ArqEmail`. Extrair/generalizar para aceitar um `MimeMessage` avulso (não persistido como `ArqEmail`). `EstruturaEmail.of(MimeMessage)` já é agnóstico; o acoplamento está em `ViewArqEmail` (usa `email.getFrom()`, foto etc.). | [`ViewArqEmail.java:45-93`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ViewArqEmail.java), [`EstruturaEmail.java:97`](../src/main/java/com/miranteinfo/alocpro/bean/arq/EstruturaEmail.java) |
| 3 | **Página viewer** | Nova `VisualizarMsgPage` (Wicket `@MountPath`) espelhando `DetalharEmailPage`: `AbstractGeradorHtmlPanel` + `ViewOutput`, recebe `ArqReferencia`/hash, chama o conversor (#1) e o viewer (#2). | nova, modelo: [`DetalharEmailPage.java:48-133`](../src/main/java/com/miranteinfo/alocpro/wicket/admin/DetalharEmailPage.java) |
| 4 | **Provedor de anexos internos** | Servir cada `Part`/anexo do email do `.msg`. Reusar/generalizar `ProvedorConteudoArqEmail` (hoje amarrado a `ArqEmail`) para uma fonte `MimeMessage` avulsa, ou usar `VisualizarArquivoResource` para os anexos. | [`ProvedorConteudoArqEmail.java:99-215`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ProvedorConteudoArqEmail.java) |
| 5 | **Interceptar o link `.msg`** | Onde os anexos são listados, detectar extensão `.msg` e apontar para `VisualizarMsgPage` (nova aba) em vez do link de download bruto. | [`ArquivosPanel.java:33-35`](../src/main/java/com/miranteinfo/alocpro/wicket/projeto/anotacoes/ArquivosPanel.java) e demais listagens (ver §6) |

> Nota: **não** é suficiente alterar `isExibirInline()` em [`ServletProvedorConteudoFuturo.java:211`](../src/main/java/info/mirante/develox/view/web/ServletProvedorConteudoFuturo.java) adicionando `.msg`, porque servir os bytes brutos do `.msg` com `Content-Disposition: inline` faria o browser tentar (e falhar) exibir um binário OLE2. A renderização precisa ser **HTML gerado no servidor**.

## 5. Alternativas

**Alternativa A (recomendada) — Converter MSG → MimeMessage e reusar `ViewArqEmail`.**
- Prós: reúso máximo do render já testado (corpo HTML, imagens inline via `cid`, listagem de anexos, detecção Tika); UI idêntica ao viewer IMAP.
- Contras: conversão MSG→MIME é a parte frágil. Outlook frequentemente guarda o corpo apenas como **RTF comprimido** (`compressedRTF`), sem HTML/texto limpo; mapear `Content-ID` das imagens inline exige cuidado. poi `MAPIMessage` **não** emite MIME pronto — a conversão é manual.
- Sublinha A.1: implementar a conversão "à mão" com poi-scratchpad (`MAPIMessage` → montar `MimeMessage` com `new MimeMessage(null, ...)` no estilo de [`EmailSenderSMTP.java:223`](../src/main/java/info/mirante/develox/email/EmailSenderSMTP.java)).
- Sublinha A.2: adicionar dependência dedicada, ex. **`org.simplejavamail:outlook-message-parser`**, que parseia `.msg` (incl. corpo RTF/HTML e anexos/CID) para um modelo estruturado, facilitando a montagem do `MimeMessage`. Custo: nova dependência no [`pom.xml`](../pom.xml); avaliar compatibilidade com Java 8.

**Alternativa B — Novo viewer sobre `MAPIMessage` (sem passar por MimeMessage).**
- Escrever `ViewMsg` que emite HTML direto a partir de `MAPIMessage` (poi-scratchpad), espelhando a estrutura de `ViewArqEmail`.
- Prós: evita reconstrução frágil de MIME; usa a API que realmente entende `.msg`.
- Contras: duplica a lógica de render/anexos; menos reúso; ainda precisa lidar com RTF→HTML.

**Alternativa C (descartada) — Só trocar `Content-Disposition` para inline.**
- Inviável: `.msg` não é tipo nativo do browser (ver nota em §4.2).

## 6. Impacto / arquivos candidatos

- **Novos:** `ConversorMsgParaMime` (util MSG→MIME), `VisualizarMsgPage` (+ `.html` Wicket), possivelmente provedor de anexos avulso.
- **Adaptação (generalizar de `ArqEmail` para `MimeMessage` avulso):** [`ViewArqEmail.java`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ViewArqEmail.java), [`ProvedorConteudoArqEmail.java`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ProvedorConteudoArqEmail.java). `EstruturaEmail` já serve sem mudança.
- **Wiring (detectar `.msg` e linkar o viewer) — múltiplos pontos de listagem de anexo a mapear:**
  - [`ArquivosPanel.java:33-35`](../src/main/java/com/miranteinfo/alocpro/wicket/projeto/anotacoes/ArquivosPanel.java) (anotações de projeto)
  - [`DownloadArquivoLink.java:84-91`](../src/main/java/com/miranteinfo/alocpro/wicket/arq/DownloadArquivoLink.java)
  - Verificar outras telas que listam `ArqReferencia`/anexos de anotação (ex.: `FormularioAnotacoes`, `UploadArquivosPanel`, anotações de RH/mirante) — **a confirmar** o inventário completo.
- **Dependência:** possivelmente novo artefato no [`pom.xml`](../pom.xml) (Alternativa A.2); caso contrário, apenas garantir que `poi-scratchpad` seja **dependência declarada** e não só transitiva de `tika-parsers`.

## 7. Riscos e decisões em aberto

1. **Corpo em RTF comprimido:** muitos `.msg` só têm `compressedRTF`; converter RTF→HTML preservando formatação/imagens é o maior risco. Tika/`RTFEditorKit` ajudam, mas com perda. **Decisão em aberto:** aceitar fallback texto-plano quando não houver HTML.
2. **Imagens inline (CID):** mapear `Content-ID` dos anexos embutidos para `src="cid:..."` no corpo — o render existente já reescreve `cid:` ([`ViewArqEmail.java:125`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ViewArqEmail.java)); a conversão precisa preencher os CIDs corretamente.
3. **poi-scratchpad transitivo — ⚠️ BLOQUEIO CONFIRMADO (2026-08-06):** o pom **exclui `poi` (core) e `poi-ooxml`** em [`pom.xml:1127-1134`](../pom.xml) e `poi-ooxml-schemas` em [`pom.xml:242-245`](../pom.xml). Como `poi-scratchpad` (onde vive `MAPIMessage`) **depende de `poi` core**, há risco real de `NoClassDefFoundError` em runtime mesmo com o `.jar` presente no `.m2`. **Antes de implementar:** declarar `poi` + `poi-scratchpad` explicitamente como dependências e validar o classpath do *war* (`mvn dependency:tree`, deploy de teste). Isso, somado ao corpo RTF comprimido (§7.1) e à necessidade de validar com `.msg` reais num app rodando, tira esta tarefa da categoria "quick win".
4. **Lib dedicada vs. manual:** decidir entre A.1 (poi manual) e A.2 (`outlook-message-parser`) — trade-off esforço vs. nova dependência.
5. **Segurança/XSS:** o corpo HTML do `.msg` é conteúdo externo renderizado inline; avaliar sanitização (o viewer IMAP hoje injeta o HTML direto — mesmo risco pré-existente).
6. **Inventário de telas** que listam `.msg` para wiring completo — **a confirmar**.

## 8. Referências de código (tabela)

| Componente | Arquivo:linha | Papel |
|---|---|---|
| Listagem de anexos (link download) | [`ArquivosPanel.java:30-38`](../src/main/java/com/miranteinfo/alocpro/wicket/projeto/anotacoes/ArquivosPanel.java) | Ponto de interceptação do `.msg` |
| Provedor de conteúdo de arquivo | [`ProvedorConteudoArquivo.java:80-128`](../src/main/java/info/mirante/develox/view/provedor/ProvedorConteudoArquivo.java) | Stream do `ArqConteudo` bruto |
| Decisão inline vs. download | [`ServletProvedorConteudoFuturo.java:211-215`](../src/main/java/info/mirante/develox/view/web/ServletProvedorConteudoFuturo.java) | `isExibirInline()`: só pdf/xml/jpg/png |
| Link download (inline só p/ pdf) | [`DownloadArquivoLink.java:84-91`](../src/main/java/com/miranteinfo/alocpro/wicket/arq/DownloadArquivoLink.java) | Outro caminho de download de anexo |
| Viewer de email (render HTML) | [`ViewArqEmail.java:49-205`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ViewArqEmail.java) | Reúso alvo (corpo + anexos) |
| Parser de estrutura MIME | [`EstruturaEmail.java:97-165`](../src/main/java/com/miranteinfo/alocpro/bean/arq/EstruturaEmail.java) | Classifica corpo/related/anexo de `MimeMessage` |
| Provedor de partes do email | [`ProvedorConteudoArqEmail.java:99-215`](../src/main/java/com/miranteinfo/alocpro/bean/arq/ProvedorConteudoArqEmail.java) | Stream de anexo/imagem inline do email |
| Página host de viewer de email | [`DetalharEmailPage.java:74-133`](../src/main/java/com/miranteinfo/alocpro/wicket/admin/DetalharEmailPage.java) | Modelo p/ nova `VisualizarMsgPage` |
| Origem IMAP (viewer citado) | [`CrawlerEmail.java:407-508`](../src/main/java/com/miranteinfo/alocpro/crawler/email/CrawlerEmail.java) | `LeitorInBox` IMAP → `MimeMessage` |
| Bytes → MimeMessage (idioma) | [`EmailSenderSMTP.java:223`](../src/main/java/info/mirante/develox/email/EmailSenderSMTP.java) | Padrão p/ construir `MimeMessage` |
| javax.mail (EML, não MSG) | [`pom.xml:969-974`](../pom.xml) | com.sun.mail:javax.mail 1.6.2 |
| Tika (traz poi-scratchpad) | [`pom.xml:139-141,1278`](../pom.xml) | tika-parsers 1.18 → poi-scratchpad 3.17 (MSG parser `MAPIMessage`) |
