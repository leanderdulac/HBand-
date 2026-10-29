param(
    [Parameter(Mandatory)][string]$SdkRoot,
    [Parameter(Mandatory)][string]$AppApk,
    [Parameter(Mandatory)][string]$TestApk,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$Serial='emulator-5588'
$ExpectedAvd='Next2U_Receipt_Lab_20260925'
$labPackage='com.aistudio.hbandhealthtech.pxq97m.storagelab'
$testPackage="$labPackage.test"
$adb=Join-Path $SdkRoot 'platform-tools/adb.exe'
$aapt=Join-Path $SdkRoot 'build-tools/36.0.0/aapt.exe'
$repo=Split-Path $PSScriptRoot -Parent
$head=(& git -C $repo rev-parse HEAD).Trim()
if($LASTEXITCODE -ne 0 -or (& git -C $repo status --porcelain=v1)){throw 'Clean committed source required'}
function Invoke-Adb([string[]]$AdbArgs) {
    $result=& $adb -s $Serial @AdbArgs 2>&1
    if($LASTEXITCODE -ne 0){throw "ADB failed: $($AdbArgs[0])"}
    return ($result -join "`n").Trim()
}
function Assert-Hash([string]$Package,[string]$Expected) {
    $location=Invoke-Adb @('shell','pm','path',$Package)
    $match=[regex]::Match($location,'^package:(/data/app/[A-Za-z0-9/_+=.~\-]+/base\.apk)$')
    if(!$match.Success){throw 'Unexpected installed APK path'}
    $digest=Invoke-Adb @('shell','sha256sum',$match.Groups[1].Value)
    if(($digest -split '\s+')[0] -cne $Expected){throw 'Installed artifact mismatch'}
}
if((Invoke-Adb @('get-state')) -ne 'device'){throw 'Not ready'}
if((Invoke-Adb @('shell','getprop','ro.kernel.qemu')) -ne '1'){throw 'Not emulator'}
if((Invoke-Adb @('shell','getprop','sys.boot_completed')) -ne '1'){throw 'Boot incomplete'}
if(((Invoke-Adb @('emu','avd','name')) -split '\r?\n')[0].Trim() -cne $ExpectedAvd){throw 'Wrong AVD'}
foreach($spec in @(@($AppApk,$labPackage),@($TestApk,$testPackage))){
    $badging=(& $aapt dump badging $spec[0] 2>&1) -join "`n"
    if($LASTEXITCODE -ne 0){throw 'Cannot inspect APK'}
    $match=[regex]::Match($badging,"(?m)^package: name='([^']+)'")
    if(!$match.Success -or $match.Groups[1].Value -cne $spec[1]){throw 'Non-lab package refused'}
    if(Invoke-Adb @('shell','pm','list','packages',$spec[1])){throw 'Existing package preserved; fresh receipt AVD required'}
}
$permissions=(& $aapt dump permissions $AppApk 2>&1) -join "`n"
if($LASTEXITCODE -ne 0 -or $permissions -match 'android.permission.INTERNET'){throw 'Network permission refused'}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Existing evidence preserved'}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
$appHash=(Get-FileHash -LiteralPath $AppApk).Hash.ToLowerInvariant()
$testHash=(Get-FileHash -LiteralPath $TestApk).Hash.ToLowerInvariant()
$started=Get-Date -Format o
$permissions | Set-Content (Join-Path $OutputDirectory 'app-permissions.txt')
Invoke-Adb @('install','-t',$AppApk) | Set-Content (Join-Path $OutputDirectory 'install-app.txt')
Invoke-Adb @('install','-t',$TestApk) | Set-Content (Join-Path $OutputDirectory 'install-test.txt')
Assert-Hash $labPackage $appHash
Assert-Hash $testPackage $testHash
$class='com.example.data.local.ReceiptRecoveryAndroidLabTest'
$interruptions=@()
foreach($point in @('before-first','before-second')){
    Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
    $cut=& $adb -s $Serial shell am instrument -w -r -e storageLab synthetic-only -e receiptCutPoint $point -e class "$class#interruptReceiptPersistence" "$testPackage/androidx.test.runner.AndroidJUnitRunner" 2>&1
    $cutExit=$LASTEXITCODE
    $cutText=$cut -join "`n"
    $cutText | Set-Content (Join-Path $OutputDirectory "$point-interrupted.txt")
    if($cutExit -notin @(0,1) -or $cutText -notmatch 'Process crashed'){throw 'Expected isolated termination missing'}
    foreach($phase in @('recoverReceiptsInNewProcess','reopenRecoveredReceipts')){
        Invoke-Adb @('shell','am','force-stop',$labPackage) | Out-Null
        $result=Invoke-Adb @('shell','am','instrument','-w','-r','-e','storageLab','synthetic-only',
            '-e','receiptCutPoint',$point,'-e','class',"$class#$phase","$testPackage/androidx.test.runner.AndroidJUnitRunner")
        $result | Set-Content (Join-Path $OutputDirectory "$point-$phase.txt")
        if($result -notmatch 'OK \(1 test\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'){
            throw "Receipt phase failed: $point/$phase; preserve database and evidence"
        }
    }
    $interruptions+=@{point=$point; terminationObserved=$true; exit=$cutExit; recovery='PASS'; secondProcessReopen='PASS'}
}
if((Get-FileHash -LiteralPath $AppApk).Hash.ToLowerInvariant() -cne $appHash -or
   (Get-FileHash -LiteralPath $TestApk).Hash.ToLowerInvariant() -cne $testHash){throw 'Input artifacts changed'}
Assert-Hash $labPackage $appHash
Assert-Hash $testPackage $testHash
if((& git -C $repo rev-parse HEAD).Trim() -cne $head -or (& git -C $repo status --porcelain=v1)){throw 'Source changed'}
[ordered]@{head=$head; started=$started; ended=(Get-Date -Format o); serial=$Serial; avd=$ExpectedAvd;
    appSha256=$appHash; testSha256=$testHash; installedHashesVerifiedBeforeAndAfter=$true;
    android=(Invoke-Adb @('shell','getprop','ro.build.version.release')); api=(Invoke-Adb @('shell','getprop','ro.build.version.sdk'));
    fingerprint=(Invoke-Adb @('shell','getprop','ro.build.fingerprint')); tests=4; failures=0; expectedInterruptions=$interruptions;
    scope='SQLCipher/Keystore in synthetic Android emulator; fake transport and assumed remote acceptance. No backend deduplication or pilot proof.'
} | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDirectory 'result.json')
Write-Output 'PASS: four recovery/reopen tests; two intentional terminations verified separately; data conserved.'
