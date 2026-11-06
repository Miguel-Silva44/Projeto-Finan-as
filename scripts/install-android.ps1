[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$ApkPath,[string]$Serial)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/android-common.ps1"
$adb=Join-Path (Get-AndroidSdk) 'platform-tools/adb.exe'
if(-not(Test-Path -LiteralPath $ApkPath) -or [IO.Path]::GetExtension($ApkPath) -ne '.apk'){throw 'Informe um APK existente.'}
Invoke-CheckedCommand $adb @('devices','-l')
$deviceLines=& $adb devices
if($LASTEXITCODE -ne 0){throw 'Não foi possível consultar dispositivos.'}
$devices=@($deviceLines | Where-Object {$_ -match '^\S+\s+device$'} | ForEach-Object {($_ -split '\s+')[0]})
if(-not $Serial){if($devices.Count -ne 1){throw 'Selecione -Serial; confirme autorização USB no aparelho se necessário.'};$Serial=$devices[0]}
if($Serial -notin $devices){throw 'Dispositivo ausente ou não autorizado.'}
Invoke-CheckedCommand $adb @('-s',$Serial,'install','-r',([IO.Path]::GetFullPath($ApkPath)))
Write-Output 'Instalado. Em caso de assinatura incompatível, investigue; este script nunca desinstala automaticamente.'
