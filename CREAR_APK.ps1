param([string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk", [string]$Jdk = '')
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
if (-not $Jdk) {
    $found = Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue | Where-Object Name -Like 'jdk-17*' | Select-Object -First 1
    if ($found) { $Jdk = $found.FullName } elseif ($env:JAVA_HOME) { $Jdk = $env:JAVA_HOME }
}
if (-not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\java.exe'))) { throw 'Instala JDK 17 o pasa -Jdk con su carpeta.' }
if (-not (Test-Path -LiteralPath $Sdk)) { throw 'Instala Android SDK 35 desde Android Studio o pasa -Sdk con su carpeta.' }
$env:JAVA_HOME = $Jdk
Set-Content -LiteralPath (Join-Path $projectDir 'local.properties') -Value ('sdk.dir=' + $Sdk.Replace('\','/')) -Encoding ascii
$signDir = Join-Path $projectDir '.signing'
$signProps = Join-Path $signDir 'signing.properties'
if (-not (Test-Path -LiteralPath $signProps)) {
    New-Item -ItemType Directory -Path $signDir -Force | Out-Null
    $randomBytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randomBytes)
    $keyPassword = [Convert]::ToBase64String($randomBytes).Replace('=','').Replace('+','a').Replace('/','b')
    $keyPath = Join-Path $signDir 'videoflow-android.jks'
    & (Join-Path $Jdk 'bin\keytool.exe') -genkeypair -keystore $keyPath -storetype JKS -alias videoflow -keyalg RSA -keysize 3072 -validity 10000 -storepass $keyPassword -keypass $keyPassword -dname 'CN=VideoFlow Local Android, OU=Local Build, O=VideoFlow Local, C=PE'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la clave de firma.' }
    @("storeFile=.signing/videoflow-android.jks", "storePassword=$keyPassword", 'keyAlias=videoflow', "keyPassword=$keyPassword") | Set-Content -LiteralPath $signProps -Encoding ascii
}
Push-Location -LiteralPath $projectDir
try {
    & .\gradlew.bat assembleRelease lintRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'La compilación o revisión de Android falló. Revisa el registro anterior.' }
    $releaseDir = Join-Path $projectDir 'release'
    $apkDir = Join-Path $projectDir 'app\build\outputs\apk\release'
    $version = (Get-Content -LiteralPath (Join-Path $apkDir 'output-metadata.json') -Raw | ConvertFrom-Json).elements[0].versionName
    if (-not $version) { throw 'No se pudo leer la versión de la APK.' }
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
    $names = @{ 'arm64-v8a'='ARM64'; 'armeabi-v7a'='ARM32'; 'universal'='Universal' }
    $checksums = @()
    foreach ($abi in $names.Keys) {
        $source = Join-Path $apkDir "app-$abi-release.apk"
        $target = Join-Path $releaseDir ("VideoFlow-Local-Android-$version-" + $names[$abi] + '.apk')
        Copy-Item -LiteralPath $source -Destination $target -Force
        $checksums += (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + [IO.Path]::GetFileName($target)
    }
    $checksums | Set-Content -LiteralPath (Join-Path $releaseDir 'SHA256SUMS.txt') -Encoding ascii
    Write-Host "APK creadas en: $releaseDir"
    Write-Host 'Conserva .signing en privado: permite instalar futuras actualizaciones sobre esta versión.'
} finally { Pop-Location }
