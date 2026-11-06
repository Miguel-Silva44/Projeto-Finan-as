# Android: implementação e geração do APK

O projeto `android/` contém um cliente TWA em Java, usando Android Browser Helper 2.6.2, AndroidX Browser estável 1.9.0, AGP 8.13.2 e Gradle Wrapper 8.13 com checksum oficial. Android mínimo 8/API 26; compilação e target API 36. O backend continua Java MVC/PostgreSQL. Não é necessário Node/Bubblewrap para executar este projeto: o módulo foi escrito diretamente com a biblioteca que implementa a TWA, permitindo versionar toda a configuração.

## Estado atual

A webapp possui manifest, ícones PNG, service worker e página offline. O cache aceita somente ícones e a página pública offline; telas, tokens e dados financeiros usam a rede. A página de exclusão `/account-deletion` encaminha para o fluxo autenticado existente. Privacidade e suporte são páginas de preparação e precisam dos dados do operador antes de uma distribuição pública.

Sem domínio configurado, um **APK debug abre somente uma tela nativa de preparação**. Ele não acessa o servidor local nem oferece operações financeiras. A versão release recusa origem vazia, identidade não confirmada ou assinatura ausente. Nenhum domínio foi fornecido; a TWA integrada não está homologada.

## APK de teste gerado em 06/11/2026

SDK instalado após o aceite explícito do usuário. APK debug compilado e assinatura v2 verificada: [app-debug.apk](../artifacts/android/hml/debug/app-debug.apk), com [checksum SHA-256](../artifacts/android/hml/debug/app-debug.apk.sha256) e [metadados](../artifacts/android/hml/debug/app-debug.apk.json). Versão `0.1.0-debug`, applicationId `br.com.financa.controle.hml`, Android mínimo API 26 e target API 36.

O lint terminou com **0 erros e 2 avisos**: versão mais recente disponível do Gradle e recomendação de regras explícitas de extração de dados Android 12+ (`allowBackup=false` já está configurado). Não houve erro de compilação ou assinatura. Relatório em `android/app/build/reports/lint-results-debug.html`. A proteção de release sem origem/identidade foi testada e rejeitou o build conforme esperado.

Não havia aparelho conectado no adb e não há emulador instalado. O APK foi inspecionado com aapt2 e apksigner, mas ainda não foi aberto em dispositivo. Sem domínio, este artefato mostra somente a preparação. Para instalar por USB, use o comando abaixo; alternativamente, copie o APK para seu telefone e abra-o para instalação pela fonte de confiança escolhida.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/install-android.ps1 -ApkPath artifacts/android/hml/debug/app-debug.apk
```

## Instalar ferramentas no Windows

Na raiz do projeto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/check-android-environment.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/setup-android-environment.ps1 -Install
```

O instalador baixa o pacote oficial de ferramentas Android e confere SHA-256. Leia e aceite os termos quando solicitado, inclusive licenças apresentadas pelo gerenciador do SDK. Não existe aceite automático. A instalação fica em `data/android-sdk`, fora do Git. Este pacote oficial ainda contém `sdkmanager`, usado aqui com versão fixada; não é necessário Android Studio nem Gradle global. Requer JDK 21 disponível, sem alterar o Java global.

O diagnóstico registra SDK e dispositivos em `data/android-environment-check.json`. `unauthorized` no adb exige liberar depuração USB no aparelho. Instalar SDK não cria automaticamente emulador; pode-se usar aparelho físico. Um ambiente já instalado pode ser selecionado com `ANDROID_HOME` nos scripts de build.

