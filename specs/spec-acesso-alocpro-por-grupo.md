# Spec — Restringir acesso ao AlocPro ao grupo AD acesso.alocpro (+ ajustes de automação cadastral)

> **Spec** — artefato de Spec-Driven Development (requisitos + design técnico)
> **Chamado 718590** · Tipo: Solicitação · Solicitante: Alan Goncalves (Suporte/Infra-Estrutura) · Abertura: 09/04/2026
> **Status:** ⏸️ **Descartado por ora:** branch `feat/tarefas-prioritarias` apagada em 09/10/2026 a pedido do Aderbal (tarefa simples, refazer se precisar; último commit era `2984802da`). Parte B (planilha) nunca foi implementada. A spec continua valendo como desenho.

> ✅ **PARTE A IMPLEMENTADA (commit local na branch `feat/tarefas-prioritarias`, refeita sobre a desenv em 30/09/2026) — DESLIGADA POR PADRÃO.** Check `memberOf` pós-autenticação, só para usuário interno: `PropriedadesSistema.getLDAPGrupoAcessoObrigatorio()` (chave `autenticacao/ldap/grupo_acesso`) + `LDAPAuthentificator.isMembroDoGrupo` (bind admin + filtro `memberOf:1.2.840.113556.1.4.1941:` = regra de cadeia) + 2 chamadas em `MiranteAuthenticationProvider` (ramos LDAP e senha-banco). **Propriedade vazia/ausente = desligado → comportamento atual 100% preservado.** `mvn compiler:compile` OK.
> **⚠️ NÃO é 100% — pendências:** (1) **validar com AD real** — só ativar setando `autenticacao/ldap/grupo_acesso=CN=acesso.alocpro,OU=Grupos,OU=AzureSync,OU=MIRANTE,DC=miranteinfo,DC=com` **após** confirmar conectividade LDAP (a conta de serviço precisa ler `memberOf`); (2) comportamento: `!isADConfigurado()` = fail-open (não bloqueia); erro de LDAP = fail-closed (bloqueia login) — confirmar se aceitável; (3) usuários **externos** (clientes) não são afetados (proposital); (4) **Parte B** (automação cadastral por campo) — não implementada.
> **Nível de dificuldade:** 🟡 Média — a Parte A exige um novo ponto de checagem de `memberOf` na autenticação (não existe hoje) e conectividade LDAP/AD confiável, mas reaproveita padrões de busca LDAP já presentes no código.
>
> Escala: 🟢 Baixa · 🟡 Média · 🔴 Alta.

> ✅ **Planilha "Detalhes_ajuste_atualizacao_cadastral" recebida (Parte B).** Regras por campo abaixo.

### Parte B — regras da planilha (automação de atualização cadastral)

| Campo (Alocpro) | AD / destino | Comportamento |
|---|---|---|
| **Nome de Guerra** | — | **Sem necessidade de abertura de chamado** |
| **Cargo** | AD `title` (ex.: "Supervisor de Infraestrutura e Suporte") | Alteração **automática no AD** pelo Alocpro + **chamado automático** (abre e fecha) só para registro — encerrado com *"Atendimento realizado por automação"*, atendente **"Alocpro"/"Sistema"**. Além disso, sempre que mudar o cargo, **abrir chamado à parte ao Suporte** para revisão de permissões de acesso. |
| **Nome Completo** | AD (atributos citados) (ex.: "Maxello Souza Fonseca") | Alteração **automática no AD** + chamado automático de registro (mesmo padrão do Cargo). |
| **Login** | AD (manual, via chamado) | Campo p/ exibição na agenda do Alocpro; **só o DP altera** (não livre ao colaborador). Alteração → **chamado automático ao Suporte** p/ ajuste manual no AD. **Ajuste de fluxo:** hoje o login muda primeiro no Alocpro e o chamado chega depois → o colaborador **perde acesso por divergência**. Novo fluxo: colaborador pede troca de nome ao DP → DP/sistema confere na **Receita Federal** → validada + confirmada troca de login → solicitação ao Suporte p/ alterar no AD → volta ao DP p/ ajustar no Alocpro. |
| **Email** (corporativo) | AD (manual, via chamado) | Continua gerando **chamado automático ao Suporte** p/ ajuste manual, mesmo fluxo do Login. |
| **Telefone Celular** | **bitsac** (manual, via chamado) | Alteração → **chamado ao Suporte** p/ ajuste manual do número **no bitsac** (ver chamado **557363**). |

