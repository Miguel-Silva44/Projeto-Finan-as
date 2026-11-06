[CmdletBinding()]
param([string]$ReportPath, [switch]$PassThru)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$report=[ordered]@{CheckedAt=[DateTime]::UtcNow.ToString('o');Platform='Windows';Architecture=$env:PROCESSOR_ARCHITECTURE;Checks=[ordered]@{}}
foreach($item in @(@('winget','--version'),@('wsl','--version'),@('docker','--version'),@('java','-version'))) {
    $report.Checks[$item[0]]=Invoke-DiagnosticCommand $item[0] @($item[1])
}
$report.Checks['wslStatus']=Invoke-DiagnosticCommand 'wsl' @('--status')
$report.Checks['compose']=Invoke-DiagnosticCommand 'docker' @('compose','version')
$report.Checks['engine']=Invoke-DiagnosticCommand 'docker' @('info','--format','{{.OSType}}')
$maven=Get-Command mvn.cmd -ErrorAction SilentlyContinue
$report.Checks['maven']=[pscustomobject]@{State=$(if($maven){'presente'}else{'ausente'});Output=$(if($maven){'mvn.cmd disponível'}else{'Instale Maven 3.9+'})}
try {
    $os=Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
    $system=Get-CimInstance Win32_ComputerSystem -ErrorAction Stop
    $cpu=Get-CimInstance Win32_Processor -ErrorAction Stop | Select-Object -First 1
    $report.Checks['hardware']=[pscustomobject]@{State='verificado';Windows=$os.Caption;Build=$os.BuildNumber;RAM_GB=[math]::Round($system.TotalPhysicalMemory/1GB,1);HypervisorPresent=$system.HypervisorPresent;VirtualizationFirmwareEnabled=$cpu.VirtualizationFirmwareEnabled;SLAT=$cpu.SecondLevelAddressTranslationExtensions}
} catch { $report.Checks['hardware']=[pscustomobject]@{State='não verificado';Output='Consulta de hardware não autorizada/disponível. Verifique no PowerShell local apropriado.'} }
$report.Checks['disk']=@(Get-PSDrive -PSProvider FileSystem | Select-Object Name,@{Name='FreeGB';Expression={[math]::Round($_.Free/1GB,1)}})
$report['ReadyForContainers']=($report.Checks.engine.State -eq 'ok' -and $report.Checks.engine.Output.Trim() -eq 'linux' -and $report.Checks.compose.State -eq 'ok')
if ($ReportPath) {
    $resolved=[IO.Path]::GetFullPath($ReportPath)
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($resolved))
    [IO.File]::WriteAllText($resolved,($report|ConvertTo-Json -Depth 8),(New-Object Text.UTF8Encoding($false)))
}
if($PassThru){return [pscustomobject]$report}
$report|ConvertTo-Json -Depth 8
