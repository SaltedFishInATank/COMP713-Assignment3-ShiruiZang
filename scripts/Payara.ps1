param([ValidateSet('start','stop','deploy','status')][string]$Action='status',[string]$PayaraHome=$env:PAYARA_HOME,[string]$JavaHome=$env:JAVA_HOME,[string]$Domain='domain1',[int]$AdminPort=4848)
$ErrorActionPreference='Stop'
if(!$JavaHome -and (Test-Path -LiteralPath 'C:\Program Files\Android\openjdk\jdk-21.0.8')){$JavaHome='C:\Program Files\Android\openjdk\jdk-21.0.8'}
if(!$PayaraHome){$candidate=Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) '.work\tools\payara6';if(Test-Path -LiteralPath $candidate){$PayaraHome=$candidate}}
if(!$PayaraHome -or !$JavaHome){throw 'Supply -PayaraHome and -JavaHome (JDK21).'}
$cli=Join-Path $PayaraHome 'glassfish\lib\client\appserver-cli.jar';$java=Join-Path $JavaHome 'bin\java.exe';$env:JAVA_HOME=$JavaHome
switch($Action){
 'start' {& $java -jar $cli start-domain $Domain}
 'stop' {# Payara Tools starts Java directly; a CLI PID file may be absent. Use its real admin stop endpoint.
  Invoke-RestMethod -Uri ('http://127.0.0.1:'+$AdminPort+'/management/domain/stop') -Method Post -Headers @{'X-Requested-By'='comp713-a3';Accept='application/json'}
 }
 'deploy' {$war=Join-Path (Split-Path $PSScriptRoot -Parent) 'backend\target\comp713-a3.war';if(!(Test-Path -LiteralPath $war)){throw 'Build first.'};& $java -jar $cli --port $AdminPort deploy --force=true --name comp713-a3 --contextroot comp713-a3 $war}
 'status' {& $java -jar $cli --port $AdminPort list-applications}
}
if($Action -ne 'stop' -and $LASTEXITCODE -ne 0){throw ('Payara '+$Action+' failed.')}