> **Cross-links:** o ajuste de Login aqui é o mesmo redesenho de **564250** (fluxo login/equipamento); "bitsac" conecta com **557363** (WhatsApp). As duas planilhas enviadas (`.xlsx` e `(1).xlsx`) têm **conteúdo idêntico**.

## 1. Descrição do chamado

Texto verbatim do solicitante:

> "Solicitamos ajustes na atual automação de abertura de chamados automáticos quando há atualização cadastral. Requisitos: Ajustes de acesso ao alocpro por grupo. Solicitamos a configuração para que apenas os usuários pertencentes ao grupo abaixo tenha acesso ao alocpro. grupo: acesso.alocpro; distinguishedName: CN=acesso.alocpro,OU=Grupos,OU=AzureSync,OU=MIRANTE,DC=miranteinfo,DC=com. Em relação aos ajustes referente às solicitações abertas relacionadas à alteração no cadastro, todos os detalhes dos ajustes necessários estão na planilha anexada."

O chamado tem **duas partes independentes**:

- **(A) Restringir o acesso ao AlocPro ao grupo AD `acesso.alocpro`** — apenas usuários membros do grupo `CN=acesso.alocpro,OU=Grupos,OU=AzureSync,OU=MIRANTE,DC=miranteinfo,DC=com` devem conseguir logar. Especificável agora — foco desta spec.
- **(B) Ajustes na automação de abertura de chamados na atualização cadastral** — ✅ **planilha recebida**; regras por campo no bloco "Parte B" do cabeçalho. Ainda **não implementada** — ver seção 5.

## 2. Objetivo

Garantir que **somente** usuários pertencentes ao grupo de AD `acesso.alocpro` consigam autenticar/entrar no AlocPro, sem quebrar:

- o fluxo de **usuários externos** (freelancers/candidatos), que não pertencem ao AD e autenticam por senha no banco;
- os fluxos de serviço/mobile (JWT) e Painel de Candidatos;
- os ambientes de desenvolvimento/homologação, onde a senha é bypassada.

A restrição por grupo deve ser **configurável** (ligar/desligar e informar o DN do grupo), para não travar ambientes sem conectividade ao AD.

## 3. Análise do sistema atual — autenticação/acesso (refs arquivo:linha)

### 3.1 Fluxo de autenticação (Spring Security)

O provider principal é `MiranteAuthenticationProvider`, registrado no contexto Spring e ligado ao `authentication-manager` padrão:

- [`applicationContext.xml:173-188`](../src/main/webapp/WEB-INF/applicationContext.xml) — declara `miranteUserDetailsService`, `miranteAuthenticationProvider` (com `userDetailsService` e `facadeAcessoProjeto`) e o `authentication-manager` alias `authenticationManager`.
- [`applicationContext.xml:265-309`](../src/main/webapp/WEB-INF/applicationContext.xml) — `<security:http>` principal: **todas as URLs** exigem apenas `hasRole('ROLE_USER')` (`intercept-url pattern="/**"` em `applicationContext.xml:306`), com `form-login` em `/login`.

O núcleo da checagem de credenciais está em:

