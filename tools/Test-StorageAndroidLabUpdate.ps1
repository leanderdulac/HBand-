param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$ExpectedAvd,
    [Parameter(Mandatory)][string]$PreviousAppApk,
    [Parameter(Mandatory)][string]$PreviousTestApk,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [switch]$PreflightOnly
)
# Deliberately specific to the retained synthetic f6aaad9 lab, never a pilot updater.
$ErrorActionPreference = 'Stop'
$labPackage = 'com.aistudio.hbandhealthtech.pxq97m.storagelab'
$testPackage = "$labPackage.test"
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
$aapt = Join-Path $SdkRoot 'build-tools/36.0.0/aapt.exe'
$signer = Join-Path $SdkRoot 'build-tools/36.0.0/apksigner.bat'
$certificate = 'a3ffbcad401b20092a131260cf84af5728959fb43c1a06aa8b268c8243e06164'
function Hash-File([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Invoke-Adb([string[]]$AdbArgs) {
    $result = & $adb -s $Serial @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($AdbArgs[0])" }
    ($result -join "`n").Trim()
}
function Assert-Target {
    if ($Serial -notmatch '^emulator-\d+$') { throw 'Emulator serial required' }
    if ($Serial -cne 'emulator-5580') { throw 'Only emulator-5580 is allowed for this retained lab' }
    if ($ExpectedAvd -cne 'Next2U_Storage_Lab_20260925') { throw 'Only the retained synthetic AVD is allowed' }
    if ((Invoke-Adb @('get-state')) -ne 'device') { throw 'Emulator not ready' }
    if ((Invoke-Adb @('shell','getprop','ro.kernel.qemu')) -ne '1') { throw 'Not a verified emulator' }
    if ((Invoke-Adb @('shell','getprop','sys.boot_completed')) -ne '1') { throw 'Boot incomplete' }
    if (((Invoke-Adb @('emu','avd','name')) -split '\r?\n')[0].Trim() -cne $ExpectedAvd) {
        throw 'Unexpected AVD; no update allowed'
    }
}
function Inspect-Apk([string]$Path, [string]$Package) {
    $badging = (& $aapt dump badging $Path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect APK' }
    $match = [regex]::Match($badging, "(?m)^package: name='([^']+)' versionCode='([^']*)'")
    if (-not $match.Success -or $match.Groups[1].Value -cne $Package) { throw 'Refusing non-lab APK' }
    if ($Package -eq $labPackage -and $match.Groups[2].Value -ne '1') { throw 'This rehearsal requires versionCode 1' }
    $signature = (& $signer verify --print-certs $Path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Invalid APK signature' }
    $certs = [regex]::Matches($signature, '(?m)^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})\r?$')
    if ($certs.Count -ne 1 -or $certs[0].Groups[1].Value -cne $certificate) { throw 'Unexpected signing certificate' }
    $manifest = (& $aapt dump xmltree $Path AndroidManifest.xml 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect manifest' }
    if ($manifest.Contains('"android.permission.INTERNET"')) { throw 'Lab APK must not request INTERNET' }
    @{path=$Path; package=$Package; sha256=(Hash-File $Path); versionCode=$match.Groups[2].Value;
      signature=$signature; manifest=$manifest}
}
function Assert-Installed([string]$Package, [string]$ExpectedHash) {
    $paths = Invoke-Adb @('shell','pm','path',$Package)
    # A single, explicit base APK only. No split selection or guessed path.
    if ($paths -cnotmatch '^package:(/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk)$') { throw 'Missing package or unexpected APK paths' }
    $remotePath = $Matches[1]
    $digest = Invoke-Adb @('shell','sha256sum',$remotePath)
    if (($digest -split '\s+')[0] -cne $ExpectedHash) { throw "Installed APK mismatch: $Package" }
    @{package=$Package; path=$remotePath; sha256=$ExpectedHash}
}
function Reopen([string]$Phase) {
    Assert-Target
    Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
    $result = Invoke-Adb @('shell','am','instrument','-w','-r','-e','storageLab','synthetic-only',
        '-e','class','com.example.data.local.StorageAndroidLabTest#reopenCurrentDatabaseInNewProcess',
        "$testPackage/androidx.test.runner.AndroidJUnitRunner")
    $result | Set-Content (Join-Path $OutputDirectory "$Phase.txt")
    if ($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "Reopen failed in $Phase; evidence and data preserved"
    }
}

Assert-Target
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Output already exists; preserve previous evidence' }
# Pin the old artifacts whose synthetic seed was previously executed and recorded.
if ((Hash-File $PreviousAppApk) -cne '1a5b1b6003dd63e8cc3c28a59d3897687cc60b249daae8da86c7f44c200a47ca' -or
    (Hash-File $PreviousTestApk) -cne 'dce75d5ea051fb0634e3d63b63aba9cba013683e1c6079928f1a8918d04de156') {
    throw 'Previous APKs do not match the retained f6aaad9 fixture'
}
$oldApp = Inspect-Apk $PreviousAppApk $labPackage
$oldTest = Inspect-Apk $PreviousTestApk $testPackage
$newApp = Inspect-Apk $AppApk $labPackage
$newTest = Inspect-Apk $TestApk $testPackage
if ($oldApp.sha256 -eq $newApp.sha256) { throw 'App APK unchanged; not an update rehearsal' }
# Exact compiled manifest equality to the pinned, isolated baseline includes Application,
# disabled BLE/boot/launcher, absent auto-init providers and test target/runner.
foreach ($pair in @(@($oldApp,$newApp), @($oldTest,$newTest))) {
    $oldManifest = $pair[0].manifest -replace '\(line=\d+\)', '(line)'
    $newManifest = $pair[1].manifest -replace '\(line=\d+\)', '(line)'
    if ($oldManifest -cne $newManifest) { throw 'Manifest differs from pinned isolated lab; review required' }
}
$before = @((Assert-Installed $labPackage $oldApp.sha256), (Assert-Installed $testPackage $oldTest.sha256))
if ($PreflightOnly) { Write-Output 'PASS: read-only update preflight; no instrumentation or installation.'; return }

New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$record = [ordered]@{started=(Get-Date -Format o); result='IN_PROGRESS'; phase='before-reopen';
    serial=$Serial; avd=$ExpectedAvd; before=$before; appInstalled=$false; testInstalled=$false;
    oldAppHash=$oldApp.sha256; oldTestHash=$oldTest.sha256; newAppHash=$newApp.sha256; newTestHash=$newTest.sha256;
    certificate=$certificate; versionCodeBefore='1'; versionCodeAfter='1'; schemaBefore=7; schemaAfter=7;
    scope='Synthetic lab APK replacement, same versionCode and schema; not a pilot release upgrade'}
foreach ($entry in @(@('old-app',$oldApp),@('old-test',$oldTest),@('new-app',$newApp),@('new-test',$newTest))) {
    $entry[1].manifest | Set-Content (Join-Path $OutputDirectory "$($entry[0])-manifest.txt")
    $entry[1].signature | Set-Content (Join-Path $OutputDirectory "$($entry[0])-signature.txt")
}
try {
    Reopen 'before'
    $record.phase = 'install-app'
    Assert-Target
    Assert-Installed $labPackage $oldApp.sha256 | Out-Null
    Assert-Installed $testPackage $oldTest.sha256 | Out-Null
    if ((Hash-File $AppApk) -cne $newApp.sha256 -or (Hash-File $TestApk) -cne $newTest.sha256) { throw 'Input APK changed' }
    $result = Invoke-Adb @('install','-r','-t',$AppApk)
    $result | Set-Content (Join-Path $OutputDirectory 'install-app.txt')
    if ($result -notmatch '(?m)^Success\r?$') { throw 'App replacement did not succeed' }
    $record.appInstalled = $true
    $record.phase = 'install-test'
    Assert-Target
    if ((Hash-File $TestApk) -cne $newTest.sha256) { throw 'Input test APK changed' }
    $result = Invoke-Adb @('install','-r','-t',$TestApk)
    $result | Set-Content (Join-Path $OutputDirectory 'install-test.txt')
    if ($result -notmatch '(?m)^Success\r?$') { throw 'Test replacement did not succeed' }
    $record.testInstalled = $true
    $record.after = @((Assert-Installed $labPackage $newApp.sha256), (Assert-Installed $testPackage $newTest.sha256))
    $record.phase = 'after-reopen'
    Reopen 'after'
    $record.result = 'PASS'
    $record.phase = 'complete'
    Write-Output 'PASS: existing encrypted queue and key metadata reopened before and after APK replacement.'
} catch {
    $record.result = 'FAIL'
    $record.failure = $_.Exception.Message
    throw # Never reinstall old APKs, clear, reseed, uninstall, or repair after a partial failure.
} finally {
    $record.ended = Get-Date -Format o
    $record | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDirectory 'result.json')
}
