. "$PSScriptRoot\env.ps1"
$ini = "$PSScriptRoot\my.ini"
$data = "D:\dev\clm\runtime\data\mysql"
if (-not (Test-Path "$data\mysql")) {
  Write-Host "Initializing MySQL data dir (insecure root, password set afterwards)..."
  & mysqld.exe --defaults-file="$ini" --initialize-insecure --console
}
$running = Get-Process mysqld -ErrorAction SilentlyContinue
if ($running) { Write-Host "mysqld already running (pid $($running.Id))"; exit 0 }
Start-Process -FilePath "$env:MYSQL_HOME\bin\mysqld.exe" -ArgumentList "--defaults-file=`"$ini`"" -WindowStyle Hidden -RedirectStandardOutput "D:\dev\clm\runtime\logs\mysqld-stdout.log" -RedirectStandardError "D:\dev\clm\runtime\logs\mysqld-stderr.log"
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
  Start-Sleep -Seconds 1
  & mysqladmin.exe --host=127.0.0.1 --port=3306 --user=root ping 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { $ok = $true; break }
  & mysqladmin.exe --host=127.0.0.1 --port=3306 --user=root --password="$env:CLM_DB_PASSWORD" ping 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) { $ok = $true; break }
}
if (-not $ok) { Write-Error "mysqld did not come up"; exit 1 }
# set root password if still empty
& mysql.exe --host=127.0.0.1 --user=root -e "SELECT 1" 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
  Write-Host "Setting root password from infra/.env"
  & mysql.exe --host=127.0.0.1 --user=root -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$env:CLM_DB_PASSWORD'; CREATE DATABASE IF NOT EXISTS ``ruoyi-vue-pro`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; FLUSH PRIVILEGES;"
}
Write-Host "MySQL is up on 127.0.0.1:3306"

