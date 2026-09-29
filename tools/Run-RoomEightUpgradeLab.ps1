param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$PreviousAppApk,
    [Parameter(Mandatory)][string]$PreviousTestApk,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$AppApkHash,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$TestApkHash,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$ExpectedSource,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$Serial='emulator-5576'
$ExpectedAvd='Next2U_UpgradeRoom8_Lab_20260926'
$labPackage='com.aistudio.hbandhealthtech.pxq97m.storagelab'
$testPackage="$labPackage.test"
$oldHash='7b36f18fc9ae1c8785d09fa2ef529a8cd442c93e6934526315e56eb95545cb73'
$oldTestHash='a9a854b587af96980867ffc217003152c4bb3b1ae69522f1b255a99b53663f23'
$certificate='a3ffbcad401b20092a131260cf84af5728959fb43c1a06aa8b268c8243e06164'
$adb=Join-Path $SdkRoot 'platform-tools/adb.exe'
$aapt=Join-Path $SdkRoot 'build-tools/36.0.0/aapt.exe'
$signer=Join-Path $SdkRoot 'build-tools/36.0.0/apksigner.bat'
$repo=Split-Path $PSScriptRoot -Parent
function Assert-Source {
    $head=& git -C $repo rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $head -cne $ExpectedSource){throw 'Source SHA mismatch'}
    $status=& git -C $repo status --porcelain=v1
    if($LASTEXITCODE -ne 0 -or $status){throw 'Clean source required'}
}
function Hash-File([string]$Path){ (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Invoke-Adb([string[]]$AdbArgs){
    $result=& $adb -s $Serial @AdbArgs 2>&1
    if($LASTEXITCODE -ne 0){throw "ADB failed: $($AdbArgs[0])"}
    ($result -join "`n").Trim()
}
function Assert-Target {
    if((Invoke-Adb @('get-state')) -ne 'device' -or (Invoke-Adb @('shell','getprop','ro.kernel.qemu')) -ne '1'){throw 'Emulator required'}
    if((Invoke-Adb @('shell','getprop','sys.boot_completed')) -ne '1'){throw 'Boot incomplete'}
    if(((Invoke-Adb @('emu','avd','name')) -split '\r?\n')[0].Trim() -cne $ExpectedAvd){throw 'Wrong AVD'}
}
function Inspect-Apk([string]$Path,[string]$Package,[string]$Hash){
    if((Hash-File $Path) -cne $Hash){throw 'Input APK hash mismatch'}
    $badging=(& $aapt dump badging $Path 2>&1) -join "`n"
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect APK'}
    $m=[regex]::Match($badging,"(?m)^package: name='([^']+)' versionCode='([^']*)'")
    if(!$m.Success -or $m.Groups[1].Value -cne $Package){throw 'Non-lab package refused'}
    if($Package -ceq $labPackage -and $m.Groups[2].Value -ne '1'){throw 'Wrong versionCode'}
    $signature=(& $signer verify --print-certs $Path 2>&1) -join "`n"
    if($LASTEXITCODE -ne 0){throw 'Invalid signature'}
    $certs=[regex]::Matches($signature,'(?m)^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})\r?$')
    if($certs.Count -ne 1 -or $certs[0].Groups[1].Value -cne $certificate){throw 'Wrong certificate'}
    $manifest=(& $aapt dump xmltree $Path AndroidManifest.xml 2>&1) -join "`n"
    if($LASTEXITCODE -ne 0 -or $manifest.Contains('"android.permission.INTERNET"')){throw 'Network permission or invalid manifest'}
    if($Package -ceq $labPackage -and ($manifest -notmatch 'android\.app\.Application' -or
        $manifest -match 'androidx\.startup\.InitializationProvider|com\.google\.firebase\.provider\.FirebaseInitProvider')){throw 'Application isolation missing'}
    @{path=$Path;sha256=$Hash;signature=$signature;manifest=$manifest;badging=$badging}
}
function Assert-Installed([string]$Package,[string]$Hash){
    $paths=Invoke-Adb @('shell','pm','path',$Package)
    if($paths -cnotmatch '^package:(/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk)$'){throw 'Unexpected installed paths'}
    $digest=Invoke-Adb @('shell','sha256sum',$Matches[1])
    if(($digest -split '\s+')[0] -cne $Hash){throw 'Installed hash mismatch'}
}
function Digest-Text([string]$Value){
    $bytes=[Text.Encoding]::UTF8.GetBytes($Value)
    $digest=[Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($digest.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() }
    finally { $digest.Dispose() }
}
$script:seenPids=@()
$script:keyHash=$null
$script:oldSnapshot=$null
$script:newSnapshot=$null
$script:phases=@()
function Run-Phase([string]$Phase,[string]$Method,[string]$AppHash,[string]$TestHash,[int]$Schema){
    Assert-Target
    Assert-Installed $labPackage $AppHash
    Assert-Installed $testPackage $TestHash
    Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
    $result=Invoke-Adb @('shell','am','instrument','-w','-r','-e','storageLab','synthetic-only',
        '-e','class',"com.example.data.local.UpgradePreservationAndroidLabTest#$Method",
        "$testPackage/androidx.test.runner.AndroidJUnitRunner")
    $result | Set-Content (Join-Path $OutputDirectory "$Phase.txt")
    if($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'){throw "Failed $Phase; preserve state"}
    $pidMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_pid=(\d+)\r?$')
    $snapshotMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_snapshot_sha256=([a-f0-9]{64})\r?$')
    $keyMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_key_metadata_sha256=([a-f0-9]{64})\r?$')
    $stateMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_snapshot=(\{[^\r\n]*\})\r?$')
    if(!$pidMatch.Success -or !$snapshotMatch.Success -or !$keyMatch.Success -or !$stateMatch.Success){throw 'Missing phase evidence'}
    $state=$stateMatch.Groups[1].Value
    if((Digest-Text $state) -cne $snapshotMatch.Groups[1].Value){throw 'Snapshot digest mismatch'}
    $snapshot=$state | ConvertFrom-Json
    if($snapshot.schema -ne $Schema){throw 'Wrong schema in phase evidence'}
    if($Schema -eq 7){
        if($script:oldSnapshot -and $script:oldSnapshot -cne $state){throw 'Previous version changed state'}
        $script:oldSnapshot=$state
    } else {
        if(!$script:oldSnapshot){throw 'Missing v7 baseline'}
        # All fields remain byte-for-byte equal except the one explicit version field.
        $oldVersionMatches=[regex]::Matches($script:oldSnapshot,'"schema":7(?=[,}])')
        if($oldVersionMatches.Count -ne 1){throw 'Ambiguous schema field'}
        $expected=$script:oldSnapshot -creplace '"schema":7(?=[,}])','"schema":8'
        if($expected -cne $state){throw 'Unexpected state change across APK replacement'}
        if($script:newSnapshot -and $script:newSnapshot -cne $state){throw 'Updated reopen changed state'}
        $script:newSnapshot=$state
    }
    $processId=$pidMatch.Groups[1].Value
    if($processId -in $script:seenPids){throw 'Process reused'}
    $script:seenPids += $processId
    if($script:keyHash -and $script:keyHash -cne $keyMatch.Groups[1].Value){throw 'Key metadata changed'}
    $script:keyHash=$keyMatch.Groups[1].Value
    $script:phases += @{phase=$Phase;pid=$processId;schema=$Schema;snapshotSha256=$snapshotMatch.Groups[1].Value;appSha256=$AppHash;testSha256=$TestHash}
    Assert-Installed $labPackage $AppHash
    Assert-Installed $testPackage $TestHash
}
Assert-Source
Assert-Target
if(Test-Path -LiteralPath $OutputDirectory){throw 'Existing evidence preserved'}
$old=Inspect-Apk $PreviousAppApk $labPackage $oldHash
$oldTest=Inspect-Apk $PreviousTestApk $testPackage $oldTestHash
$new=Inspect-Apk $AppApk $labPackage $AppApkHash
$test=Inspect-Apk $TestApk $testPackage $TestApkHash
if(($old.manifest -replace '\(line=\d+\)','(line)') -cne ($new.manifest -replace '\(line=\d+\)','(line)')){throw 'Isolated manifest changed'}
if(Invoke-Adb @('shell','pm','list','packages',$labPackage)){throw 'Fresh laboratory required; preserve all existing packages'}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$record=[ordered]@{started=(Get-Date -Format o);result='IN_PROGRESS';phase='install-previous';harnessHead=$ExpectedSource;
    oldHead='466d79a8ef0229adb72f36f56a9743a5db99960f';oldTestHead='82b395015ddf6d5f7557f75bfd63bd77b59f3f67';newHead=$ExpectedSource;
    oldAppSha256=$oldHash;newAppSha256=$AppApkHash;oldTestSha256=$oldTestHash;newTestSha256=$TestApkHash;
    certificate=$certificate;serial=$Serial;avd=$ExpectedAvd;versionCodeBefore=1;versionCodeAfter=1;
    schemaBefore=7;schemaAfter=8;executor='Codex local Windows Android emulator; not CI';pilotReady=$false}
foreach($entry in @(@('previous',$old),@('previous-test',$oldTest),@('current',$new),@('test',$test))){
    foreach($kind in @('manifest','signature','badging')){ $entry[1][$kind] | Set-Content (Join-Path $OutputDirectory "$($entry[0])-$kind.txt") }
}
try {
    foreach($apk in @($PreviousAppApk,$PreviousTestApk)){
        $installed=Invoke-Adb @('install','-t',$apk)
        $installed | Add-Content (Join-Path $OutputDirectory 'initial-install.txt')
        if($installed -notmatch '(?m)^Success\r?$'){throw 'Install unsuccessful'}
    }
    $record.phase='seed-previous'
    Run-Phase '01-seed-previous' 'seedPreviousVersion' $oldHash $oldTestHash 7
    $record.phase='reopen-previous'
    Run-Phase '02-reopen-previous' 'verifyExistingStateInNewProcess' $oldHash $oldTestHash 7
    $record.phase='replace-app-and-instrumentation'
    Assert-Target
    Assert-Installed $labPackage $oldHash
    Assert-Installed $testPackage $oldTestHash
    foreach($entry in @(@($AppApk,$AppApkHash,'replace-app.txt'),@($TestApk,$TestApkHash,'replace-instrumentation.txt'))){
        if((Hash-File $entry[0]) -cne $entry[1]){throw 'New APK changed'}
        $replace=Invoke-Adb @('install','-r','-t',$entry[0])
        $replace | Set-Content (Join-Path $OutputDirectory $entry[2])
        if($replace -notmatch '(?m)^Success\r?$'){throw 'Replacement unsuccessful'}
    }
    $record.phase='verify-updated'
    Run-Phase '03-verify-updated' 'verifyExistingV7StateAfterMigration' $AppApkHash $TestApkHash 8
    $record.phase='force-stop-reopen-updated'
    Run-Phase '04-force-stop-reopen-updated' 'verifyExistingV7StateAfterMigration' $AppApkHash $TestApkHash 8
    foreach($entry in @(@($PreviousAppApk,$oldHash),@($PreviousTestApk,$oldTestHash),@($AppApk,$AppApkHash),@($TestApk,$TestApkHash))){
        if((Hash-File $entry[0]) -cne $entry[1]){throw 'Input changed'}
    }
    Assert-Source
    $record.result='PASS'
} catch { $record.result='FAIL'; $record.error=$_.Exception.Message; throw }
finally {
    $record.ended=Get-Date -Format o
    $record.processIds=$script:seenPids
    $record.phases=$script:phases
    $record.keyMetadataSha256=$script:keyHash
    $record | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDirectory 'result.json')
}