- [`MiranteAuthenticationProvider.additionalAuthenticationChecks`](../src/main/java/com/miranteinfo/springsecurity/MiranteAuthenticationProvider.java) — `MiranteAuthenticationProvider.java:62-134`. Lógica atual:
  1. `identificarPessoaLogin(username)` resolve a `Pessoa` pelo e-mail (`MiranteAuthenticationProvider.java:71`); se nula → `UsernameNotFoundException`.
  2. **Usuário externo** (`pessoa.isUsuarioExterno()`, linha 77): autentica pela senha no banco e exige perfil de cliente em projeto ativo (`possuiPerfilClienteEmProjetoAtivo`, linha 87).
  3. **Usuário interno** (linha 101): se `PropriedadesSistema.isAutenticacaoLDAPAtiva()` **e** `cadastroUsuarioBean.isUsuarioAD(pessoa)` (linha 103) → autentica no LDAP via `autenticarSenhaLDAP(...)` (linha 104). Caso contrário, autentica pela **senha no banco** (`autenticarSenhaPessoa`, linha 117).
  4. Em qualquer sucesso: `setUserInSession(...)` grava o `AlocproUser` na sessão (linhas 89/108/121/136).

**Ponto crítico:** não existe **nenhuma** verificação de pertencimento a grupo (`memberOf`) em nenhum ramo. Basta ter uma `Pessoa` cadastrada e credencial válida para entrar.

### 3.2 Concessão de ROLE / autoridade

- [`MiranteUserDetailsService.loadUserByUsername`](../src/main/java/com/miranteinfo/springsecurity/MiranteUserDetailsService.java) — `MiranteUserDetailsService.java:46-49` cria `AlocproUser`, que **sempre** recebe `ROLE_USER` (`MiranteUserDetailsService.java:53` e construtor `:81-85`). Como o `intercept-url /**` só exige `ROLE_USER`, **todo usuário autenticado tem acesso à aplicação**. Perfis (`AcsPerfil`) e projetos controlam apenas *o que* o usuário pode fazer, não *se* ele entra.

### 3.3 Autenticação LDAP existente (o que dá para reaproveitar)

- [`LDAPAuthentificator`](../src/main/java/com/miranteinfo/springsecurity/LDAPAuthentificator.java) — cliente JNDI/LDAP:
  - `autentificar(usuario, senha)` / `authenticate(...)` (`LDAPAuthentificator.java:50-65`): faz **bind** com `usuario@dominio`; sucesso = credencial válida. **Não filtra por grupo.**
  - `open(...)` (`LDAPAuthentificator.java:71-110`): monta o contexto JNDI, trata códigos de erro do AD (senha expirada `data 532/773`, credencial inválida `data 52e`, bloqueado `data 775`, desabilitado `data 533`).
  - `findAccountByAccountName(...)` (`LDAPAuthentificator.java:168-187`): **padrão de busca já pronto** — `search` com filtro `(&(objectClass=user)(sAMAccountName=%s))`, `SUBTREE_SCOPE`, lendo atributo `distinguishedname`. É a base natural para adicionar leitura de `memberOf`.
  - `buscarLoginUsuariosAD(Set<String> logins)` (`LDAPAuthentificator.java:200-235`): faz **bind como admin** (`createAdminPrincipal()` + `defaultConnectionPassword`) e busca em `defaultLdapFolder`. Modelo direto para consultar o AD com a conta de serviço.
  - `isADConfigurado()` (`LDAPAuthentificator.java:237-239`): `connectionURL` não vazio.

### 3.4 Configuração LDAP/AD atual

- Propriedades LDAP: [`PropriedadesSistema.java:106-132`](../src/main/java/com/miranteinfo/alocpro/prp/PropriedadesSistema.java) — `getLDAPConnectionURL/Folder/ConnectionName/ConnectionPassword/Domain`, `isAutenticacaoLDAPAtiva`, `isAtualizacaoSenhaLDAPAtiva`. As chaves usam prefixo `autenticacao/ldap/...`.
- Valores commitados: [`config.properties:56-62`](../src/main/webapp/WEB-INF/config.properties):
  - `autenticacao/ldap/ativo=false` ← **LDAP de login desligado no config commitado**
  - `connectionURL=ldap://DEP-WSRV1012.miranteinfo.com:389`
  - `connectionName=aviso.alocpro`, `connectionPassword=...`
  - `folder=DC=miranteinfo,DC=com`, `domain=miranteinfo.com`

