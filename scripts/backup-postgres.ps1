[CmdletBinding()]
param([ValidateSet('dev','hml','prod')][string]$Environment='dev',[ValidateSet('once','verify','restore')][string]$Mode='once',[string]$File,[string]$RestoreDatabase)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$compose=Get-ComposeArguments $Environment
$arguments=@($Mode)
if($Mode -in @('verify','restore')){
    if($File -notmatch '^financas-[A-Za-z0-9-]+\.dump\.gpg$'){throw 'Informe somente o nome válido de um backup.'}
    $arguments+=$File
}
if($Mode -eq 'restore'){
    if($RestoreDatabase -notmatch '^financas_restore_[a-z0-9_]+$'){throw 'O destino deve ser um novo banco chamado financas_restore_<identificador>.'}
    $arguments+=$RestoreDatabase
}
Invoke-CheckedCommand docker ($compose+@('build','backup'))
Invoke-CheckedCommand docker ($compose+@('run','--rm','--no-deps','backup')+$arguments)
