param([string]$Modules = 'yudao-server', [switch]$NoClean)
. "$PSScriptRoot\env.ps1"
Set-Location D:\dev\clm\backend
$goal = if ($NoClean) { 'package' } else { 'clean package' }
$cmd = "`"$env:MAVEN_HOME\bin\mvn.cmd`" -s `"D:\dev\clm\infra\maven-settings.xml`" -B -pl $Modules -am $goal `"-DskipTests`" `"-Dmaven.test.skip=true`" `"-Dmaven.javadoc.skip=true`""
Write-Host $cmd
cmd /c $cmd
exit $LASTEXITCODE