## Gerar e instalar debug sem domínio

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Environment hml -BuildType Debug -Artifact Apk
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/install-android.ps1 -ApkPath artifacts/android/hml/debug/app-debug.apk
```

O script produz APK, checksum e metadados em `artifacts/android/hml/debug/`, ignorados pelo Git. Verifica a assinatura com `apksigner`. Se houver mais de um dispositivo, informe `-Serial` ao instalar. O debug sem domínio serve para verificar instalação e a tela de preparação; não satisfaz o aceite funcional do aplicativo financeiro.

## Conectar à homologação

1. Disponibilize o backend por uma origem HTTPS estável, com certificado válido. `localhost` no telefone aponta para o próprio telefone; o container atual publica a aplicação apenas no loopback do PC.
2. Copie `android/config/hml.example.properties` para `android/config/hml.local.properties`. Configure `origin=https://DOMINIO-REAL`, sem caminho, e mantenha um applicationId próprio terminado em `.hml`.
3. Gere o debug e obtenha o SHA-256 público exibido por `apksigner` no build.
4. No `.env` do ambiente servidor, configure `ANDROID_PACKAGE` e `ANDROID_FINGERPRINTS` (fingerprints separados por vírgula). Fora do Compose, use `APP_ANDROID_PACKAGE` e `APP_ANDROID_FINGERPRINTS`. Reinicie a aplicação com essas variáveis.
5. Confirme `https://DOMINIO-REAL/.well-known/assetlinks.json`: deve ser JSON público, sem redirecionamento. Sem configuração, o endpoint retorna `[]`, sem inventar certificados.
6. Execute `verify-android-domain.ps1 -Environment hml -Fingerprint 'SHA256:COM:32:BYTES'` usando o fingerprint real completo. Instale o APK novamente e valide a TWA no aparelho.

Falha de associação pode abrir Custom Tab com barra do navegador. Isso precisa ser corrigido antes do aceite. O fallback do Browser Helper usa navegador; não foi adicionada WebView com acesso a cookies. Falta de provedor compatível e downloads autenticados devem ser homologados em aparelhos reais.

## Release e assinatura

Copie `android/config/prod.example.properties` para `prod.local.properties`; defina origem, nome e applicationId definitivo, versão e `identityConfirmed=true`. Não troque o ID depois da distribuição. O perfil backend deve ser `prod,postgres`, com HTTPS, SMTP e nenhuma conta dev habilitada.

Crie uma chave de assinatura conscientemente, por exemplo `keytool -genkeypair -keystore CAMINHO_FORA_DO_REPOSITORIO -alias financas -keyalg RSA -keysize 3072 -validity 10000`, respondendo às solicitações interativas. Guarde chave e credenciais separadamente, com cópia de recuperação. O build não cria nem substitui chaves.

Copie `android/signing.example.properties` para `android/signing.properties` e configure caminho e alias. Forneça `ANDROID_STORE_PASSWORD` e `ANDROID_KEY_PASSWORD` somente no ambiente do processo, por mecanismo privado; não escreva senhas em comandos compartilhados ou arquivos versionados.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Environment prod -BuildType Release -Artifact Apk
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1 -Environment prod -BuildType Release -Artifact Aab
```

Mantenha assinatura compatível e aumente `versionCode` para atualizar instalações. `install-android.ps1` nunca desinstala para contornar assinatura incompatível. Ao usar Play App Signing, o fingerprint distribuído pela loja pode ser diferente do certificado de upload; ajuste Asset Links ao canal real.

## Validação e limites

`mvn.cmd verify spotless:check` executa testes financeiros e os novos testes de recursos PWA, associação, proteção de rotas e CSRF. `scripts/pwa-check.cjs` verifica no Chromium o login dev em uma instância de teste, Cache Storage sem dados financeiros, indisponibilidade, retorno online e logout. Não execute esse script contra produção.

O workflow Android gera debug e executa lint sem chaves release. Não publica na loja nem gera releases automaticamente. As dependências transitivas foram resolvidas em `android/app/gradle.lockfile`. Em atualização explícita de bibliotecas, execute `android/gradlew.bat -p android :app:dependencies --write-locks` e revise o diff; builds normais usam o lockfile existente. A matriz ainda precisa passar pela compilação com SDK e lint. A geração por terminal segue o [Gradle Wrapper oficial](https://developer.android.com/build/building-cmdline); a arquitetura segue a [documentação TWA](https://developer.android.com/develop/ui/views/layout/webapps/trusted-web-activities).

Antes de distribuir: executar AN01–AN26 da spec em API 26 e versão atual, incluindo aparelho real, associação, CSV/ZIP, perda de rede em POST, atualização, segundo plano, acessibilidade e expiração. A preparação sem domínio não comprova esses critérios. Publicação Play exige ainda política final, responsável/contato, exclusão, Data Safety, conta e revisão da loja.
