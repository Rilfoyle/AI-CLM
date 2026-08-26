. "$PSScriptRoot\env.ps1"
$running = Get-Process redis-server -ErrorAction SilentlyContinue
if ($running) { Write-Host "redis-server already running (pid $($running.Id))"; exit 0 }
Start-Process -FilePath "$env:REDIS_HOME\redis-server.exe" -ArgumentList "--port 6379 --bind 127.0.0.1 --dir D:/dev/clm/runtime/data/redis --save `"`"" -WindowStyle Hidden -RedirectStandardOutput "D:\dev\clm\runtime\logs\redis-stdout.log" -RedirectStandardError "D:\dev\clm\runtime\logs\redis-stderr.log"
for ($i = 0; $i -lt 20; $i++) { Start-Sleep -Milliseconds 500; $t = New-Object Net.Sockets.TcpClient; try { $t.Connect('127.0.0.1',6379); $ok=$t.Connected } catch { $ok=$false } finally { $t.Close() }; if ($ok) { Write-Host "Redis is up on 127.0.0.1:6379"; exit 0 } }
Write-Error "redis did not come up"; exit 1

