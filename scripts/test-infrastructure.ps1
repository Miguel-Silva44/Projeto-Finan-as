[CmdletBinding()]
param([ValidateSet('dev','hml')][string]$Environment='dev',[switch]$RecreateContainers,[switch]$TestBackup)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$compose=Get-ComposeArguments $Environment
Invoke-CheckedCommand docker ($compose+@('config','--quiet'))
Invoke-CheckedCommand docker ($compose+@('exec','-T','db','psql','-U','financas_admin','-d','financas','-c','SELECT current_database(), version();'))
# Independent probe, never a financial record.
$probe=[guid]::NewGuid().ToString()
$sql="CREATE TABLE IF NOT EXISTS infra_persistence_probe(id VARCHAR(36) PRIMARY KEY); INSERT INTO infra_persistence_probe(id) VALUES('$probe');"
Invoke-CheckedCommand docker ($compose+@('exec','-T','db','psql','-U','financas_admin','-d','financas','-v','ON_ERROR_STOP=1','-c',$sql))
if($RecreateContainers){Invoke-CheckedCommand docker ($compose+@('up','-d','--force-recreate','--wait','--wait-timeout','300','db','app'))}
$count=& docker @compose exec -T db psql -U financas_admin -d financas -tAc "SELECT COUNT(*) FROM infra_persistence_probe WHERE id='$probe';"
if($LASTEXITCODE -ne 0 -or ($count -join '').Trim() -ne '1'){throw 'Falha de continuidade do volume.'}
Invoke-CheckedCommand docker ($compose+@('exec','-T','db','psql','-U','financas_admin','-d','financas','-c',"DELETE FROM infra_persistence_probe WHERE id='$probe';"))
if($TestBackup){& "$PSScriptRoot/backup-postgres.ps1" -Environment $Environment -Mode once}
Write-Output 'Volume persistente validado. Prova removida sem alterar dados financeiros.'
