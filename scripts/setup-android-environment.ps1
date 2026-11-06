[CmdletBinding()]
param([switch]$Install)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
. "$PSScriptRoot/android-common.ps1"
if(-not $Install){& "$PSScriptRoot/check-android-environment.ps1"; return}
if(-not(Get-Command java -ErrorAction SilentlyContinue)){throw 'Instale JDK 21 antes de continuar; o Java do backend não será substituído.'}
$sdk=Get-AndroidSdk
if(-not(Test-Path -LiteralPath $sdk)){$sdk=Join-Path (Get-ProjectRoot) 'data/android-sdk'}
$manager=Join-Path $sdk 'cmdline-tools/15859902/bin/sdkmanager.bat'
if(-not(Test-Path -LiteralPath $manager)){
    $installed=Get-ChildItem -LiteralPath (Join-Path $sdk 'cmdline-tools') -Filter sdkmanager.bat -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if($installed){$manager=$installed.FullName}
}
if(-not(Test-Path -LiteralPath $manager)){
    Write-Output 'Leia os termos do Android SDK: https://developer.android.com/studio#terms-and-conditions'
    $accept=Read-Host 'Você leu e aceita os termos para baixar e usar o SDK? Digite ACEITO para continuar'
    if($accept -cne 'ACEITO'){throw 'Licença não aceita; instalação não executada.'}
    $zip=Join-Path (Get-ProjectRoot) 'data/commandlinetools-win-15859902.zip'
    $expectedHash='90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A'
    if(-not(Test-Path -LiteralPath $zip) -or (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $expectedHash){
        Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $zip
    }
    if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne '90AE805D20434428BFFCB699C290860F19BB5F66A67E6B330067E3DE801FB04A'){throw 'Checksum do SDK não corresponde à fonte oficial.'}
    $stage=Join-Path (Get-ProjectRoot) ('data/at-'+[guid]::NewGuid().ToString('N').Substring(0,8))
    [void][IO.Directory]::CreateDirectory($stage)
    # The SDK contains paths exceeding Windows PowerShell/.NET Framework MAX_PATH.
    # JDK's ZIP extractor supports these paths without changing global OS settings.
    Push-Location $stage
    try{Invoke-CheckedCommand jar @('--extract','--file',$zip)}finally{Pop-Location}
    [void][IO.Directory]::CreateDirectory((Join-Path $sdk 'cmdline-tools'))
    Move-Item -LiteralPath (Join-Path $stage 'cmdline-tools') -Destination (Join-Path $sdk 'cmdline-tools/15859902')
}
$env:ANDROID_HOME=$sdk
# sdkmanager remains included in the pinned official command-line package.
# License answers remain interactive; never pipe automatic acceptance.
# Installing only required packages prompts only their applicable licenses,
# rather than unrelated TV/XR/preview SDK agreements.
Invoke-CheckedCommand $manager @("--sdk_root=$sdk",'platform-tools','platforms;android-36','build-tools;36.0.0')
Write-Output 'SDK instalado no projeto. Os scripts resolvem esse caminho sem alterar variáveis globais.'
& "$PSScriptRoot/check-android-environment.ps1"
