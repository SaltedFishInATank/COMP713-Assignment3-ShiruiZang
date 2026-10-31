param([string]$JavaHome=$env:JAVA_HOME,[string]$MavenHome=$env:MAVEN_HOME,[string]$MavenRepository)
$ErrorActionPreference='Stop'
if(!$JavaHome -and (Test-Path -LiteralPath 'C:\Program Files\Android\openjdk\jdk-21.0.8')){$JavaHome='C:\Program Files\Android\openjdk\jdk-21.0.8'}
if(!$MavenHome -and (Test-Path -LiteralPath 'C:\Program Files\NetBeans-25\netbeans\java\maven')){$MavenHome='C:\Program Files\NetBeans-25\netbeans\java\maven'}
if(!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))){throw 'Supply -JavaHome for JDK21.'}
$env:JAVA_HOME=$JavaHome
$maven=if($MavenHome){Join-Path $MavenHome 'bin\mvn.cmd'}else{'mvn'}
$projectRoot=Split-Path $PSScriptRoot -Parent
$argsList=@('-B','-ntp','-f',(Join-Path $projectRoot 'backend\pom.xml'),'clean','verify')
if($MavenRepository){$argsList+=('-Dmaven.repo.local='+$MavenRepository)}
& $maven @argsList
if($LASTEXITCODE -ne 0){throw 'Backend Maven verification failed.'}
Get-Item -LiteralPath (Join-Path $projectRoot 'backend\target\comp713-a3.war')
