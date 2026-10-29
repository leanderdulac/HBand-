param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedAvd,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [switch]$IncludeAtomicInterruption
)
$ErrorActionPreference = 'Stop'
$labPackage = 'com.aistudio.hbandhealthtech.pxq97m.storagelab'
$testPackage = "$labPackage.test"
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$aapt = Join-Path $SdkRoot 'build-tools/36.0.0/aapt.exe'
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
if ($Serial -notmatch '^emulator-\d+$') { throw 'Emulator serial required' }
if ((Invoke-Adb @('get-state')) -ne 'device') { throw 'Emulator not ready' }
if ((Invoke-Adb @('shell','getprop','ro.kernel.qemu')) -ne '1') { throw 'Not a verified emulator' }
if ((Invoke-Adb @('shell','getprop','sys.boot_completed')) -ne '1') { throw 'Boot incomplete' }
$avdLines = (Invoke-Adb @('emu','avd','name')) -split '\r?\n'
if ($avdLines[0].Trim() -cne $ExpectedAvd) { throw 'Unexpected AVD; no installation allowed' }
foreach ($spec in @(@($AppApk,$labPackage), @($TestApk,$testPackage))) {
    $badging = (& $aapt dump badging $spec[0] 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK' }
    $match = [regex]::Match($badging, "(?m)^package: name='([^']+)'")
    if (-not $match.Success -or $match.Groups[1].Value -cne $spec[1]) { throw 'Refusing non-lab APK' }
    if (Invoke-Adb @('shell','pm','list','packages',$spec[1])) { throw 'Lab package already exists; preserve it and use a fresh lab AVD' }
}
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Output already exists; preserve previous evidence' }
$appHash = (Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash.ToLowerInvariant()
$testHash = (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash.ToLowerInvariant()
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$start = Get-Date -Format o
Invoke-Adb @('install','-t',$AppApk) | Set-Content (Join-Path $OutputDirectory 'install-app.txt')
Invoke-Adb @('install','-t',$TestApk) | Set-Content (Join-Path $OutputDirectory 'install-test.txt')
Assert-InstalledHash $labPackage $appHash
Assert-InstalledHash $testPackage $testHash
$class = 'com.example.data.local.StorageAndroidLabTest'
$phases = [ordered]@{
    seed = @{filter="$class#seedCurrentDatabase"; count=1}
    migration = @{filter=("$class#encryptedMigrationPreservesAllRowsAndStableIds", "$class#duplicateIdentityRollsBackEncryptedMigration", "$class#wrongKeyDoesNotReplaceEncryptedDatabase", 'com.example.ExampleInstrumentedTest') -join ','; count=4}
    reopen = @{filter="$class#reopenCurrentDatabaseInNewProcess"; count=1}
}
foreach ($name in $phases.Keys) {
    # Force-stop only the isolated synthetic package, never clear or uninstall it.
    Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
    $phase = $phases[$name]
    $result = Invoke-Adb @('shell','am','instrument','-w','-r','-e','storageLab','synthetic-only','-e','class',$phase.filter,"$testPackage/androidx.test.runner.AndroidJUnitRunner")
    $result | Set-Content (Join-Path $OutputDirectory "$name.txt")
    if ($result -notmatch "OK \($($phase.count) tests?\)" -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Instrumentation phase $name failed; evidence preserved"
    }
}
[int]$passedTests = 6
$expectedInterruptions = @()
if ($IncludeAtomicInterruption) {
    foreach ($point in @('before-queue', 'after-queue')) {
        Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
        # A crash is expected only here, and only accepted after independent recovery assertions.
        $crash = & $adb -s $Serial shell am instrument -w -r -e storageLab synthetic-only -e atomicCutPoint $point -e class "$class#interruptAtomicRecording" "$testPackage/androidx.test.runner.AndroidJUnitRunner" 2>&1
        $crashExit = $LASTEXITCODE
        $crashText = ($crash -join "`n")
        $crashText | Set-Content (Join-Path $OutputDirectory "atomic-$point-interrupted.txt")
        if ($crashText -notmatch 'Process crashed' -or $crashExit -notin @(0,1)) {
            throw "Expected isolated process termination missing at $point; preserve evidence"
        }
        $recovery = Invoke-Adb @('shell','am','instrument','-w','-r','-e','storageLab','synthetic-only',
            '-e','atomicCutPoint',$point,'-e','class',"$class#recoverAfterAtomicInterruption",
            "$testPackage/androidx.test.runner.AndroidJUnitRunner")
        $recovery | Set-Content (Join-Path $OutputDirectory "atomic-$point-recovered.txt")
        if ($recovery -notmatch 'OK \(1 test\)' -or $recovery -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
            throw "Atomic recovery failed at $point; preserve data and evidence"
        }
        $passedTests++
        $expectedInterruptions += @{cutPoint=$point; terminationObserved=$true; adbExit=$crashExit; recovery='PASS'}
    }
}
if ((Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash.ToLowerInvariant() -cne $appHash -or
    (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash.ToLowerInvariant() -cne $testHash) {
    throw 'Input APK changed during the test; preserve evidence without success claim'
}
Assert-InstalledHash $labPackage $appHash
Assert-InstalledHash $testPackage $testHash
[ordered]@{
    started=$start; ended=(Get-Date -Format o); serial=$Serial; avd=$ExpectedAvd
    android=(Invoke-Adb @('shell','getprop','ro.build.version.release'))
    api=(Invoke-Adb @('shell','getprop','ro.build.version.sdk'))
    abi=(Invoke-Adb @('shell','getprop','ro.product.cpu.abi'))
    fingerprint=(Invoke-Adb @('shell','getprop','ro.build.fingerprint'))
    appSha256=$appHash; testSha256=$testHash; installedHashesVerifiedBeforeAndAfter=$true
    tests=$passedTests; failures=0; expectedInterruptions=$expectedInterruptions
    scope='Synthetic isolated AVD; process termination is not power loss, pilot update or backend test'
} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'result.json')
Write-Output "PASS: $passedTests Android tests; $($expectedInterruptions.Count) expected process terminations verified separately; synthetic data preserved."
