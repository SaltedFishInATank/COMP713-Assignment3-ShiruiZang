param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PayaraHome = (Join-Path $PSScriptRoot '../../.work/tools/payara6'),
    [string]$Broker = 'localhost:7676',
    [string]$EventId,
    [string]$Data,
    [string]$EvidenceDirectory = (Join-Path $PSScriptRoot '../evidence/broker')
)
$ErrorActionPreference = 'Stop'
if (-not $Data) { $Data = Join-Path $PSScriptRoot ("../consumer/data/test-" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$consumer = Join-Path $PSScriptRoot '../consumer/Run-Consumer.ps1'
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
$common = @{JavaHome=$JavaHome; PayaraHome=$PayaraHome; Broker=$Broker; Data=$Data}
if ($EventId) { $common.EventId = $EventId }
Write-Host 'Requires an actual task.submitted event already queued by the deployed Payara backend.'
$retry = @(& $consumer @common -FailOnce -Limit 1 -IdleSeconds 30 2>&1)
$retry | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'retry.log')
if (($retry -join "`n") -notmatch 'RETRY_ROLLBACK' -or ($retry -join "`n") -notmatch 'redelivered=true' -or
    ($retry -join "`n") -notmatch 'NOTIFICATION_CREATED' -or ($retry -join "`n") -notmatch 'ACK_COMMIT') {
    throw 'Retry test did not receive, roll back, redeliver and acknowledge a real event. Inspect retry.log.'
}
$acknowledged = [regex]::Match(($retry -join "`n"), 'ACK_COMMIT eventId=(\S+)')
$eventId = $acknowledged.Groups[1].Value
$common.EventId = $eventId
$before = @(& $consumer @common -Status)
$before | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'before-duplicate.log')
& $consumer @common -ReplayLast | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'replay.log')
$duplicate = @(& $consumer @common -Limit 1 -IdleSeconds 30 2>&1)
$duplicate | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'duplicate.log')
if (($duplicate -join "`n") -notmatch 'DUPLICATE_SUPPRESSED' -or ($duplicate -join "`n") -notmatch 'ACK_COMMIT') {
    throw 'Duplicate replay was not suppressed and acknowledged. Inspect duplicate.log.'
}
$after = @(& $consumer @common -Status)
$after | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'after-duplicate.log')
$beforeCount = $before | Where-Object { $_ -match '^PERSISTED_NOTIFICATIONS ' }
$afterCount = $after | Where-Object { $_ -match '^PERSISTED_NOTIFICATIONS ' }
if ($beforeCount -ne $afterCount) { throw 'Duplicate replay changed the business notification count.' }
$result = [ordered]@{ testedAt=(Get-Date -Format o); broker=$Broker; data=[IO.Path]::GetFullPath($Data)
    eventId=$eventId; retry='passed'; redelivery='passed'; acknowledgement='passed'; duplicateSuppression='passed'; persistence='passed'; count=$afterCount }
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'result.json')
$after
Write-Host "Broker checks passed. Evidence: $EvidenceDirectory"
