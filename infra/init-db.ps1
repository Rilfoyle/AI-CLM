# (Re)initialize the local POC database from scratch.
# WARNING: the official base SQL contains DROP TABLE statements — only run against the disposable local clm MySQL.
param([switch]$Force)
. "$PSScriptRoot\env.ps1"
$mysql = "$env:MYSQL_HOME\bin\mysql.exe"
$db = 'ruoyi-vue-pro'
if (-not $Force) {
  Write-Host "This will DROP and re-create all tables in database '$db' on 127.0.0.1:3306. Re-run with -Force to proceed."
  exit 1
}
& $mysql --host=127.0.0.1 --user=root --password="$env:CLM_DB_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS ``$db`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>&1 | Where-Object { $_ -notmatch 'Warning' }
$files = @(
  'D:\dev\clm\backend\sql\mysql\ruoyi-vue-pro.sql',   # upstream base (system/infra), includes DROP TABLE
  'D:\dev\clm\sql\01_bpm_tables.sql',                  # bpm_* business tables (derived from entity classes) + category seed
  'D:\dev\clm\sql\02_clm_tables.sql',                  # clm_* tables
  'D:\dev\clm\sql\03_clm_menus_dicts.sql',             # menus / role grants / dicts
  'D:\dev\clm\sql\04_menu_trim.sql',                   # hide non-CLM module menus
  'D:\dev\clm\sql\05_menu_redesign.sql',               # contract-focused menu tree
  'D:\dev\clm\sql\06_demo_upgrade.sql',                # relation/template columns, lifecycle labels, draft-center menu
  'D:\dev\clm\sql\07_process_menu_optimize.sql'        # process menus: design vs monitoring split, clearer names
)
foreach ($f in $files) {
  Write-Host "importing $f"
  cmd /c "`"$mysql`" --host=127.0.0.1 --user=root --password=$env:CLM_DB_PASSWORD --default-character-set=utf8mb4 $db < `"$f`"" 2>&1 | Where-Object { $_ -notmatch 'Warning' }
  if ($LASTEXITCODE -ne 0) { Write-Error "import failed: $f"; exit 1 }
}
# Flowable ACT_* / FLW_* tables are created automatically by the backend on first start (flowable.database-schema-update=true).
& $mysql --host=127.0.0.1 --user=root --password="$env:CLM_DB_PASSWORD" -e "SELECT COUNT(*) AS tables_total FROM information_schema.tables WHERE table_schema='$db';" 2>&1 | Where-Object { $_ -notmatch 'Warning' }
Write-Host "done"
