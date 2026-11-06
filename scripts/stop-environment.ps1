[CmdletBinding()]
param([ValidateSet('dev','hml','prod')][string]$Environment='dev')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
Invoke-CheckedCommand docker ((Get-ComposeArguments $Environment)+@('--profile','backup','down'))
Write-Output 'Containers parados. Volumes e backups preservados.'
