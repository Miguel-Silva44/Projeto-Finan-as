[CmdletBinding()]
param([ValidateSet('dev','hml','prod')][string]$Environment='dev',[int]$AppPort=8080,[int]$DatabasePort=5432,[string]$BaseUrl,[string]$BackupDirectory)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$root=Get-ProjectRoot
$envPath=Join-Path $root ".env.$Environment"
$secretDirectory=Join-Path $root "data/secrets/$Environment"
if(Test-Path -LiteralPath $envPath){throw ".env.$Environment já existe. Nenhuma credencial foi alterada."}
if(Test-Path -LiteralPath $secretDirectory){throw 'Já existe diretório de segredos. Reutilize-o ou investigue uma preparação parcial; não gere novas senhas sobre um banco existente.'}
if($AppPort -lt 1024 -or $AppPort -gt 65535 -or $DatabasePort -lt 1024 -or $DatabasePort -gt 65535){throw 'Portas devem estar entre 1024 e 65535.'}
if(-not $BaseUrl){if($Environment -eq 'prod'){throw 'Produção exige -BaseUrl https://seu-dominio e -BackupDirectory para armazenamento independente.'};$BaseUrl="http://localhost:$AppPort"}
if($BaseUrl -match '[\r\n]' -or $BackupDirectory -match '[\r\n]'){throw 'Configuração inválida.'}
if($Environment -eq 'prod' -and ($BaseUrl -notmatch '^https://[^/ ]+' -or -not $BackupDirectory)){throw 'Produção exige HTTPS e caminho explícito para backup independente.'}
if(-not $BackupDirectory){$BackupDirectory="./data/backups/$Environment"}
[void][IO.Directory]::CreateDirectory($secretDirectory)
$utf8=New-Object Text.UTF8Encoding($false)
foreach($name in @('admin_password','migration_password','app_password','backup_password')){
    $bytes=New-Object byte[] 48
    $random=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$random.GetBytes($bytes)}finally{$random.Dispose()}
    [IO.File]::WriteAllText((Join-Path $secretDirectory $name),[Convert]::ToBase64String($bytes),$utf8)
}
# SMTP secrets are configured separately by the operator, never echoed by scripts.
foreach($name in @('smtp_user','smtp_password')){[IO.File]::WriteAllText((Join-Path $secretDirectory $name),'',$utf8)}
$profiles=if($Environment -eq 'dev'){'dev,postgres'}elseif($Environment -eq 'prod'){'prod,postgres'}else{'postgres'}
$mailMode=if($Environment -eq 'prod'){'smtp'}else{'file'}
$secure=if($Environment -eq 'prod'){'true'}else{'false'}
$lines=@("FINANCE_ENV=$Environment","SPRING_PROFILES_ACTIVE=$profiles",'POSTGRES_DB=financas',"APP_PORT=$AppPort","DB_PORT=$DatabasePort","BASE_URL=$BaseUrl","COOKIE_SECURE=$secure","MAIL_MODE=$mailMode",'SMTP_HOST=localhost','SMTP_PORT=1025','SMTP_AUTH=false','SMTP_TLS=false','MAIL_FROM=acesso@financas.local',"BACKUP_DIRECTORY=$BackupDirectory",'BACKUP_INTERVAL_SECONDS=86400','BACKUP_RETENTION_DAYS=90')
$lines+=Get-Content (Join-Path $root 'config/images.env') | Where-Object {$_ -match '^[A-Z_]+='}
[IO.File]::WriteAllText($envPath,($lines -join "`n")+"`n",$utf8)
Write-Output "Ambiente $Environment criado; senhas não são exibidas. Preserve data/secrets/$Environment para reabrir o banco."
if($Environment -eq 'prod'){Write-Output 'Configure SMTP no arquivo de ambiente e segredos SMTP antes de iniciar. Valide armazenamento de backup e proxy HTTPS.'}
