param([string]$JavaHome=$env:JAVA_HOME,[string]$PayaraHome=$env:PAYARA_HOME)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if(!$JavaHome -and (Test-Path -LiteralPath 'C:\Program Files\Android\openjdk\jdk-21.0.8')){$JavaHome='C:\Program Files\Android\openjdk\jdk-21.0.8'}
if(!$PayaraHome){$candidate=Join-Path (Split-Path $root -Parent) '.work\tools\payara6';if(Test-Path -LiteralPath $candidate){$PayaraHome=$candidate}}
if(!$JavaHome -or !$PayaraHome){throw 'Supply JavaHome(JDK21) and PayaraHome.'}
$consumer=Join-Path $root 'consumer';$runtime=Join-Path $consumer 'data';New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$jar=Join-Path $consumer 'target\task-notification-consumer.jar';if(!(Test-Path -LiteralPath $jar)){throw 'Build the consumer with Maven first.'}
$cp=@($jar,(Join-Path $consumer 'target\lib\*'),(Join-Path $PayaraHome 'mq\lib\imq.jar'),(Join-Path $PayaraHome 'mq\lib\jms.jar')) -join ';'
$data=Join-Path $runtime 'notifications'
$argsList=@('-cp',('"'+$cp+'"'),'nz.ac.aut.comp713.consumer.NotificationConsumer','--data',('"'+$data+'"'),'--broker','localhost:7676')
$p=Start-Process -FilePath (Join-Path $JavaHome 'bin\java.exe') -ArgumentList $argsList -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtime 'consumer-running.log') -RedirectStandardError (Join-Path $runtime 'consumer-error.log') -PassThru
[ordered]@{pid=$p.Id;startedAt=(Get-Date -Format o);data=$data;output=(Join-Path $runtime 'consumer-running.log');stopCommand=('Stop-Process -Id '+$p.Id)} | ConvertTo-Json | Tee-Object -FilePath (Join-Path $runtime 'runtime.json')
