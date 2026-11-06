[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$SourceJdbcUrl,[ValidateSet('dev','hml','prod')][string]$Environment='dev',[switch]$AllowDevelopment,[switch]$WritesStopped)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
if(-not $WritesStopped){throw 'Pare todas as escritas e faça backup da origem; depois informe -WritesStopped.'}
if($Environment -ne 'dev' -and $AllowDevelopment){throw 'Contas de desenvolvimento só podem ser importadas para ambiente dev.'}
$root=Get-ProjectRoot
$values=Read-EnvironmentFile (Join-Path $root ".env.$Environment")
if($Environment -ne 'dev'){throw 'Por padrão, bancos hml/prod não expõem porta. Execute o utilitário em rede administrativa com IMPORT_TARGET_URL e credencial de migração, conforme documentação.'}
$env:IMPORT_SOURCE_URL=$SourceJdbcUrl
$env:IMPORT_TARGET_URL="jdbc:postgresql://127.0.0.1:$($values['DB_PORT'])/financas"
$env:IMPORT_TARGET_PASSWORD_FILE=Join-Path $root "data/secrets/$Environment/migration_password"
$env:IMPORT_ALLOW_DEVELOPMENT=if($AllowDevelopment){'true'}else{'false'}
$env:IMPORT_REPORT=Join-Path $root "data/import-$Environment-report.txt"
Invoke-CheckedCommand java @('-Dloader.main=br.com.financa.tools.H2PostgresImporter','-cp',(Join-Path $root 'target/controle-financeiro-1.0.0.jar'),'org.springframework.boot.loader.launch.PropertiesLauncher')