### 3.5 Integração AD via Hub de TI (canal REST paralelo)

Existe um **segundo** canal de AD, o Hub de TI, hoje **habilitado**:

- [`IntegracaoUsuarioADService`](../src/main/java/com/miranteinfo/alocpro/ad/service/IntegracaoUsuarioADService.java) — `criarUsuario` (`:67-105`) e `desativarUsuario` (`:107-142`) via `POST /ad/usuarios` e `/ad/usuarios/{login}/desativar`.
- Config: [`config.properties:220-223`](../src/main/webapp/WEB-INF/config.properties) — `integracao.ad.url=https://10.0.0.92`, `integracao.ad.habilitar=true`.
- **Importante:** este Hub **não expõe** hoje endpoint para consultar pertencimento a grupo (`memberOf`) — a confirmar com a equipe de Infra. Só cria/desativa usuário.

**Conclusão do estado atual:** hoje o acesso é concedido a qualquer `Pessoa` com credencial válida; não há filtro por grupo de AD. Para a Parte A é necessário **introduzir** um ponto de verificação de `memberOf == acesso.alocpro` na autenticação, com fonte de verdade no AD (via LDAP direto e/ou via novo endpoint no Hub).

## 4. Solução proposta — Parte A: acesso por grupo AD

### 4.1 Estratégia recomendada

Adicionar uma **checagem de pertencimento ao grupo** na autenticação de **usuários internos** (não externos), logo após a validação de credenciais e antes de `setUserInSession(...)`. A verificação deve ser:

- **Configurável** por novas propriedades (ligar/desligar + DN do grupo), no padrão `autenticacao/ldap/...` já existente.
- **Fail-safe conforme decisão de negócio** (ver seção 7): definir se, quando o AD estiver inacessível ou a checagem desligada, o login é **permitido** (comportamento atual) ou **negado**.
- **Não aplicável a usuários externos** (candidatos/freelancers não estão no AD).

### 4.2 Onde alterar (ponto de injeção)

`MiranteAuthenticationProvider.additionalAuthenticationChecks` — ramo do usuário interno em `MiranteAuthenticationProvider.java:101-128`. Após autenticar (LDAP ou senha do banco) e antes de `setUserInSession(user, pessoa, false)` (linhas 108/121), inserir:

```
if (!AcessoGrupoAlocpro.permitido(pessoa)) {
    throw new InsufficientAuthenticationException(
        "Usuário sem acesso ao AlocPro (não pertence ao grupo acesso.alocpro): " + user.getUsername());
}
```

Lançar `InsufficientAuthenticationException` (já importada, `MiranteAuthenticationProvider.java:12`) mantém a mensagem coerente com o tratamento de "usuário sem direito" já usado (linhas 92/95).

### 4.3 Verificação de membership no AD (novo método em `LDAPAuthentificator`)

Adicionar método que consulta o AD com a **conta de serviço** e verifica `memberOf`, reaproveitando o padrão de `findAccountByAccountName`/`buscarLoginUsuariosAD`:

```
public static boolean isMembroDoGrupo(String sAMAccountName, String grupoDN) {
    // bind admin: open(false, createAdminPrincipal(), defaultConnectionPassword, false)
    // filtro: (&(objectClass=user)(sAMAccountName=<login>)(memberOf=<grupoDN>))
    // retorna answer.hasMoreElements()
}
```

Vantagens: usa `memberOf` (checa o grupo direto). Se a política exigir **grupos aninhados**, usar a regra de correspondência em cadeia do AD: `memberOf:1.2.840.113556.1.4.1941:=<grupoDN>` (a confirmar necessidade). O `grupoDN` vem de propriedade nova, com default = `CN=acesso.alocpro,OU=Grupos,OU=AzureSync,OU=MIRANTE,DC=miranteinfo,DC=com`.

### 4.4 Novas propriedades de configuração

