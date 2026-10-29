param(
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$ApkAnalyzer
)
$ErrorActionPreference = 'Stop'
$manifestText = (& $ApkAnalyzer manifest print $Apk 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Unable to read compiled APK manifest' }
[xml]$manifest = $manifestText
$android = 'http://schemas.android.com/apk/res/android'
$root = $manifest.DocumentElement
$app = $root.SelectSingleNode('application')
function Assert-Lab([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw "Unsafe BLE lab APK: $Reason" }
}
Assert-Lab ($root.GetAttribute('package') -ceq 'com.aistudio.hbandhealthtech.pxq97m.blelab') 'wrong package'
Assert-Lab (-not $root.HasAttribute('sharedUserId', $android)) 'shared UID'
$permissions = @($root.SelectNodes('uses-permission | uses-permission-sdk-23') | ForEach-Object { $_.GetAttribute('name', $android) })
Assert-Lab ('android.permission.INTERNET' -notin $permissions) 'INTERNET permission present'
Assert-Lab ('android.permission.BLUETOOTH_SCAN' -in $permissions) 'BLE scan missing'
Assert-Lab ('android.permission.BLUETOOTH_CONNECT' -in $permissions) 'BLE connect missing'
Assert-Lab ($app.GetAttribute('debuggable', $android) -ceq 'true') 'not a debug build'
Assert-Lab ($app.GetAttribute('allowBackup', $android) -ceq 'false') 'backup enabled'
Assert-Lab ($app.GetAttribute('name', $android) -ceq 'com.example.HBandHealthSyncApp') 'wrong Application'
Assert-Lab ($app.GetAttribute('label', $android) -ceq 'VE30 Ensaio') 'missing lab label'
foreach ($component in @('com.example.MainActivity', 'com.example.data.hband.HBandBleService', 'com.inuker.bluetooth.library.BluetoothService')) {
    $node = @($app.SelectNodes('activity | service') | Where-Object { $_.GetAttribute('name', $android) -ceq $component })
    Assert-Lab ($node.Count -eq 1) "missing component: $component"
    Assert-Lab ($node[0].GetAttribute('enabled', $android) -cne 'false') "disabled component: $component"
}
$boot = @($app.SelectNodes('receiver') | Where-Object { $_.GetAttribute('name', $android) -ceq 'com.example.data.hband.HBandBootReceiver' })
Assert-Lab ($boot.Count -eq 1 -and $boot[0].GetAttribute('enabled', $android) -ceq 'false') 'boot session enabled'
$providers = @($app.SelectNodes('provider'))
Assert-Lab (-not ($providers | Where-Object { $_.GetAttribute('name', $android) -ceq 'com.google.firebase.provider.FirebaseInitProvider' })) 'Firebase initializer present'
$fileProvider = @($providers | Where-Object { $_.GetAttribute('name', $android) -ceq 'androidx.core.content.FileProvider' })
Assert-Lab ($fileProvider.Count -eq 1 -and $fileProvider[0].GetAttribute('authorities', $android) -ceq 'com.aistudio.hbandhealthtech.pxq97m.blelab.fileprovider') 'shared provider authority'
[ordered]@{
    result = 'PASS'; apkSha256 = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
    package = $root.GetAttribute('package'); internet = $false; bluetooth = $true
    scope = 'Compiled manifest only; does not install, access a device or prove clinical accuracy.'
} | ConvertTo-Json
