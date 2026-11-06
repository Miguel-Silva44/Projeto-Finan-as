[CmdletBinding()]
param([ValidateSet('hml','prod')][string]$Environment='hml',[Parameter(Mandatory=$true)][string]$Fingerprint)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/android-common.ps1"
$config=Get-AndroidConfiguration $Environment
Assert-AndroidOrigin $config['origin']
if($Fingerprint -notmatch '^(?i)([0-9a-f]{2}:){31}[0-9a-f]{2}$'){throw 'Informe fingerprint SHA-256 com dois dígitos por byte e dois-pontos.'}
$origin=$config['origin'].TrimEnd('/')
$response=Invoke-WebRequest -UseBasicParsing -Uri "$origin/.well-known/assetlinks.json" -MaximumRedirection 0 -TimeoutSec 20
if($response.Headers['Content-Type'] -notmatch 'application/json'){throw 'Asset Links deve ser JSON público.'}
$links=$response.Content | ConvertFrom-Json
$match=@($links | Where-Object {$_.target.namespace -eq 'android_app' -and $_.target.package_name -eq $config['applicationId'] -and
    'delegate_permission/common.handle_all_urls' -in $_.relation -and $Fingerprint -in $_.target.sha256_cert_fingerprints})
if($match.Count -ne 1){throw 'Associação package/certificado ausente ou ambígua.'}
$manifest=Invoke-WebRequest -UseBasicParsing -Uri "$origin/manifest.webmanifest" -MaximumRedirection 0 -TimeoutSec 20
$web=$manifest.Content | ConvertFrom-Json
if($web.start_url -ne '/dashboard' -or $web.scope -ne '/'){throw 'Manifest incompatível com a configuração Android.'}
Write-Output 'HTTPS, manifest e associação conferidos. A validação no navegador do aparelho continua obrigatória.'