Em `PropriedadesSistema` (padrão dos getters LDAP existentes, `PropriedadesSistema.java:106-132`) e em `config.properties` (bloco `:56-62`):

| Chave | Default sugerido | Uso |
|-------|------------------|-----|
| `autenticacao/ldap/grupoAcesso/ativo` | `false` (ligar em produção) | liga/desliga a restrição por grupo |
| `autenticacao/ldap/grupoAcesso/dn` | `CN=acesso.alocpro,OU=Grupos,OU=AzureSync,OU=MIRANTE,DC=miranteinfo,DC=com` | DN do grupo exigido |
| `autenticacao/ldap/grupoAcesso/negarSeIndisponivel` | a definir (ver Riscos) | política quando o AD não responde |

Manter a restrição **desligada** em dev/homologação evita travar o login já bypassado (`isAmbienteDesenvolvimento`, `MiranteAuthenticationProvider.java:178`; homologação, `:185`).

### 4.5 Alternativa (via Hub de TI)

Se a Infra preferir centralizar no Hub (config `integracao.ad.*` já habilitada), a checagem seria um **novo endpoint** no Hub (ex.: `GET /ad/usuarios/{login}/grupos` ou `/pertence-grupo`), consumido por um novo método em `IntegracaoUsuarioADService`. **Requer contrato novo do Hub (a confirmar)** — hoje só existem criar/desativar (`IntegracaoUsuarioADService.java:67,107`). Decisão LDAP-direto vs. Hub em aberto (seção 7).

### 4.6 Fora de escopo (não alterar)

- Usuários externos (`isUsuarioExterno`) — não estão no AD; ramo `MiranteAuthenticationProvider.java:77-99` permanece.
- Painel de Candidatos e canais JWT/mobile (`applicationContext.xml:190-264`) — autenticação própria, sem relação com o grupo corporativo.

## 5. Parte B: automação de atualização cadastral (planilha recebida — não implementada)

✅ Planilha `Detalhes_ajuste_atualizacao_cadastral.xlsx` recebida; as regras por campo estão no bloco **"Parte B — regras da planilha"**, no cabeçalho desta spec.

- Escopo declarado: "ajustes na atual automação de abertura de chamados automáticos quando há atualização cadastral".
- Provável área de código: processos MBPM/Singular de suporte/chamado (ex.: pacote `processo/mbpm/suporte/chamado/` — `ChamadoProcess`/`ChamadoDefinicao`) e os gatilhos de alteração cadastral de `Pessoa`/`RecursoHumano`. **A confirmar** no código.
- Ações pendentes: mapear cada regra da planilha contra o código (campos monitorados, condições de abertura, destinatários/fila, textos) e então detalhar impacto e testes.

## 6. Impacto / arquivos candidatos

Parte A (todos apenas candidatos — `.java` NÃO editados nesta fase, somente análise):

| Arquivo | Alteração prevista |
|---------|--------------------|
| [`MiranteAuthenticationProvider.java`](../src/main/java/com/miranteinfo/springsecurity/MiranteAuthenticationProvider.java) `:101-128` | inserir checagem de grupo no ramo do usuário interno, antes de `setUserInSession` |
| [`LDAPAuthentificator.java`](../src/main/java/com/miranteinfo/springsecurity/LDAPAuthentificator.java) `:168-235` | novo método `isMembroDoGrupo(login, grupoDN)` (bind admin + filtro `memberOf`) |
| [`PropriedadesSistema.java`](../src/main/java/com/miranteinfo/alocpro/prp/PropriedadesSistema.java) `:106-132` | novos getters `grupoAcesso/ativo`, `grupoAcesso/dn`, política de indisponibilidade |
| [`config.properties`](../src/main/webapp/WEB-INF/config.properties) `:56-62` | novas chaves de configuração do grupo |
| (alternativa) [`IntegracaoUsuarioADService.java`](../src/main/java/com/miranteinfo/alocpro/ad/service/IntegracaoUsuarioADService.java) | novo método de consulta de grupo, se optar por checar via Hub |

Parte B: a definir após a planilha.

