Set-StrictMode -Version Latest
. "$PSScriptRoot/environment-common.ps1"
function Get-AndroidSdk {
    if($env:ANDROID_HOME){return $env:ANDROID_HOME}
    if($env:ANDROID_SDK_ROOT){return $env:ANDROID_SDK_ROOT}
    $localSdk=Join-Path (Get-ProjectRoot) 'data/android-sdk'
    if(Test-Path -LiteralPath $localSdk){return $localSdk}
    return (Join-Path $env:LOCALAPPDATA 'Android/Sdk')
}
function Get-AndroidConfiguration([string]$Environment) {
    $path=Join-Path (Get-ProjectRoot) "android/config/$Environment.local.properties"
    if(-not(Test-Path -LiteralPath $path)){$path=Join-Path (Get-ProjectRoot) "android/config/$Environment.example.properties"}
    $result=@{}
    foreach($line in Get-Content -LiteralPath $path -Encoding UTF8){
        if($line -match '^([A-Za-z]+)=(.*)$'){$result[$matches[1]]=$matches[2].Trim()}
    }
    return $result
}
function Assert-AndroidOrigin([string]$Origin) {
    $uri=$null
    if(-not [Uri]::TryCreate($Origin,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -ne 'https' -or
        $uri.UserInfo -or $uri.Query -or $uri.Fragment -or $uri.AbsolutePath -ne '/' -or
        $uri.Host -eq 'localhost' -or $uri.Host.EndsWith('.local') -or $uri.Host.EndsWith('.invalid') -or $uri.HostNameType -ne [UriHostNameType]::Dns){
        throw 'Configure uma origem HTTPS pública sem caminho, credenciais ou tokens.'
    }
}
