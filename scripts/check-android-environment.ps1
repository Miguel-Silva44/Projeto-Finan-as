[CmdletBinding()]
param([string]$ReportPath='data/android-environment-check.json')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/android-common.ps1"
$sdk=Get-AndroidSdk
$adb=Join-Path $sdk 'platform-tools/adb.exe'
$report=[ordered]@{
    CheckedAt=[DateTime]::UtcNow.ToString('o')
    OperatingSystem=[Environment]::OSVersion.VersionString
    ProcessArchitecture=$env:PROCESSOR_ARCHITECTURE
    Java=(Invoke-DiagnosticCommand java @('-version'))
    Node=(Invoke-DiagnosticCommand node @('--version'))
    SdkPath=$sdk
    Platform36=(Test-Path -LiteralPath (Join-Path $sdk 'platforms/android-36/android.jar'))
    BuildTools36=(Test-Path -LiteralPath (Join-Path $sdk 'build-tools/36.0.0/apksigner.bat'))
    Adb=(Invoke-DiagnosticCommand $adb @('version'))
    Devices=(Invoke-DiagnosticCommand $adb @('devices','-l'))
    Wrapper=(Test-Path -LiteralPath (Join-Path (Get-ProjectRoot) 'android/gradle/wrapper/gradle-wrapper.jar'))
    Emulator=(Test-Path -LiteralPath (Join-Path $sdk 'emulator/emulator.exe'))
    SdkLicenses=(Test-Path -LiteralPath (Join-Path $sdk 'licenses/android-sdk-license'))
    Agp='8.13.2'; Gradle='8.13'; BrowserHelper='2.6.2'
}
$report.ReadyToBuild=($report.Java.State -eq 'ok' -and $report.Platform36 -and $report.BuildTools36 -and $report.Wrapper)
$drive=Get-PSDrive -Name ([IO.Path]::GetPathRoot((Get-ProjectRoot)).TrimEnd('\').TrimEnd(':')) -ErrorAction SilentlyContinue
$report.FreeDiskGb=if($drive -and $null -ne $drive.Free){[math]::Round($drive.Free/1GB,1)}else{$null}
$fullPath=[IO.Path]::GetFullPath((Join-Path (Get-ProjectRoot) $ReportPath))
[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($fullPath))
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $fullPath -Encoding UTF8
Write-Output ($report | ConvertTo-Json -Depth 5)
Write-Output 'ReadyToBuild não comprova build nem homologação. USB unauthorized exige confirmação no aparelho; emulador é opcional.'
