param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$PreviousAppApk,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$TestApkHash,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$Serial='emulator-5590'
$ExpectedAvd='Next2U_Upgrade466d_Lab_20260925'
$labPackage='com.aistudio.hbandhealthtech.pxq97m.storagelab'
$testPackage="$labPackage.test"
$oldHash='b1b8046ed1bfbf23c8179b28d6e2aa8af21888ed5b0f42d1c21c48846a11e8b5'
$newHash='7b36f18fc9ae1c8785d09fa2ef529a8cd442c93e6934526315e56eb95545cb73'
$certificate='a3ffbcad401b20092a131260cf84af5728959fb43c1a06aa8b268c8243e06164'
$adb=Join-Path $SdkRoot 'platform-tools/adb.exe'
$aapt=Join-Path $SdkRoot 'build-tools/36.0.0/aapt.exe'
$signer=Join-Path $SdkRoot 'build-tools/36.0.0/apksigner.bat'
$repo=Split-Path $PSScriptRoot -Parent
$head=(& git -C $repo rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0 -or (& git -C $repo status --porcelain=v1)){throw 'Clean committed harness required'}
function Hash-File([string]$Path){ (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Invoke-Adb([string[]]$AdbArgs){
    $result=& $adb -s $Serial @AdbArgs 2>&1
    if($LASTEXITCODE -ne 0){throw "ADB failed: $($AdbArgs -join ' ')"}
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
    @{path=$Path;sha256=$Hash;signature=$signature;manifest=$manifest;badging=$badging}
}
function Assert-Installed([string]$Package,[string]$Hash){
    $paths=Invoke-Adb @('shell','pm','path',$Package)
    if($paths -cnotmatch '^package:(/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk)$'){throw 'Unexpected installed paths'}
    $digest=Invoke-Adb @('shell','sha256sum',$Matches[1])
    if(($digest -split '\s+')[0] -cne $Hash){throw 'Installed hash mismatch'}
}
$script:seenPids=@()
$script:snapshotHash=$null
$script:keyHash=$null
function Run-Phase([string]$Phase,[string]$Method,[string]$AppHash){
    Assert-Target
    Assert-Installed $labPackage $AppHash
    Assert-Installed $testPackage $TestApkHash
    Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
    $result=Invoke-Adb @('shell','am','instrument','-w','-r','-e','storageLab','synthetic-only',
        '-e','class',"com.example.data.local.UpgradePreservationAndroidLabTest#$Method",
        "$testPackage/androidx.test.runner.AndroidJUnitRunner")
    $result | Set-Content (Join-Path $OutputDirectory "$Phase.txt")
    if($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'){throw "Failed $Phase; preserve state"}
    $pidMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_pid=(\d+)\r?$')
    $snapshotMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_snapshot_sha256=([a-f0-9]{64})\r?$')
    $keyMatch=[regex]::Match($result,'(?m)^INSTRUMENTATION_STATUS: upgrade_key_metadata_sha256=([a-f0-9]{64})\r?$')
    if(!$pidMatch.Success -or !$snapshotMatch.Success -or !$keyMatch.Success){throw 'Missing phase evidence'}
    $processId=$pidMatch.Groups[1].Value
    if($processId -in $script:seenPids){throw 'Process reused'}
    $script:seenPids += $processId
    if($script:snapshotHash -and $script:snapshotHash -cne $snapshotMatch.Groups[1].Value){throw 'Snapshot changed'}
    if($script:keyHash -and $script:keyHash -cne $keyMatch.Groups[1].Value){throw 'Key metadata changed'}
    $script:snapshotHash=$snapshotMatch.Groups[1].Value
    $script:keyHash=$keyMatch.Groups[1].Value
    Assert-Installed $labPackage $AppHash
    Assert-Installed $testPackage $TestApkHash
}
Assert-Target
if(Test-Path -LiteralPath $OutputDirectory){throw 'Existing evidence preserved'}
$old=Inspect-Apk $PreviousAppApk $labPackage $oldHash
$new=Inspect-Apk $AppApk $labPackage $newHash
$test=Inspect-Apk $TestApk $testPackage $TestApkHash
if(($old.manifest -replace '\(line=\d+\)','(line)') -cne ($new.manifest -replace '\(line=\d+\)','(line)')){throw 'Isolated manifest changed'}
if(Invoke-Adb @('shell','pm','list','packages',$labPackage)){throw 'Fresh laboratory required; preserve all existing packages'}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$record=[ordered]@{started=(Get-Date -Format o);result='IN_PROGRESS';phase='install-previous';harnessHead=$head;
    oldHead='5c80c1b22c3c4c2d5ac20a2fda017ae1aa019de1';newHead='466d79a8ef0229adb72f36f56a9743a5db99960f';
    oldAppSha256=$oldHash;newAppSha256=$newHash;testSha256=$TestApkHash;certificate=$certificate;serial=$Serial;avd=$ExpectedAvd;
    versionCodeBefore=1;versionCodeAfter=1;schemaBefore=7;schemaAfter=7;pilotReady=$false}
foreach($entry in @(@('previous',$old),@('current',$new),@('test',$test))){
    foreach($kind in @('manifest','signature','badging')){ $entry[1][$kind] | Set-Content (Join-Path $OutputDirectory "$($entry[0])-$kind.txt") }
}
try {
    foreach($apk in @($PreviousAppApk,$TestApk)){
        $installed=Invoke-Adb @('install','-t',$apk)
        $installed | Add-Content (Join-Path $OutputDirectory 'initial-install.txt')
        if($installed -notmatch '(?m)^Success\r?$'){throw 'Install unsuccessful'}
    }
    $record.phase='seed-previous'
    Run-Phase '01-seed-previous' 'seedPreviousVersion' $oldHash
    $record.phase='reopen-previous'
    Run-Phase '02-reopen-previous' 'verifyExistingStateInNewProcess' $oldHash
    $record.phase='replace-app'
    Assert-Target
    Assert-Installed $labPackage $oldHash
    if((Hash-File $AppApk) -cne $newHash){throw 'New APK changed'}
    $replace=Invoke-Adb @('install','-r','-t',$AppApk)
    $replace | Set-Content (Join-Path $OutputDirectory 'replace-app.txt')
    if($replace -notmatch '(?m)^Success\r?$'){throw 'Replacement unsuccessful'}
    $record.phase='verify-updated'
    Run-Phase '03-verify-updated' 'verifyExistingStateInNewProcess' $newHash
    $record.phase='force-stop-reopen-updated'
    Run-Phase '04-force-stop-reopen-updated' 'verifyExistingStateInNewProcess' $newHash
    if((Hash-File $PreviousAppApk) -cne $oldHash -or (Hash-File $AppApk) -cne $newHash -or (Hash-File $TestApk) -cne $TestApkHash){throw 'Inputs changed'}
    if((& git -C $repo rev-parse HEAD).Trim() -cne $head -or (& git -C $repo status --porcelain=v1)){throw 'Harness source changed'}
    $record.result='PASS'
} catch { $record.result='FAIL'; $record.error=$_.Exception.Message; throw }
finally {
    $record.ended=Get-Date -Format o
    $record.processIds=$script:seenPids
    $record.snapshotSha256=$script:snapshotHash
    $record.keyMetadataSha256=$script:keyHash
    $record | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $OutputDirectory 'result.json')
}
