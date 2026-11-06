[CmdletBinding()]
param([ValidateSet('dev','hml')][string]$Environment='dev')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$root=Get-ProjectRoot
$values=Read-EnvironmentFile (Join-Path $root ".env.$Environment")
$secretDirectory=Join-Path $root "data/secrets/$Environment"
$env:SPRING_PROFILES_ACTIVE=$values['SPRING_PROFILES_ACTIVE']
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$($values['DB_PORT'])/financas"
$env:SPRING_DATASOURCE_USERNAME='financas_app'
$env:SPRING_FLYWAY_USER='financas_migrator'
$env:SPRING_DATASOURCE_PASSWORD=[IO.File]::ReadAllText((Join-Path $secretDirectory 'app_password'))
$env:SPRING_FLYWAY_PASSWORD=[IO.File]::ReadAllText((Join-Path $secretDirectory 'migration_password'))
$env:BASE_URL=$values['BASE_URL'];$env:PORT=$values['APP_PORT']
try {
    Push-Location $root
    Invoke-CheckedCommand mvn.cmd @('spring-boot:run')
} finally {
    Pop-Location
    Remove-Item Env:SPRING_DATASOURCE_PASSWORD,Env:SPRING_FLYWAY_PASSWORD -ErrorAction SilentlyContinue
}
