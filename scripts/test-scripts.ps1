[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$failures=@()
foreach($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1'){
    $tokens=$null;$errors=$null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors)
    foreach($error in $errors){$failures+=($file.Name+': '+$error.Message)}
}
if($failures.Count){throw ($failures -join "`n")}
Write-Output ('Sintaxe PowerShell validada: '+(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1').Count+' scripts.')
