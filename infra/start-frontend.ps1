. "$PSScriptRoot\env.ps1"
$existing = Get-NetTCPConnection -State Listen -LocalPort 3000 -ErrorAction SilentlyContinue
if ($existing) { Write-Host "port 3000 already in use by pid $($existing.OwningProcess)"; exit 0 }
$log = 'D:\dev\clm\runtime\logs\frontend.log'
$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c','pnpm exec vite --mode env.local --port 3000 --host 127.0.0.1' -WorkingDirectory 'D:\dev\clm\frontend' -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError 'D:\dev\clm\runtime\logs\frontend-err.log' -PassThru
Write-Host "frontend pid=$($p.Id), log=$log"
