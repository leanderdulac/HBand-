param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedAvd,
    [Parameter(Mandatory)][string]$ExpectedSource,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$AppSha256,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][string]$TestSha256,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$labPackage = 'com.aistudio.hbandhealthtech.pxq97m.storagelab'
$testPackage = "$labPackage.test"
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$aapt = Join-Path $SdkRoot 'build-tools/36.0.0/aapt.exe'
$source = Split-Path -Parent $PSScriptRoot
function Assert-Source {
    $head = & git -C $source rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -cne $ExpectedSource) { throw 'Source SHA mismatch' }
    $status = & git -C $source status --porcelain=v1
    if ($LASTEXITCODE -ne 0 -or $status) { throw 'Source must be clean' }
}
function Invoke-Adb([string[]]$AdbArgs) {
    $result = & $adb -s $Serial @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($AdbArgs[0])" }
    return ($result -join "`n").Trim()
}
function Assert-InstalledHash([string]$Package, [string]$ExpectedHash) {
    $location = Invoke-Adb @('shell','pm','path',$Package)
    $match = [regex]::Match($location, '^package:(/data/app/[A-Za-z0-9/_+=.~\-]+/base\.apk)$')
    if (-not $match.Success) { throw 'Unexpected installed lab APK path' }
    $digest = Invoke-Adb @('shell','sha256sum',$match.Groups[1].Value)
    if (($digest -split '\s+')[0] -cne $ExpectedHash) { throw "Installed APK hash mismatch: $Package" }
}
function Assert-Emulator {
    if ($Serial -notmatch '^emulator-\d+$') { throw 'Emulator serial required' }
    if ((Invoke-Adb @('get-state')) -ne 'device') { throw 'Emulator not ready' }
    if ((Invoke-Adb @('shell','getprop','ro.kernel.qemu')) -ne '1') { throw 'Not an emulator' }
    if ((Invoke-Adb @('shell','getprop','sys.boot_completed')) -ne '1') { throw 'Boot incomplete' }
    $avdLines = (Invoke-Adb @('emu','avd','name')) -split '\r?\n'
    if ($avdLines[0].Trim() -cne $ExpectedAvd) { throw 'Unexpected AVD; refusing operation' }
}
Assert-Source
Assert-Emulator
$specs = @(@($AppApk,$labPackage,$AppSha256), @($TestApk,$testPackage,$TestSha256))
foreach ($spec in $specs) {
    if ($spec[2] -cnotmatch '^[a-f0-9]{64}$') { throw 'Explicit lowercase SHA256 required' }
    if ((Get-FileHash -LiteralPath $spec[0] -Algorithm SHA256).Hash.ToLowerInvariant() -cne $spec[2]) { throw 'Input APK hash mismatch' }
    $badging = (& $aapt dump badging $spec[0] 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK' }
    $match = [regex]::Match($badging, "(?m)^package: name='([^']+)'")
    if (-not $match.Success -or $match.Groups[1].Value -cne $spec[1]) { throw 'Refusing non-lab APK' }
    $permissions = (& $aapt dump permissions $spec[0] 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $permissions -match 'android\.permission\.INTERNET') { throw 'No INTERNET permission allowed, including test APK' }
    if (Invoke-Adb @('shell','pm','list','packages',$spec[1])) { throw 'Existing lab package; use a fresh AVD without deleting data' }
}
$manifest = (& $aapt dump xmltree $AppApk AndroidManifest.xml 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or $manifest -notmatch 'android\.app\.Application' -or
    $manifest -match 'androidx\.startup\.InitializationProvider|com\.google\.firebase\.provider\.FirebaseInitProvider') {
    throw 'Lab application/startup isolation not confirmed'
}
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Output exists; preserve previous evidence' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$start = Get-Date -Format o
$manifest | Set-Content (Join-Path $OutputDirectory 'app-manifest.txt')
try {
    Invoke-Adb @('install','-t',$AppApk) | Set-Content (Join-Path $OutputDirectory 'install-app.txt')
    Invoke-Adb @('install','-t',$TestApk) | Set-Content (Join-Path $OutputDirectory 'install-test.txt')
    foreach ($spec in $specs) { Assert-InstalledHash $spec[1] $spec[2] }
    $class = 'com.example.util.ProviderAndroidLabTest'
    $phases = [ordered]@{
        generate = 'generateAndVerifyNativeUris'
        reopen = 'reopenRetainedUrisInAnotherProcess'

    }
    foreach ($name in $phases.Keys) {
        Assert-Emulator
        Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
        $result = Invoke-Adb @('shell','am','instrument','-w','-r','-e','providerLab','synthetic-only',
            '-e','class',"$class#$($phases[$name])","$testPackage/androidx.test.runner.AndroidJUnitRunner")
        $result | Set-Content (Join-Path $OutputDirectory "$name.txt")
        if ($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
            throw "Phase $name failed; retained data must not be reseeded or cleared"
        }
    }
    foreach ($spec in $specs) {
        if ((Get-FileHash -LiteralPath $spec[0] -Algorithm SHA256).Hash.ToLowerInvariant() -cne $spec[2]) { throw 'APK changed during execution' }
        Assert-InstalledHash $spec[1] $spec[2]
    }
    Assert-Source
    [ordered]@{
        started=$start; ended=(Get-Date -Format o); source=$ExpectedSource; sourceCleanBeforeAndAfter=$true
        serial=$Serial; avd=$ExpectedAvd; appSha256=$AppSha256; testSha256=$TestSha256
        installedHashesVerifiedBeforeAndAfter=$true; internetPermissionAbsentInBothApks=$true
        android=(Invoke-Adb @('shell','getprop','ro.build.version.release'))
        api=(Invoke-Adb @('shell','getprop','ro.build.version.sdk'))
        abi=(Invoke-Adb @('shell','getprop','ro.product.cpu.abi'))
        fingerprint=(Invoke-Adb @('shell','getprop','ro.build.fingerprint'))
        tests=2; failures=0; artifacts=2; retainedAcrossProcessRestart=$true
        executor='Codex local Windows/Android emulator; not CI'
        scope='Native PNG rendering, FileProvider URI read and process reopen; not cross-app grants, chooser, APK update, physical device, backend or pilot acceptance'
    } | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'result.json')
    Write-Output 'PASS: 2 native Android phases; 2 PNGs read via FileProvider and retained after process restart; outside roots rejected.'
} catch {
    $_.Exception.Message | Set-Content (Join-Path $OutputDirectory 'FAILED.txt')
    throw
}
