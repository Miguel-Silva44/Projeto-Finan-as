# Spec — PostgreSQL em Docker com persistência e migração do H2

> **Versão:** 1.2 · **Atualização:** 06/11/2026 · **Criação:** 29/10/2026.
> **Status:** implementação local executada e validada; homologação de produção e infraestrutura externa pendentes. Consulte a seção 14 para evidências e limites.
> **Origem:** pedido do usuário para adotar PostgreSQL e Docker com persistência.
> **Dependência:** [spec principal](spec-app-controle-financeiro.md).
> **Complexidade:** média para banco novo; alta quando inclui migração dos dados existentes e recuperação operacional.

## 1. Objetivo e escopo

Executar a aplicação Java MVC com PostgreSQL em container, preservando dados após reiniciar ou recriar containers e mantendo a separação entre desenvolvimento e produção. Entregar também migrações, testes reais de PostgreSQL, backup e restauração verificáveis.

O PostgreSQL armazena os registros; o volume Docker armazena os arquivos do banco fora do ciclo de vida do container. A cópia de recuperação fica em armazenamento independente. Volumes persistem fora do container, mas não oferecem por si só recuperação de perda do computador ou do disco. [Volumes Docker](https://docs.docker.com/engine/storage/volumes/).

Esta entrega não inclui hospedagem contratada, cluster, alta disponibilidade, Kubernetes, replicação nem funcionamento offline. A etapa Android está em [spec de APK](spec-android-apk.md).

## 2. Diagnóstico inicial do repositório (antes da implementação)

| Área | Existe hoje | Trabalho restante |
|---|---|---|
| Dependências | Driver JDBC PostgreSQL, Spring JDBC, Flyway e módulo Flyway PostgreSQL em [pom.xml](../pom.xml) | Fixar matriz testada de versões e validar o banco real |
| Banco padrão | H2 em arquivo em [application.properties](../src/main/resources/application.properties) | Perfil PostgreSQL sem fallback silencioso para H2 |
| Desenvolvimento | [application-dev.properties](../src/main/resources/application-dev.properties) usa `DEV_DATABASE_URL` e banco H2 separado | Configurar o dev PostgreSQL sem confundir variáveis de produção |
| Schema | Migrações V1 a V4; V4 marca a conta dev | Aplicar desde banco vazio em PostgreSQL e validar constraints/índices |
| Consultas | SQL parametrizado, `FOR UPDATE`, agregações, `LIMIT`, datas e transações | Verificar tipos retornados, concorrência, locks e desempenho no PostgreSQL |
| Backup | [BackupService](../src/main/java/br/com/financa/service/BackupService.java) aceita somente H2 e usa `SCRIPT TO` | Fluxo PostgreSQL com `pg_dump`/`pg_restore`, criptografia e agendamento |
| Testes | Integração em H2; carga usa `RANDOM_UUID()` e `SYSTEM_RANGE` | Suite PostgreSQL com Testcontainers; fixtures portáveis |
| Android/web | Backend acessível por HTTP local; sessões em memória | HTTPS e endereço estável para uso externo |
| Docker | Nenhum Dockerfile, Compose ou `.dockerignore` no projeto | Criar build, serviços, volumes, healthchecks e documentação |

Na inspeção, `docker` não foi localizado no PATH. Não foi comprovada a existência de um daemon Docker ativo. Instalação e capacidade de executar containers devem ser verificadas antes da implementação; esta spec não instala ferramentas nem altera o banco em execução.

**Atualização do usuário em 06/11/2026:** Docker e PostgreSQL ainda não estão instalados. A entrega deve começar pela preparação do PC via terminal. A inspeção desta sessão encontrou `winget.exe` e `wsl.exe` no PATH e não encontrou `docker`; isso não comprova WSL 2 funcional. Consultas CIM de hardware/Windows foram negadas neste ambiente, portanto versão, memória e virtualização ainda precisam ser verificadas no terminal local apropriado.

**Ponto importante:** definir apenas `DATABASE_URL` hoje não muda a URL do perfil `dev`, porque esse perfil tem configuração própria baseada em `DEV_DATABASE_URL`.

### 2.1 Preparação obrigatória do PC Windows

Entregar um roteiro PowerShell que começa com diagnóstico e instala somente os componentes ausentes. O PostgreSQL será baixado como imagem Docker e executado no container; não exigir instalador Windows do PostgreSQL, pgAdmin ou instalação separada de `psql`. As ferramentas `psql`, `pg_dump` e `pg_restore` serão usadas dentro dos containers.

Os comandos desta seção são instruções para a futura preparação do ambiente; não foram executadas instalações nesta atualização. Separar diagnóstico de instalação e indicar quando há necessidade de administrador, interação, reinicialização ou reabertura do terminal. Não alterar BIOS, reiniciar o PC, apagar volumes ou aceitar licenças automaticamente.

### 2.2 Verificações antes de baixar

Executar no PowerShell do PC:

```powershell
Get-Command winget,wsl,docker,java,mvn.cmd -ErrorAction SilentlyContinue |
    Select-Object Name,Source

Get-CimInstance Win32_OperatingSystem |
    Select-Object Caption,Version,BuildNumber,OSArchitecture

Get-CimInstance Win32_ComputerSystem |
    Select-Object HypervisorPresent,@{Name='RAM_GB';Expression={[math]::Round($_.TotalPhysicalMemory / 1GB,1)}}

Get-CimInstance Win32_Processor |
    Select-Object -First 1 VirtualizationFirmwareEnabled,SecondLevelAddressTranslationExtensions

Get-PSDrive -PSProvider FileSystem
wsl --status
wsl --version
wsl --list --verbose
winget --version
java -version
mvn.cmd -version
```

Executar verificações de componentes apenas se o comando correspondente existir. Se CIM retornar acesso negado, repetir o diagnóstico no PowerShell local com permissão adequada ou obter a informação pelo Windows; marcar como **não verificado**, sem tratar a falha como incompatibilidade ou virtualização desativada. Interpretar os indicadores de CPU junto com `HypervisorPresent`, porque um hipervisor já ativo pode alterar o que é reportado.

Conferir a matriz Windows/arquitetura/WSL e os requisitos de hardware da versão Docker selecionada na [documentação oficial de instalação](https://docs.docker.com/desktop/setup/install/windows-install/). Verificar espaço para imagens, builds e crescimento do volume do banco. Registrar os resultados sem informações pessoais ou segredos. Se a virtualização de firmware estiver indisponível, orientar o ajuste na BIOS/UEFI e pausar a instalação dependente dela.

### 2.3 Instalar ou atualizar WSL 2

Se WSL não estiver funcional, abrir PowerShell **como administrador** e executar, quando aplicável:

```powershell
wsl --install --no-distribution
```

Não é necessário instalar Ubuntu só para executar o backend WSL 2 do Docker Desktop. Se o Windows solicitar reinicialização, salvar o trabalho, reiniciar manualmente e retomar este roteiro. Em seguida:

```powershell
wsl --update
wsl --set-default-version 2
wsl --status
wsl --version
```

Se já houver WSL adequado, reutilizá-lo. A ausência de distribuições pessoais em `wsl --list --verbose` não prova que o backend Docker seja inviável. Não converter nem remover distribuições existentes por suposição. [Instalação WSL](https://learn.microsoft.com/en-us/windows/wsl/install), [opções de instalação](https://learn.microsoft.com/en-us/windows/wsl/basic-commands).

### 2.4 Baixar e instalar Docker Desktop pelo terminal

Com `winget` disponível, inspecionar o pacote e executar a instalação se ausente:

```powershell
winget list --exact --id Docker.DockerDesktop
winget show --exact --id Docker.DockerDesktop --source winget
winget install --exact --id Docker.DockerDesktop --source winget --interactive
```

O pacote deve corresponder ao Docker Desktop, e não apenas ao cliente Docker CLI. Resolver solicitações do instalador e permissões do Windows; confirmar backend WSL 2 e containers Linux. Se já estiver instalado, validar antes de propor atualização. Não reinstalar automaticamente um ambiente existente. [Comando WinGet](https://learn.microsoft.com/en-us/windows/package-manager/winget/install), [identificador no catálogo Microsoft](https://github.com/microsoft/winget-pkgs/tree/master/manifests/d/Docker/DockerDesktop).

Se `winget` estiver ausente ou indisponível, oferecer download direto pelo PowerShell. **Exemplo para Windows x64**; em ARM64 selecionar o instalador correspondente indicado pelo fornecedor:

```powershell
$dockerInstaller = Join-Path $env:TEMP 'DockerDesktopInstaller.exe'
Invoke-WebRequest -Uri 'https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe' -OutFile $dockerInstaller
$dockerSignature = Get-AuthenticodeSignature -LiteralPath $dockerInstaller
if ($dockerSignature.Status -ne 'Valid' -or $dockerSignature.SignerCertificate.Subject -notmatch 'Docker') {
    throw 'A assinatura do instalador Docker não foi validada. Interrompa a instalação.'
}
Start-Process -FilePath $dockerInstaller -Wait -ArgumentList 'install','--user','--backend=wsl-2'
```

Validar novamente URL, arquitetura e parâmetros contra a documentação oficial antes de automatizar esse fallback. O instalador pode solicitar interação e permissões; não prometer instalação totalmente silenciosa. Fechar e reabrir PowerShell e terminal do IntelliJ para atualizar o PATH depois de instalar.

### 2.5 Iniciar e validar Docker

No novo terminal:

```powershell
docker --version
docker compose version
docker desktop start --timeout 120
docker version
docker info --format '{{.OSType}}'
docker run --rm hello-world
```

Se a versão não oferecer `docker desktop start`, iniciar Docker Desktop pelo aplicativo instalado e repetir as verificações. O primeiro uso pode exigir configurações/interação na interface. O resultado precisa incluir acesso ao **servidor/engine** e tipo `linux`; ter somente a CLI não atende ao requisito. Não instalar Compose separadamente quando o Docker Desktop já fornecer `docker compose`. [Inicialização pela CLI](https://docs.docker.com/reference/cli/docker/desktop/start/).

`hello-world` é um teste descartável sem banco. Falhas de rede, proxy, permissão, engine ou WSL devem ser diagnosticadas antes de prosseguir; registrar mensagens sem credenciais.

### 2.6 Baixar PostgreSQL e validar o serviço

Depois de fixar a versão/digest na implementação, baixar as imagens pelo terminal. O comando abaixo usa o major proposto como exemplo de preparação:

```powershell
docker pull postgres:18
docker image inspect postgres:18 --format '{{json .RepoDigests}}'
```

Registrar o digest e usar a referência fixada no Compose entregue. A imagem baixada não significa banco inicializado. Quando os arquivos Compose da seção 5.3 existirem:

```powershell
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml pull db
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml up -d db
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml ps
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml exec db pg_isready -U financas_admin -d financas
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml exec db psql -U financas_admin -d financas -c 'SELECT version(), current_database();'
```

`financas_admin` e `financas` são nomes propostos para bootstrap/verificação; ajustar ao contrato final de credenciais. A aplicação continua usando usuário runtime restrito. Validar também autenticação JDBC com esse usuário e a persistência do volume após recriar o serviço. Não sugerir `up` antes de criar os arquivos referenciados, nem iniciar um PostgreSQL temporário sem volume como se fosse a configuração final.

### 2.7 Scripts a entregar

- `scripts/check-environment.ps1`: diagnóstico sem instalações; relatório de componente presente/ausente/não verificado, versões e próximos passos.
- `scripts/setup-dev-environment.ps1`: instalação orientada dos componentes faltantes, retomável após reboot, checagem de códigos de saída e URLs/assinaturas oficiais. Informar previamente mudanças que exigem administrador; sem reboot automático, remoção de dados ou mudança global da política de execução.
- O roteiro deve permitir diagnóstico e download antes de o Compose existir, indicando claramente a etapa posterior que depende da implementação.
- Incluir instruções no README e na operação. As futuras tarefas Android reaproveitam essa preparação para o servidor; o telefone não recebe Docker ou PostgreSQL.

## 3. Arquitetura alvo

```mermaid
flowchart LR
  browser[Navegador ou Android] --> proxy[HTTPS / proxy reverso]
  proxy --> app[Spring MVC · uma instância]
  app --> db[PostgreSQL · rede interna]
  db --> disk[(Volume nomeado)]
  backup[Job de backup] --> db
  backup --> archive[(Backup criptografado externo)]
```

Três modos documentados:

1. **Desenvolvimento:** banco no Docker; Java pode rodar na IDE/host ou em container; perfil `dev` explícito.
2. **Homologação:** app e banco em containers, sem credenciais dev; serviços de e-mail de teste.
3. **Produção:** app e banco em containers, proxy HTTPS, SMTP real e backups externos; uma instância da aplicação inicialmente.

O Docker Desktop/Engine precisa permanecer em execução. Containers locais não tornam o serviço público nem mantêm o app disponível quando o computador estiver desligado.

## 4. Requisitos de infraestrutura

| ID | Requisito |
|---|---|
| PG01 | Subir PostgreSQL e aplicação por Docker Compose com configuração reproduzível |
| PG02 | Usar volume nomeado explícito para o banco e identificá-lo na documentação |
| PG03 | Preservar contas, lançamentos, cartões, auditoria e idempotência após recriação dos containers |
| PG04 | Separar bancos, volumes, credenciais e nomes de projeto Compose por ambiente |
| PG05 | Executar Flyway antes de aceitar tráfego financeiro; falhar se schema ou conexão estiverem inválidos |
| PG06 | Não criar usuário dev nem permitir seu login em homologação/produção |
| PG07 | Executar testes críticos no mesmo major de PostgreSQL da implantação |
| PG08 | Gerar backup completo diário criptografado e comprovar restauração independente |
| PG09 | Oferecer migração H2 → PostgreSQL opcional, explícita e reconciliada |
| PG10 | Documentar inicialização, parada, atualização, recuperação e limites operacionais |
| PG11 | Verificar o PC e instalar componentes ausentes pelo terminal, com roteiro retomável e validação do engine |
| PG12 | Baixar PostgreSQL como imagem e executar suas ferramentas no container, sem exigir instalação nativa do banco |

## 5. Docker e persistência

### 5.1 Imagens e diretórios

Proposta: PostgreSQL **18**, com patch e digest fixados na implementação; evitar `latest`. Para a aplicação, build multiestágio Maven/JDK 21 e runtime Java 21, também com versões registradas. Executar a aplicação com usuário sem privilégios e copiar apenas o artefato necessário.

Na imagem oficial PostgreSQL 18, montar o volume em **`/var/lib/postgresql`**; o diretório de dados padrão fica sob `18/docker`. Não copiar cegamente a montagem de versões anteriores. Confirmar esses caminhos na tag efetivamente fixada. [Imagem oficial PostgreSQL](https://hub.docker.com/_/postgres).

Volumes propostos, com escopo pelo nome do projeto Compose:

- `postgres_data`: arquivos ativos do PostgreSQL.
- `backup_staging`: artefatos temporários restritos; cópia final vai para armazenamento independente.
- `dev_mail`: e-mails em arquivo apenas no desenvolvimento, se a aplicação também estiver em container.

Manter o nome de projeto estável: `financas-dev`, `financas-hml` e `financas-prod`. Trocar o nome pode selecionar outro volume e aparentar perda dos dados. Registrar ambiente, volume e localização do backup no inventário operacional.

### 5.2 Rede e disponibilidade

- A aplicação em container conecta a `db:5432`. Na IDE/host conecta à porta publicada, por exemplo `localhost:5432`.
- Em desenvolvimento, publicar banco somente em `127.0.0.1` quando necessário à IDE.
- Em produção, não publicar a porta do banco na internet; expor apenas o proxy HTTPS.
- Definir healthcheck `pg_isready` e dependência `service_healthy`. `depends_on` sem healthcheck não garante prontidão do banco. [Inicialização no Compose](https://docs.docker.com/compose/how-tos/startup-order/).
- Prontidão da aplicação deve refletir conexão e schema aplicados, sem divulgar credenciais. Definir endpoint restrito para o healthcheck; hoje não há Actuator no `pom.xml`.
- Configurar timeout e política de reinício; testar recuperação de conexão após reiniciar o banco. Healthcheck de inicialização não substitui recuperação durante a execução.

### 5.3 Ciclo de vida

Os comandos abaixo são **contratos da futura entrega**, dependentes dos arquivos Compose ainda não criados:

```powershell
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml up -d --build
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml ps
docker compose -p financas-dev --env-file .env.dev -f compose.yaml -f compose.dev.yaml down
```

Recriar containers ou executar `down` sem remoção de volumes deve preservar os dados. Não incluir `down -v`, `volume prune` ou exclusão de volumes em procedimentos normais. Uma eventual limpeza destrutiva precisa identificar o volume e ter autorização específica.

## 6. Configuração, credenciais e perfis

Criar `application-postgres.properties` e `application-prod.properties`. O perfil PostgreSQL exige URL e credenciais válidas; na ausência delas, a aplicação deve encerrar em vez de criar um H2 vazio.

No Compose, usar as propriedades Spring de maior precedência `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`, ou equivalente por arquivo secreto, evitando dependência da ordem de carregamento dos perfis. Documentar a relação com as variáveis `DATABASE_*` e `DEV_DATABASE_*` existentes. Não manter duas URLs efetivas sem informar qual prevalece.

| Configuração | Dev | Produção |
|---|---|---|
| Perfis Spring | `dev,postgres` | `prod,postgres` |
| Credencial dev do app | Permitida no banco isolado | Bloqueada e sem inicializador |
| `BASE_URL` | Endereço acessível ao dispositivo em teste | Origem HTTPS pública e estável |
| Cookies seguros | Conforme HTTPS do teste | `Secure`, `HttpOnly` e política SameSite validada |
| E-mail | Arquivo persistente ou servidor de teste | SMTP com entrega validada |
| Templates | Cache desativado para desenvolvimento | Cache ativado |
| Tarefas | Uma instância | Uma instância; coordenação necessária para escalar |

A conta inicial criada por `POSTGRES_USER` na imagem oficial tem poder administrativo; ela não deve ser a credencial do app. Variáveis e scripts de inicialização da imagem atuam apenas sobre diretório vazio; trocar a senha no `.env` não altera automaticamente um banco já inicializado. [Inicialização da imagem oficial](https://hub.docker.com/_/postgres).

Separar credenciais administrativas, migração e runtime. Flyway usa a credencial de migração; runtime recebe DML e permissões necessárias às tabelas, sem superusuário ou criação arbitrária de schema. Definir grants para objetos futuros. Scripts de bootstrap só criam banco/papéis; tabelas e evolução pertencem ao Flyway.

Entregar `.env.example` sem valores reais. Arquivos `.env` são configuração do Compose; não são carregados automaticamente pelo Java fora dele. Em produção, montar segredos e usar mecanismo compatível do Spring, como config tree. Nenhuma senha entra em Dockerfile, imagem, APK, Git ou saída de diagnóstico.

## 7. Homologação SQL e Flyway

1. Aplicar V1–V4 em PostgreSQL vazio e executar `validate`.
2. Conferir `BIGINT`, booleanos, `DATE`, `TIMESTAMP WITH TIME ZONE`, nomes e tipos das colunas retornados pelo JDBC.
3. Testar constraints compostas por titular, unicidades, cascatas, limites monetários e índices.
4. Validar agregações e `CAST(CONCAT(...))` dos relatórios, ordenação de nulos, filtros inclusivos e datas no fuso do usuário.
5. Validar `FOR UPDATE`, isolamento de leitura, duas operações concorrentes e rollback da auditoria.
6. Tratar deadlocks/conflitos transitórios com resposta ou repetição limitada e idempotente; não repetir cegamente comandos financeiros.

Não alterar migrações já aplicadas em H2 para tentar corrigir um PostgreSQL novo. Se surgir incompatibilidade nas migrações iniciais, registrar a decisão: preservar o histórico H2 existente e fornecer cadeia inicial específica por fornecedor/localização Flyway. Nova migração só resolve o problema se a cadeia anterior conseguir executá-la. Nunca habilitar `baseline-on-migrate` apenas para esconder divergência.

## 8. Dados H2 existentes

Há dois caminhos, ambos explícitos:

- **Banco novo:** iniciar PostgreSQL vazio e manter H2 preservado. Nenhuma informação é migrada implicitamente.
- **Migração com preservação:** executar utilitário H2 → PostgreSQL, com escrita suspensa, origem preservada e destino inicialmente vazio.

O banco H2 normal e o banco H2 dev são origens diferentes. Não transportar a conta dev para produção. A exportação CSV/ZIP do usuário é útil para consulta, mas não é backup completo de autenticação: não contém hashes de senha nem todos os metadados necessários.

### 8.1 Procedimento de migração proposto

1. Inventariar origem, usuários, operações e schema; fazer backup verificado; impedir novas escritas.
2. Preparar destino por Flyway e validar que não contém dados de negócio.
3. Ler por JDBC e gravar em lotes na ordem das dependências, preservando IDs, hashes de senha, versões, centavos, datas, titularidade, auditoria e idempotência.
4. Rejeitar registros dev em destino de produção, inconsistências e duplicidades; não truncar ou arredondar valores silenciosamente.
5. Recriar o histórico Flyway a partir das migrações do destino, sem copiar a tabela de controle H2. Invalidar tokens de verificação/recuperação e sessões, comunicando a necessidade de novo login ou reenvio de link.
6. Reconciliar contagens e conteúdo por entidade; conferir saldo por conta, compras/parcelas, total de cada fatura e resultado por mês. Contagem igual sozinha não basta.
7. Emitir relatório sem segredos e liberar tráfego apenas após aprovação da reconciliação.

Falha deve deixar o destino identificado como não liberado. A repetição só é aceita sobre destino vazio ou após detectar, pelo manifesto da migração, uma execução já concluída e idêntica; não fazer importação cumulativa. Não automatizar limpeza de dados sem autorização. Após iniciar escritas no PostgreSQL, voltar ao H2 requer reconciliar essas escritas; não é rollback instantâneo.

## 9. Backup e recuperação PostgreSQL

Substituir a execução H2 agendada por estratégia dependente do banco ou job operacional PostgreSQL. Não deixar o agendamento atual falhar diariamente tentando executar `SCRIPT TO` em PostgreSQL.

Proposta: `pg_dump` em formato custom (`-Fc`), cliente compatível com o major do servidor, arquivo local restrito, criptografia autenticada e envio para destino separado. Restaurar com `pg_restore` em banco independente e reconstruir os papéis/grants operacionais. Um dump oferece snapshot consistente; formato custom permite restauração seletiva. [Backup lógico PostgreSQL](https://www.postgresql.org/docs/current/backup-dump.html).

No PowerShell, evitar redirecionar o dump binário por pipeline textual. Gerar arquivo com `--file` dentro do container/job e transferir por mecanismo que preserve bytes. Backups grandes devem ser processados por streaming, evitando carregar tudo em memória como ocorre no backup H2 atual.

Metas herdadas: RPO de até 24h, RTO de até 8h e retenção máxima proposta de 90 dias, a formalizar. Agendar fora do ciclo do container da aplicação, registrar último sucesso e tamanho, detectar falhas e testar restauração periodicamente. Volume e backup no mesmo disco não atendem à recuperação de perda desse disco.

## 10. Testes e critérios de aceitação

Usar PostgreSQL real em Testcontainers no CI e na integração local, com imagem alinhada à implantação. Docker indisponível deve falhar na etapa PostgreSQL obrigatória do CI, e não virar teste silenciosamente ignorado. Testes puros de domínio podem continuar sem containers. [Módulo PostgreSQL do Testcontainers](https://java.testcontainers.org/modules/databases/postgres/).

| ID | Verificação | Aceite |
|---|---|---|
| CP01 | Subir ambiente limpo | Banco pronto, Flyway aplicado, app responde sem dados fictícios em produção |
| CP02 | Criar dados e recriar containers | Mesmos IDs, saldos, faturas e auditoria; login funciona |
| CP03 | `down` e novo `up` com mesmo projeto/volume | Dados preservados |
| CP04 | Reiniciar o computador/daemon | Após iniciar Docker e serviços, dados intactos |
| CP05 | Executar dev e produção isolados | Volumes e usuários distintos; conta dev bloqueada em produção |
| CP06 | Ausência de configuração PostgreSQL | Inicialização falha, sem fallback para H2 |
| CP07 | Rodar testes CA01–CA22 da spec no PostgreSQL | Resultados financeiros, autorização e concorrência preservados |
| CP08 | Duas requisições de pagamento/edição | Um efeito financeiro; repetição idempotente ou conflito controlado |
| CP09 | Backup e restore em volume novo | Usuários, vínculos, auditoria e saldos reconciliados |
| CP10 | Migração de cópia H2 representativa | Conteúdo e totais reconciliados; tokens antigos inválidos |
| CP11 | Falha durante migração | Destino não liberado; origem preservada; reexecução controlada |
| CP12 | 100 mil lançamentos e 20 sessões HTTP | Medir P95 no ambiente PostgreSQL e perseguir a meta de 1s; não reutilizar o número medido em H2 |
| CP13 | Inspecionar imagem e portas | Sem segredos embutidos, runtime não privilegiado e banco sem exposição pública |
| CP14 | Exclusão e expiração de backup | Remoção do titular e retenção comprovadas no novo banco |
| CP15 | PC sem Docker/PostgreSQL | Roteiro identifica ausências, orienta WSL/instalação e conclui com engine Linux operacional |
| CP16 | PC com ambiente já preparado | Diagnóstico reutiliza componentes; não reinstala nem altera dados existentes |
| CP17 | Instalação exige reboot ou permissão | Motivo e retomada explícitos; nenhuma reinicialização automática |
| CP18 | PostgreSQL baixado e iniciado | Imagem identificada, healthcheck OK, consulta de versão e conexão JDBC runtime válidas |
| CP19 | Diagnóstico sem permissão/rede | Resultado não verificado ou falha acionável, sem falso sucesso ou avanço prematuro |

O teste de carga atual usa SQL exclusivo de H2; substituir por geração de fixtures Java/JDBC ou estratégia específica de PostgreSQL. O teste de restauração precisa usar ferramentas PostgreSQL e validar um banco completo.

## 11. Arquivos e entregas previstos

| Arquivo/área | Entrega |
|---|---|
| `Dockerfile`, `.dockerignore` | Build Java reproduzível, runtime mínimo e exclusão de `data`, segredos, IDE e artefatos locais |
| `compose.yaml`, `compose.dev.yaml`, `compose.prod.yaml` | Serviços, rede, volumes, healthchecks e exposição por ambiente |
| `.env.example` e documentação | Variáveis e precedência; sem senha real |
| `application-postgres.properties`, `application-prod.properties` | Configuração obrigatória e defaults adequados à produção |
| Configuração dev | Compatibilidade com banco dev PostgreSQL e login dev já existente |
| `pom.xml` e testes | Testcontainers, perfil de integração PostgreSQL e métricas reais |
| Repositórios/migrações | Ajustes constatados na homologação; histórico preservado |
| Backup | Job PostgreSQL, criptografia, restore e alertas; separar o modo H2 |
| Utilitário de migração | Importação por JDBC e relatório de reconciliação |
| `scripts/check-environment.ps1`, `scripts/setup-dev-environment.ps1` | Verificação do PC, download/instalação via terminal e retomada após reboot |
| `README.md`, `docs/operacao.md` | Procedimentos executáveis e recuperação testada |

## 12. Sequência e definição de pronto

1. Executar o diagnóstico do PC; preparar WSL 2, instalar Docker pelo terminal se ausente, validar engine/Compose e baixar a imagem PostgreSQL, conforme seção 2. Depois fixar imagens/versões.
2. Entregar PostgreSQL vazio com Flyway e perfis separados.
3. Containerizar app e validar persistência após recriação.
4. Executar testes PostgreSQL e corrigir diferenças de SQL/concorrência.
5. Entregar backup/restore, segredos e operação HTTPS.
6. Entregar e ensaiar migração H2, se os dados existentes forem preservados.
7. Homologar no host escolhido e atualizar a spec principal com evidências.

Pronto significa CP01–CP19 aprovados, ou CP10–CP11 explicitamente não aplicáveis por decisão de começar vazio; nenhum dado antigo apagado por suposição; backup restaurado em ambiente independente; produção sem conta dev. Ainda precisam ser definidos host/domínio, armazenamento de backup e quais bancos H2 existentes serão migrados. Essas definições não impedem implementar e testar o ambiente local.

## 13. Histórico

| Versão | Data | Alteração |
|---|---|---|
| 1.0 | 29/10/2026 | Desenho inicial PostgreSQL, Docker, persistência e migração |
| 1.1 | 06/11/2026 | Preparação do PC sem Docker/PostgreSQL: diagnóstico, WSL 2, instalação/download via terminal e critérios CP15–CP19 |
| 1.2 | 06/11/2026 | Implementação Docker/PostgreSQL, instalação pelo terminal, migração H2, backup/restore e testes reais; evidências na seção 14 |

## 14. Resultado da implementação local — 06/11/2026

- Docker Desktop 4.98.0 instalado pelo terminal; WSL 2 existente reutilizado. Engine Linux, Compose e PostgreSQL 18.6 operacionais. Diagnóstico em `data/environment-check.json`, com estado pronto para containers.
- App Java e PostgreSQL em containers saudáveis; imagens fixadas por digest em `config/images.env`. Perfis dev/hml/prod, volumes por projeto, segredos fora do Git e usuários distintos de administração/migração/execução. Migrações V1–V4 preservadas; V5–V7 específicas PostgreSQL.
- `mvn.cmd -Ppostgres-it verify spotless:check`: 27 testes comuns aprovados, um teste H2 de carga opcional ignorado e 19 testes PostgreSQL aprovados. Evidência: `target/postgres-verification.log`. Cobertura financeira, autorização, concorrência, rollback, importação H2, repetição controlada e permissões do usuário runtime.
- Recriação real de containers aprovada com marcador preservado no volume. Backup criptografado, checksum, restauração em banco separado e reconciliação de contagens aprovados: `target/postgres-backup-validation.txt`. O banco restaurado foi preservado para inspeção.
- CP12: teste PostgreSQL com 100 mil lançamentos e 20 sessões HTTP simultâneas aprovado, P95 de 893 ms (meta 1.000 ms). Evidência: `target/postgres-http-performance.txt`; cenário reproduzível `PostgresLoadIT`, ativado por `finance.postgres-performance=true`.
- Navegação Chromium em desktop e celular aprovada contra a aplicação Docker. Homologação em aparelhos reais permanece necessária.
- Não foi realizada importação dos arquivos H2 pessoais existentes: eles foram preservados. O importador foi ensaiado com fixture H2 e invalida tokens antigos, rejeita destino divergente e preserva a origem. A execução real exige interromper escritas e selecionar a origem explicitamente.
- CP04 (reinício do PC), execução completa de produção com domínio/HTTPS/SMTP, backup fora do disco e recuperação após perda do host permanecem pendentes de homologação operacional. Retenção foi implementada; o decurso real de 90 dias não foi observado. Não declarar todos os CP aprovados antes dessas etapas.

Instalação e operação reproduzíveis no [guia PostgreSQL/Docker](../docs/postgresql-docker.md). Android/APK permanece uma entrega separada.
