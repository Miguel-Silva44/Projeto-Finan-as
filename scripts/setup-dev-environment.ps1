[CmdletBinding(SupportsShouldProcess=$true)]
param([switch]$Install,[switch]$UpdateWsl,[switch]$StartDocker,[switch]$PullImages)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/environment-common.ps1"
$root=Get-ProjectRoot
$report=& "$PSScriptRoot/check-environment.ps1" -PassThru -ReportPath (Join-Path $root 'data/environment-check.json')
Write-Output ('Engine Linux pronto: '+$report.ReadyForContainers)
if($Install) {
    if($report.Checks.wsl.State -ne 'ok') {
        $identity=[Security.Principal.WindowsIdentity]::GetCurrent()
        $administrator=(New-Object Security.Principal.WindowsPrincipal($identity)).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
        if(-not $administrator){throw 'WSL precisa ser preparado. Abra PowerShell como administrador e execute novamente com -Install; não haverá reboot automático.'}
        if($PSCmdlet.ShouldProcess('Windows','Instalar WSL 2 sem distribuição')) {
            & wsl --install --no-distribution
            if($LASTEXITCODE -notin @(0,3010)){throw "Instalação WSL falhou ($LASTEXITCODE)."}
            Write-Output 'WSL preparado. Se o Windows solicitar reinicialização, salve o trabalho e reinicie manualmente antes de retomar.'
        }
        return
    }
    if(-not(Get-Command docker -ErrorAction SilentlyContinue)) {
        $installed=Get-ItemProperty 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Uninstall/*','HKLM:/Software/Microsoft/Windows/CurrentVersion/Uninstall/*' -ErrorAction SilentlyContinue | Where-Object { $_.PSObject.Properties['DisplayName'] -and $_.DisplayName -like 'Docker Desktop*' }
        if($installed){throw 'Docker Desktop já está instalado, mas fora do PATH. Reabra o terminal ou conclua a primeira inicialização.'}
        if(Get-Command winget -ErrorAction SilentlyContinue) {
            if($PSCmdlet.ShouldProcess('Docker Desktop','Baixar e instalar pelo WinGet com interação')) {
                Invoke-CheckedCommand winget @('show','--exact','--id','Docker.DockerDesktop','--source','winget')
                Invoke-CheckedCommand winget @('install','--exact','--id','Docker.DockerDesktop','--source','winget','--interactive')
            }
        } else {
            if($env:PROCESSOR_ARCHITECTURE -ne 'AMD64'){throw 'Fallback automático validado somente para Windows x64. Use o instalador oficial da sua arquitetura.'}
            if($PSCmdlet.ShouldProcess('Docker Desktop','Baixar instalador oficial e validar assinatura')) {
                $installer=Join-Path $root 'data/installers/DockerDesktopInstaller.exe'
                [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($installer))
                Invoke-WebRequest -UseBasicParsing -Uri 'https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe' -OutFile $installer
                $signature=Get-AuthenticodeSignature -LiteralPath $installer
                if($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Docker'){throw 'Assinatura Docker inválida. Instalação interrompida.'}
                # Interactive installer must remain visible for license/permission decisions.
                $process=Start-Process -FilePath $installer -Wait -PassThru -ArgumentList 'install','--user','--backend=wsl-2'
                if($process.ExitCode -notin @(0,3010)){throw "Instalador retornou $($process.ExitCode)."}
            }
        }
        Write-Output 'Reabra o terminal para atualizar PATH. Resolva solicitações do instalador; não há aceite automático de licença.'
        return
    }
}
if($UpdateWsl -and $PSCmdlet.ShouldProcess('WSL','Atualizar WSL e selecionar versão 2')){Invoke-CheckedCommand wsl @('--update');Invoke-CheckedCommand wsl @('--set-default-version','2')}
if($StartDocker -and $PSCmdlet.ShouldProcess('Docker Desktop','Iniciar engine')){Invoke-CheckedCommand docker @('desktop','start','--timeout','120')}
$engine=Invoke-DiagnosticCommand docker @('info','--format','{{.OSType}}')
if($engine.State -ne 'ok' -or $engine.Output.Trim() -ne 'linux') {
    Write-Output 'Docker Linux ainda não está pronto. Conclua WSL/primeiro uso do Docker e execute novamente com -StartDocker.'
    if($PullImages){throw 'Não é possível baixar imagens sem engine Linux pronto.'}
    return
}
if($PullImages) {
    $images=Read-EnvironmentFile (Join-Path $root 'config/images.env')
    foreach($name in @('POSTGRES_IMAGE','MAVEN_IMAGE','JAVA_IMAGE')){
        if($PSCmdlet.ShouldProcess($images[$name],'Baixar imagem fixada por digest')){Invoke-CheckedCommand docker @('pull',$images[$name])}
    }
    Invoke-CheckedCommand docker @('run','--rm','hello-world')
}
Write-Output 'Preparação disponível. Gere o ambiente com new-environment.ps1 e inicie com start-environment.ps1.'
