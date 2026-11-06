[CmdletBinding()]
param([ValidateSet('hml','prod')][string]$Environment='hml',
    [ValidateSet('Debug','Release')][string]$BuildType='Debug',
    [ValidateSet('Apk','Aab')][string]$Artifact='Apk')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/android-common.ps1"
$root=Get-ProjectRoot
$env:GRADLE_USER_HOME=Join-Path $root 'data/gradle'
$config=Get-AndroidConfiguration $Environment
if($config['origin']){Assert-AndroidOrigin $config['origin']}
if($BuildType -eq 'Release'){
    if(-not $config['origin'] -or $config['identityConfirmed'] -ne 'true'){throw 'Release exige origem HTTPS e identidade confirmada no arquivo local.'}
    if(-not $env:ANDROID_STORE_PASSWORD -or -not $env:ANDROID_KEY_PASSWORD){throw 'Defina senhas de assinatura nas variáveis do processo; não passe senhas em argumentos.'}
}
$env:ANDROID_HOME=Get-AndroidSdk
if(-not(Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms/android-36/android.jar'))){throw 'SDK API 36 ausente. Execute setup-android-environment.ps1 -Install e aceite as licenças interativamente.'}
$task=if($Artifact -eq 'Apk'){"assemble$BuildType"}else{"bundle$BuildType"}
Push-Location (Join-Path $root 'android')
try{Invoke-CheckedCommand '.\gradlew.bat' @($task,"lint$BuildType", "-PfinanceEnvironment=$Environment",'--no-daemon','--console=plain')}finally{Pop-Location}
$kind=$BuildType.ToLowerInvariant()
$relative=if($Artifact -eq 'Apk'){"app/build/outputs/apk/$kind/app-$kind.apk"}else{"app/build/outputs/bundle/$kind/app-$kind.aab"}
$source=Join-Path $root "android/$relative"
if(-not(Test-Path -LiteralPath $source)){throw 'Artefato esperado não foi produzido.'}
$destination=Join-Path $root "artifacts/android/$Environment/$kind"
[void][IO.Directory]::CreateDirectory($destination)
$file=Join-Path $destination ([IO.Path]::GetFileName($source))
Copy-Item -LiteralPath $source -Destination $file
$hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
[IO.File]::WriteAllText("$file.sha256","$hash  $([IO.Path]::GetFileName($file))`n")
$fingerprint=$null
if($Artifact -eq 'Apk'){
    $signatureOutput=& (Join-Path $env:ANDROID_HOME 'build-tools/36.0.0/apksigner.bat') verify --verbose --print-certs $file
    if($LASTEXITCODE -ne 0){throw 'Assinatura do APK inválida.'}
    Write-Output $signatureOutput
    $certificate=@($signatureOutput | Select-String 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]{64})')
    if($certificate.Count -ne 1){throw 'Fingerprint do certificado não encontrado.'}
    $fingerprint=([regex]::Matches($certificate[0].Matches[0].Groups[1].Value.ToUpperInvariant(),'..') | ForEach-Object {$_.Value}) -join ':'
}else{Invoke-CheckedCommand jarsigner @('-verify',$file)}
$commit=& git -C $root rev-parse HEAD
$dirty=[bool](& git -C $root status --porcelain)
$actualVersionName=$config['versionName']
if($BuildType -eq 'Debug'){$actualVersionName+='-debug'}
@{Environment=$Environment;BuildType=$BuildType;ApplicationId=$config['applicationId'];Origin=$config['origin'];
    VersionCode=$config['versionCode'];VersionName=$actualVersionName;Commit=$commit;Sha256=$hash;
    Configured=[bool]$config['origin'];CertificateSha256=$fingerprint;WorkingTreeDirty=$dirty;
    Agp='8.13.2';Gradle='8.13';BrowserHelper='2.6.2';BuiltAt=[DateTime]::UtcNow.ToString('o')} |
    ConvertTo-Json | Set-Content -LiteralPath "$file.json" -Encoding UTF8
Write-Output "Artefato validado: $file"
if(-not $config['origin']){Write-Output 'DEBUG SEM SERVIDOR: abre somente a tela de preparação; não equivale a aplicativo homologado.'}
