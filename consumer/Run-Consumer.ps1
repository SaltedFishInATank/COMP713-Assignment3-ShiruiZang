param(
    [string]$PayaraHome = (Join-Path $PSScriptRoot '../../.work/tools/payara6'),
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Data = (Join-Path $PSScriptRoot 'data/notifications'),
    [string]$Broker = 'localhost:7676',
    [string]$Queue = 'TaskEventsQueue',
    [string]$User = 'guest',
    [string]$EventId,
    [int]$Limit = 0,
    [int]$IdleSeconds = 0,
    [switch]$FailOnce,
    [switch]$FailAfterStore,
    [switch]$Status,
    [switch]$ReplayLast
)
$ErrorActionPreference = 'Stop'
if ($JavaHome) { $java = Join-Path $JavaHome 'bin/java.exe' } else { $java = 'java' }
$client = Join-Path $PayaraHome 'mq/lib/imq.jar'
$api = Join-Path $PayaraHome 'mq/lib/jms.jar'
$application = Join-Path $PSScriptRoot 'target/task-notification-consumer.jar'
foreach ($file in @($client, $api, $application)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Missing $file. Build with Maven and set PayaraHome to the selected Payara installation." }
}
$classpath = @($application, (Join-Path $PSScriptRoot 'target/lib/*'), $client, $api) -join [IO.Path]::PathSeparator
$ConsumerArguments = @('--data', $Data, '--broker', $Broker, '--queue', $Queue, '--user', $User,
    '--limit', $Limit.ToString(), '--idle-seconds', $IdleSeconds.ToString())
if ($FailOnce) { $ConsumerArguments += '--fail-once' }
if ($FailAfterStore) { $ConsumerArguments += '--fail-after-store' }
if ($Status) { $ConsumerArguments += '--status' }
if ($ReplayLast) { $ConsumerArguments += '--replay-last' }
if ($EventId) { $ConsumerArguments += @('--event-id', $EventId) }
& $java '-cp' $classpath 'nz.ac.aut.comp713.consumer.NotificationConsumer' @ConsumerArguments
if ($LASTEXITCODE -ne 0) { throw "Consumer failed with exit code $LASTEXITCODE" }
