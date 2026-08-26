. "$PSScriptRoot\env.ps1"
Get-Process mysqld,redis-server,java -ErrorAction SilentlyContinue | Select-Object Id, ProcessName, StartTime
