param([string]$Profiles = 'local,clm-local')
. "$PSScriptRoot\env.ps1"
$jar = 'D:\dev\clm\backend\yudao-server\target\yudao-server.jar'
if (-not (Test-Path $jar)) { Write-Error "jar not found: $jar (run build-backend.ps1)"; exit 1 }
$existing = Get-NetTCPConnection -State Listen -LocalPort 48080 -ErrorAction SilentlyContinue
if ($existing) { Write-Host "port 48080 already in use by pid $($existing.OwningProcess)"; exit 0 }
$log = 'D:\dev\clm\runtime\logs\backend.log'
$p = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList @('-Dfile.encoding=UTF-8','-Djdk.net.unixdomain.tmpdir=D:\dev\clm\runtime\tmp','-Djava.io.tmpdir=D:\dev\clm\runtime\tmp','-Xms512m','-Xmx2g','-jar',"`"$jar`"","--spring.profiles.active=$Profiles") -WorkingDirectory 'D:\dev\clm\backend' -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError 'D:\dev\clm\runtime\logs\backend-err.log' -PassThru
Write-Host "backend pid=$($p.Id), log=$log"

