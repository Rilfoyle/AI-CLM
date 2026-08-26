. "$PSScriptRoot\env.ps1"
Get-NetTCPConnection -State Listen -LocalPort 48080 -ErrorAction SilentlyContinue | ForEach-Object { Write-Host "stopping pid $($_.OwningProcess)"; Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
