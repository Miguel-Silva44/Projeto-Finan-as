[CmdletBinding()]
param([ValidateSet('dev','hml','prod')][string]$Environment='dev',[switch]$DatabaseOnly,[switch]$WithBackup,[switch]$NoBuild)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$engine=Invoke-DiagnosticCommand docker @('info','--format','{{.OSType}}')
if($engine.State -ne 'ok' -or $engine.Output.Trim() -ne 'linux'){throw 'Engine Docker Linux indisponível. Execute setup-dev-environment.ps1 -StartDocker.'}
$compose=Get-ComposeArguments $Environment
Invoke-CheckedCommand docker ($compose+@('config','--quiet'))
$argsList=$compose
if($WithBackup){$argsList+=@('--profile','backup')}
$argsList+=@('up','-d','--wait','--wait-timeout','300')
if(-not $NoBuild -and -not $DatabaseOnly){$argsList+='--build'}
if($DatabaseOnly){$argsList+='db'}
Invoke-CheckedCommand docker $argsList
Invoke-CheckedCommand docker ($compose+@('exec','-T','db','pg_isready','-U','financas_admin','-d','financas'))
Invoke-CheckedCommand docker ($compose+@('exec','-T','db','psql','-U','financas_admin','-d','financas','-c','SELECT version(), current_database();'))
Invoke-CheckedCommand docker ($compose+@('ps'))
Write-Output 'Serviços prontos. Volumes são preservados ao parar/recriar containers.'
