[CmdletBinding()]
param([ValidateSet('dev','hml')][string]$Environment='dev')
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$compose=Get-ComposeArguments $Environment
$root=Get-ProjectRoot
$configuration=Read-EnvironmentFile (Join-Path $root ".env.$Environment")
$backupPath=$configuration['BACKUP_DIRECTORY']
if(-not [IO.Path]::IsPathRooted($backupPath)){$backupPath=Join-Path $root $backupPath}
$marker=[guid]::NewGuid().ToString()
$restore='financas_restore_'+[guid]::NewGuid().ToString('N')
$beforeFiles=@(Get-ChildItem -LiteralPath $backupPath -Filter '*.dump.gpg' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name)
$sql="CREATE TABLE IF NOT EXISTS infra_backup_probe(id VARCHAR(36) PRIMARY KEY); INSERT INTO infra_backup_probe(id) VALUES('$marker');"
Invoke-CheckedCommand docker ($compose+@('exec','-T','db','psql','-U','financas_admin','-d','financas','-v','ON_ERROR_STOP=1','-c',$sql))
try {
    & "$PSScriptRoot/backup-postgres.ps1" -Environment $Environment -Mode once
    $backup=Get-ChildItem -LiteralPath $backupPath -Filter '*.dump.gpg' | Where-Object {$_.Name -notin $beforeFiles} | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if(-not $backup){throw 'Backup novo não encontrado.'}
    & "$PSScriptRoot/backup-postgres.ps1" -Environment $Environment -Mode verify -File $backup.Name
    & "$PSScriptRoot/backup-postgres.ps1" -Environment $Environment -Mode restore -File $backup.Name -RestoreDatabase $restore
    $value=& docker @compose exec -T db psql -U financas_admin -d $restore -tAc "SELECT COUNT(*) FROM infra_backup_probe WHERE id='$marker';"
    if($LASTEXITCODE -ne 0 -or ($value -join '').Trim() -ne '1'){throw 'Marcador não encontrado no banco restaurado.'}
    foreach($table in @('app_user','account','entry','transfer','purchase','installment','invoice','invoice_payment','budget','audit','idempotency')){
        $original=& docker @compose exec -T db psql -U financas_admin -d financas -tAc "SELECT COUNT(*) FROM $table;"
        if($LASTEXITCODE -ne 0){throw 'Falha ao contar origem.'}
        $restored=& docker @compose exec -T db psql -U financas_admin -d $restore -tAc "SELECT COUNT(*) FROM $table;"
        if($LASTEXITCODE -ne 0 -or ($original -join '').Trim() -ne ($restored -join '').Trim()){throw "Contagem diferente em $table. Não escreva durante o ensaio."}
    }
    [void][IO.Directory]::CreateDirectory((Join-Path $root 'target'))
    [IO.File]::WriteAllText((Join-Path $root 'target/postgres-backup-validation.txt'),"Backup: $($backup.Name)`nRestore: $restore`nMarcador e contagens reconciliados.`n",(New-Object Text.UTF8Encoding($false)))
    Write-Output "Backup criptografado e restore reconciliados em $restore. Banco de ensaio preservado para revisão."
} finally {
    Invoke-CheckedCommand docker ($compose+@('exec','-T','db','psql','-U','financas_admin','-d','financas','-c',"DELETE FROM infra_backup_probe WHERE id='$marker';"))
}
