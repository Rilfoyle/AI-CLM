# Dot-source this file:  . D:\dev\clm\infra\env.ps1
$ClmRoot = 'D:\dev\clm'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:MAVEN_HOME = "$ClmRoot\runtime\tools\apache-maven-3.9.9"
$env:MYSQL_HOME = "$ClmRoot\runtime\tools\mysql-8.0.33-winx64"
$env:REDIS_HOME = "$ClmRoot\runtime\tools\redis\Redis-7.4.2-Windows-x64-msys2"
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:MYSQL_HOME\bin;$env:REDIS_HOME;$env:PATH"
$env:MAVEN_OPTS = '-Xmx2g -Dfile.encoding=UTF-8 -Djdk.net.unixdomain.tmpdir=D:\dev\clm\runtime\tmp'
# Windows: JDK17 NIO Pipe uses AF_UNIX sockets under the temp dir; the default user Temp breaks connect() on this box.
$env:CLM_JVM_OPTS = '-Djdk.net.unixdomain.tmpdir=D:\dev\clm\runtime\tmp -Djava.io.tmpdir=D:\dev\clm\runtime\tmp'
New-Item -ItemType Directory -Force D:\dev\clm\runtime\tmp | Out-Null
# load .env (KEY=VALUE lines) into process env
Get-Content "$ClmRoot\infra\.env" | Where-Object { $_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$' } | ForEach-Object {
  $k = $Matches[1]; $v = $Matches[2].Trim()
  Set-Item -Path "Env:$k" -Value $v
}


