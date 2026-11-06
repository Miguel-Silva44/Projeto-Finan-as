Set-StrictMode -Version Latest

# Refresh PATH for this process only after a Desktop install.
if(-not(Get-Command docker -ErrorAction SilentlyContinue)) {
    foreach($candidate in @((Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin'),(Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'))) {
        if(Test-Path -LiteralPath (Join-Path $candidate 'docker.exe')){$env:PATH=$candidate+';'+$env:PATH;break}
    }
}

function Get-ProjectRoot {
    return [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Invoke-CheckedCommand {
    param([Parameter(Mandatory=$true)][string]$FilePath, [string[]]$Arguments = @())
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$FilePath falhou (código $LASTEXITCODE)." }
}

function Invoke-DiagnosticCommand {
    param([string]$FilePath, [string[]]$Arguments=@(), [int]$TimeoutSeconds=15)
    $command = Get-Command $FilePath -ErrorAction SilentlyContinue
    if (-not $command) { return [pscustomobject]@{State='ausente'; ExitCode=$null; Output='Comando não encontrado'} }
    try {
        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = $command.Source
        $info.Arguments = ($Arguments | ForEach-Object { if($_ -match '[\s"]'){'"' + $_.Replace('"','\"') + '"'}else{$_} }) -join ' '
        $info.UseShellExecute=$false
        $info.CreateNoWindow=$true
        $info.RedirectStandardOutput=$true
        $info.RedirectStandardError=$true
        if($FilePath -eq 'wsl'){$info.StandardOutputEncoding=[Text.Encoding]::Unicode;$info.StandardErrorEncoding=[Text.Encoding]::Unicode}
        $process = New-Object Diagnostics.Process
        $process.StartInfo=$info
        [void]$process.Start()
        $output=$process.StandardOutput.ReadToEndAsync()
        $errors=$process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds*1000)) {
            $process.Kill()
            return [pscustomobject]@{State='não verificado';ExitCode=$null;Output='Tempo limite de diagnóstico excedido'}
        }
        $text=($output.Result + $errors.Result).Replace([string][char]0,'').Trim()
        return [pscustomobject]@{State=$(if($process.ExitCode -eq 0){'ok'}else{'falha'});ExitCode=$process.ExitCode;Output=$text}
    } catch { return [pscustomobject]@{State='não verificado';ExitCode=$null;Output=$_.Exception.GetType().Name} }
}

function Get-ComposeArguments {
    param([ValidateSet('dev','hml','prod')][string]$Environment='dev')
    $root=Get-ProjectRoot
    $envFile=Join-Path $root ".env.$Environment"
    if (-not (Test-Path -LiteralPath $envFile)) { throw "Crie .env.$Environment com scripts/new-environment.ps1 antes de continuar." }
    $configuration=Read-EnvironmentFile $envFile
    if($configuration['FINANCE_ENV'] -ne $Environment){throw 'O ambiente no arquivo não corresponde ao projeto Compose solicitado.'}
    $arguments=@('compose','--project-directory',$root,'-p',"financas-$Environment",'--env-file',$envFile,'-f',(Join-Path $root 'compose.yaml'))
    if ($Environment -eq 'dev') { $arguments+=@('-f',(Join-Path $root 'compose.dev.yaml')) }
    if ($Environment -eq 'prod') { $arguments+=@('-f',(Join-Path $root 'compose.prod.yaml')) }
    return $arguments
}

function Read-EnvironmentFile {
    param([string]$Path)
    $values=@{}
    foreach($line in [IO.File]::ReadAllLines($Path)) {
        if ($line -match '^([A-Z_]+)=(.*)$') { $values[$matches[1]]=$matches[2] }
    }
    return $values
}