## 7. Riscos e decisões em aberto

- **Fonte da verdade do grupo (decisão principal):** LDAP direto (`LDAPAuthentificator`, requer `connectionURL`/conta de serviço acessíveis) **ou** novo endpoint no Hub de TI (`integracao.ad.*`). O Hub hoje não tem consulta de grupo (a confirmar).
- **Comportamento com AD indisponível:** negar (mais seguro, mas trava login se o AD/rede cair) vs. permitir (fail-open, mantém disponibilidade). Precisa de decisão de negócio/Infra.
- **LDAP de login está `ativo=false` no config commitado** (`config.properties:56`). A checagem de grupo não pode depender de `isAutenticacaoLDAPAtiva()`; deve ter flag própria e usar a conta de serviço mesmo quando o login não usa LDAP.
- **Grupos aninhados:** confirmar se `acesso.alocpro` pode conter subgrupos — se sim, usar a regra `LDAP_MATCHING_RULE_IN_CHAIN` (`1.2.840.113556.1.4.1941`).
- **Sincronização AzureSync:** o DN vive em `OU=AzureSync` (grupos sincronizados do Entra ID/Azure AD). Confirmar que o AD on-premises consultado por `connectionURL` já reflete o grupo sincronizado (latência de sync pode atrasar concessão/revogação de acesso).
- **Usuários internos que não estão no grupo:** ao ligar a restrição, quem hoje entra deixará de entrar. Recomenda-se rodada de validação (dry-run/log) antes de bloquear, e alinhar com Infra a população inicial do grupo.
- **Dev/homologação:** manter restrição desligada para não conflitar com o bypass de senha (`MiranteAuthenticationProvider.java:178,185`).
- **Parte B bloqueada:** sem a planilha anexa não há especificação; risco de retrabalho se implementada por suposição.

## 8. Referências de código (tabela)

| Ref | Local | Papel |
|-----|-------|-------|
| `MiranteAuthenticationProvider.java:62-134` | provider Spring Security | fluxo de autenticação; ponto de injeção da checagem de grupo |
| `MiranteAuthenticationProvider.java:101-128` | ramo usuário interno | onde inserir `isMembroDoGrupo` antes de `setUserInSession` |
| `MiranteAuthenticationProvider.java:178,185` | bypass de senha | dev/homologação — manter restrição desligada |
| `MiranteUserDetailsService.java:46-49,53,81-85` | UserDetailsService | concede `ROLE_USER` a todo autenticado (acesso = ter login) |
| `LDAPAuthentificator.java:50-65` | `autentificar/authenticate` | bind LDAP (sem filtro de grupo hoje) |
| `LDAPAuthentificator.java:168-187` | `findAccountByAccountName` | padrão de `search` reaproveitável para `memberOf` |
| `LDAPAuthentificator.java:200-235` | `buscarLoginUsuariosAD` | bind como admin + search em `defaultLdapFolder` |
| `LDAPAuthentificator.java:237-239` | `isADConfigurado` | guarda por `connectionURL` |
| `PropriedadesSistema.java:106-132` | getters LDAP | padrão para novas propriedades do grupo |
| `config.properties:56-62` | config LDAP | `ativo=false`, URL/conta/folder/domain |
| `config.properties:220-223` | config Hub AD | `integracao.ad.*` habilitado |
| `IntegracaoUsuarioADService.java:67-142` | Hub de TI | criar/desativar usuário; sem consulta de grupo (a confirmar) |
| `applicationContext.xml:173-188` | Spring beans | wiring do provider e `authentication-manager` |
| `applicationContext.xml:265-309` | `<security:http>` | `intercept-url /**` = `hasRole('ROLE_USER')`; form-login `/login` |
| `CadastroUsuarioBean.java:93-108` | `identificarPessoaLogin` | resolve `Pessoa` por e-mail no login |
| `CadastroUsuarioBean.java:159-168` | `isUsuarioAD` | decide se o usuário é do AD (por domínio de e-mail) |
