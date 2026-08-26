# CLM POC 本机运行手册（Windows 11，无 Docker）

所有运行时都在 `D:\dev\clm\runtime\` 下以便携方式运行；不安装 Windows 服务。

## 目录

```
D:\dev\clm\
├── README.md                 # 原始技术方案交接文档（macOS 视角）
├── docs/                     # 本手册、实施规格、交付说明
├── sql/                      # 01 BPM 业务表 / 02 CLM 表 / 03 菜单+字典（可重复执行）
├── scripts/                  # create-bpm-model.ps1（部署审批流程模型）、e2e-api.ps1（纵向闭环 API 测试）
├── infra/                    # env.ps1（工具链与环境变量）、start-*.ps1、build-backend.ps1、init-db.ps1、my.ini、maven-settings.xml、.env（不提交）
├── backend/                  # ruoyi-vue-pro v2026.07(jdk17/21) 克隆，分支 clm-poc，新增 yudao-module-clm
├── frontend/                 # yudao-ui-admin-vue3 v2026.07 克隆，分支 clm-poc，新增 src/views/clm 等
└── runtime/                  # tools/（JDK 由 winget 安装在 Program Files；Maven/MySQL/Redis 便携包）、data/、logs/、tmp/、downloads/（不提交）
```

## 一次性准备（已完成）

| 组件 | 位置 | 备注 |
|---|---|---|
| JDK 17.0.20 (Temurin) | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot` | `winget install EclipseAdoptium.Temurin.17.JDK` |
| Maven 3.9.9 | `runtime\tools\apache-maven-3.9.9` | 镜像 `infra\maven-settings.xml`（阿里云） |
| MySQL 8.0.33 | `runtime\tools\mysql-8.0.33-winx64`，数据 `runtime\data\mysql`，配置 `infra\my.ini` | root 密码在 `infra\.env`（`CLM_DB_PASSWORD`） |
| Redis 7.4.2 | `runtime\tools\redis\Redis-7.4.2-Windows-x64-msys2` | 仅 127.0.0.1:6379，无持久化 |
| Node 24 / pnpm 10 | 系统已有 | 前端依赖已 `pnpm install --frozen-lockfile` |

## 日常启动顺序

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\infra\start-mysql.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\infra\start-redis.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\infra\start-backend.ps1     # 需先 build-backend.ps1 生成 jar；端口 48080
powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\infra\start-frontend.ps1    # Vite dev，端口 3000
```

- 后端日志：`runtime\logs\backend.log`；前端：`runtime\logs\frontend.log`。
- 停后端：`infra\stop-backend.ps1`。MySQL/Redis 用任务管理器结束 `mysqld.exe` / `redis-server.exe` 即可（或 `Stop-Process -Name mysqld`）。
- 登录：`http://127.0.0.1:3000`，租户 `芋道源码`，账号 `admin / admin123`（上游默认；勿用于非本机环境）。

## 重新构建后端

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\infra\build-backend.ps1      # mvn -pl yudao-server -am clean package -DskipTests
```

仅跑 CLM 模块单测：

```powershell
. D:\dev\clm\infra\env.ps1; cd D:\dev\clm\backend
cmd /c 'mvn.cmd -s D:\dev\clm\infra\maven-settings.xml -B -pl yudao-module-clm test "-DargLine=-Djdk.net.unixdomain.tmpdir=D:\dev\clm\runtime\tmp -Dfile.encoding=UTF-8"'
```

## 重建数据库（本机可丢弃库）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\dev\clm\infra\init-db.ps1 -Force
```

依次导入：上游基础 SQL（含 DROP TABLE）→ `sql\01_bpm_tables.sql` → `sql\02_clm_tables.sql` → `sql\03_clm_menus_dicts.sql`。Flowable 的 `ACT_*`/`FLW_*` 由后端首次启动自动建表。之后需重新执行 `scripts\create-bpm-model.ps1` 部署审批流程模型。

## 本机两个已知坑（已在脚本中处理）

1. **JDK 17 `Pipe.open()` 失败**（`Unable to establish loopback connection`）：本机上 Windows 版 PipeImpl 优先用 AF_UNIX socket，且默认用户 Temp 目录下的 socket 文件无法 connect。所有 JVM 都必须加 `-Djdk.net.unixdomain.tmpdir=D:\dev\clm\runtime\tmp`（`env.ps1` 的 `MAVEN_OPTS`、`start-backend.ps1` 已包含；surefire 需通过 `-DargLine` 传入）。
2. **PowerShell 5.1 会拆分带点的参数**：`-h127.0.0.1` 会变成 `-h127`，`-Dmaven.javadoc.skip=true` 会报 "Unknown lifecycle phase"。脚本统一使用 `--host=` 形式，并通过 `cmd /c` 调用 Maven。
