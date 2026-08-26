# AI 开发交接文档（下一会话在 macOS 上运行）

> 读者：接手本项目的 AI 开发会话。仓库：`https://github.com/Rilfoyle/AI-CLM`（master）。
> 上一会话在 Windows 上完成了从零到可演示版的全部工作；**你在 macOS 上工作**，本文告诉你：项目现状、哪些东西可以直接用、哪些是 Windows 专属需要换做法、以及不能推翻的决策。
> 产品功能概览见根 `README.md`；本文只讲开发视角。

---

## 1. 项目现状（截至 2026-08-25，全部实测验证）

- **核心闭环可演示**：融合起草中心（模板/上传/复制/续签）→ 合同草稿 → 不可变正文版本（SHA-256）→ 提交审批（binding 绑定准确版本与表单快照）→ BPM 两级审批（通过/驳回/撤销/重提）→ 上传盖章件归档 → 已签订。
- **界面**：首页=合同工作台（6 个任务计数桶+3 面板）；详情页文档为中心（docx-preview 直渲正文+阶段条+右侧五页签）；台账状态页签；角色化瘦身菜单（首页/审批中心/合同管理/流程配置/系统管理/基础设施）；品牌 TuriX。
- **质量基线**：后端单测 41 项全绿；`scripts/e2e-api.ps1` 57 项断言、`scripts/e2e-onlyoffice-callback.ps1` 61 项断言全部通过（Windows 上的最后一次运行）；前端 vue-tsc/eslint/prettier 对 clm 代码零错误，`pnpm build:local` 通过。
- **演示数据**：由脚本走真实 API 生成（6 类型+范本 DOCX、12 签约方、19 份合同铺满生命周期、时间回拨）。**注意：数据在上一台机器的本地 MySQL 里，不在仓库中** —— 你需要在 Mac 上重新初始化并重灌（见 §4）。
- **ONLYOFFICE**：适配代码完成（令牌保护的开放接口、幂等回调），仅缺真实 Document Server 验收 —— **Mac 上如果有 Docker，这是你最值得先做的事**（`infra/compose.onlyoffice.yaml` + `docs/CLM_ONLYOFFICE_SPEC.md §6`）。

## 2. 代码结构与关键决策（勿推翻）

结构：`backend/`（ruoyi-vue-pro v2026.07(jdk17/21) @ec3f7cbf + 自建 `yudao-module-clm`）、`frontend/`（yudao-ui-admin-vue3 v2026.07 @d4b521a1 + `src/{api,views}/clm`）、`sql/01..07`（按序可重放）、`scripts/`、`docs/`。上游版本已锁定，不要跟 master。

锁定决策（依据 `docs/UPSTREAM_HANDOVER.md` 与实施过程）：
1. 合同是第一业务轴，无"合同申请"对象；不做电子签；不碰 `crm_contract`；不建通用低代码平台。
2. 双状态轴：`approval_status`(0 未提交/1 审批中/2 通过/3 驳回/4 取消，与 BPM 实例状态同值) ⊥ `lifecycle_status`(1 草稿/2 审批通过/3 已签订/4 到期/5 终止/6 作废)。
3. `clm_workflow_binding` 一次流程一条，**businessKey = binding id**（非合同 id）；监听器 `ClmProcessInstanceStatusListener` 按 businessKey+processInstanceId 双校验，不限定流程 key。
4. 文档版本永不覆盖，提交审批即冻结并复核 SHA-256；内容存 CLM 私有表 `clm_document_blob`（**不要**改走 infra 的 `@PermitAll` 文件路由——那是公开可匿名读的，且上游默认主存储配置是坏的占位 S3）。
5. 合同级 ACL：负责人/参与人/流程参与人，**super_admin 无旁路**；列表过滤必须在 Mapper SQL 层。
6. 约定：错误码段 `1_070_*`、菜单 id 7000-7999、字典 `clm_*`；所有新 DO 继承 `TenantBaseDO` 且新表必须带 `tenant_id`（上游租户拦截器默认拦截所有已映射表）。
7. API 契约在 `docs/CLM_POC_SPEC.md` + `docs/CLM_DEMO_SPEC.md`，改接口先改契约。

二开必读：`docs/upstream-maps/`（上一会话对上游代码约定的深度梳理：模块规范/BPM 集成/文件存储/前端约定/菜单权限种子）。

## 3. Windows 专属 vs 通用（对你最重要的一节）

**通用（直接可用）**：全部 Java/Vue 源码、`sql/*.sql`、`scripts/gen_demo_docs.py`（`pip install python-docx`）、`infra/compose.onlyoffice.yaml`、`infra/maven-settings.xml`（阿里云镜像，国内网络建议 `mvn -s` 挂上）、所有 docs。

**Windows 专属（Mac 上不要直接跑，按下述处理）**：
| 文件 | 作用 | Mac 上的做法 |
|---|---|---|
| `infra/env.ps1` | 设 JAVA_HOME/PATH + 从 `infra/.env` 读环境变量 | 用 shell export 替代（见 §4 模板） |
| `infra/start-mysql.ps1` / `start-redis.ps1` | 便携版 MySQL/Redis 启动 | `brew services start mysql@8.0 redis` 或 docker compose（`docs/UPSTREAM_HANDOVER.md §5.2` 有现成 compose.yaml 内容） |
| `infra/init-db.ps1` | 建库 + 按序导入 上游基础 SQL → `sql/01..07` | 手动 `mysql < file` 同序执行即可 |
| `infra/build-backend.ps1` / `start-backend.ps1` / `start-frontend.ps1` | mvn 打包 / 启动 jar / 启动 vite | 直接跑等价 mvn/java/pnpm 命令（README 快速开始有） |
| `scripts/create-bpm-model.ps1` | 登录后创建+发布 SIMPLE 审批流程模型（幂等；模型 JSON 在 `scripts/clm_contract_approval_v1.model.json`） | 首选 `brew install powershell` 用 pwsh 跑（见下方 pwsh 注意事项）；或照脚本逻辑用 curl/python 重写（就 4 个接口：login → model/list → create|update → deploy） |
| `scripts/seed-demo-data.ps1` / `e2e-api.ps1` / `e2e-onlyoffice-callback.ps1` / `cleanup-e2e-data.ps1` | 演示数据 / 端到端测试 | 同上：pwsh 可跑大部分，需修两处 Windows 痕迹：① `cmd /c "...mysql.exe ... < file"` 改为直接调 `mysql`；② `. "$PSScriptRoot\..\infra\env.ps1"`/`$env:MYSQL_HOME` 相关路径改为 Mac 的 mysql 客户端路径。建议接手后先把这四个脚本移植成 bash 或 python 并回提交 |

**Windows 坑在 Mac 上的适用性**：
- JDK17 `Pipe.open()` 需要 `-Djdk.net.unixdomain.tmpdir=...` —— **仅 Windows 该机器的问题，Mac 不需要**（backend 启动参数里如果你看到这个 flag，是无害的，可不带）。
- PowerShell 5.1 拆分带点参数、无 BOM 中文按 GBK —— pwsh 7 无此问题，但脚本保留 BOM 无害。
- curl 内联中文 JSON 乱码 —— 那是 git-bash/Win 编码问题；Mac 的 curl 正常，但保险起见登录体仍可走 `--data-binary @file`。
- **Mac 专属注意**：`docs/UPSTREAM_HANDOVER.md §5` 本来就是 macOS 视角写的初始搭建指南（brew 装 JDK/Maven、docker compose 起 MySQL/Redis），可直接参考；Apple Silicon 上 MySQL 镜像用 `mysql:8.0.33`（arm64 有官方镜像）。

## 4. Mac 环境搭建（建议路径）

```bash
brew install openjdk@17 maven mysql@8.0 redis pnpm python@3.12   # 或 MySQL/Redis 走 docker
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 环境变量（等价于 Windows 的 infra/.env，此文件不入库需自建）
export CLM_DB_PASSWORD='<你设置的root密码>'
export CLM_DB_USERNAME=root
export CLM_DB_URL='jdbc:mysql://127.0.0.1:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true'
export CLM_REDIS_HOST=127.0.0.1
export CLM_REDIS_PORT=6379
# 可选（在线编辑）：CLM_ONLYOFFICE_ENABLED/URL/CALLBACK_BASE/JWT_SECRET

mysql -uroot -p -e "CREATE DATABASE \`ruoyi-vue-pro\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
for f in backend/sql/mysql/ruoyi-vue-pro.sql sql/01_bpm_tables.sql sql/02_clm_tables.sql \
         sql/03_clm_menus_dicts.sql sql/04_menu_trim.sql sql/05_menu_redesign.sql \
         sql/06_demo_upgrade.sql sql/07_process_menu_optimize.sql; do
  mysql -uroot -p"$CLM_DB_PASSWORD" --default-character-set=utf8mb4 ruoyi-vue-pro < "$f"
done

cd backend && mvn -s ../infra/maven-settings.xml -pl yudao-server -am clean package -DskipTests
java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local,clm-local   # 48080
cd ../frontend && pnpm install --frozen-lockfile && pnpm dev                              # 3000

# 流程模型 + 演示数据（pwsh 或按 §3 移植）
brew install powershell
pwsh scripts/create-bpm-model.ps1
pip3 install python-docx && python3 scripts/gen_demo_docs.py
pwsh scripts/seed-demo-data.ps1        # 注意先按 §3 修掉 cmd /c 与 mysql.exe 路径
```

登录 `http://127.0.0.1:3000`：租户 `芋道源码`；`admin/admin123`（终审）、`yudao/admin123`（法务一审）、`test/admin123`（无权用户，演示权限隔离用）。验证健康：`mvn -pl yudao-module-clm test`（应 41 项全绿）→ 跑通 `e2e-api` 移植版。

## 5. 待办（按优先级）

1. **ONLYOFFICE Gate**（Mac+Docker 即可做）：`docker compose -f infra/compose.onlyoffice.yaml up -d`，按 `docs/CLM_ONLYOFFICE_SPEC.md §6` 验收真实 DOCX 打开/保存回调/并发/越权；注意 callback-base-url 用 `host.docker.internal`。
2. **BPM 建表 SQL 授权决策**：`sql/01` 是从 MIT 实体类自行推导的 DDL；商用交付前需向上游确认授权或评估换 Warm-Flow（唯一法务阻塞，见 `docs/DELIVERY.md §6.2`）。
3. **客户需求 🟡 批**（约一周，清单与方案在 `docs/REQUIREMENTS_COVERAGE.md`）：合同编码拼我方简称+提交时生成、到期/归档提醒（Quartz+站内信）、相对方 Excel 批量导入、归档 UI 完善、重大承诺必填配置化。
4. **🔴 批需先和用户对齐再动**：钉钉对接（凭证/主数据归属/免登范围）、审批节点可编辑（与不可变审计有冲突，需字段级方案）。
5. 工程债：`ContractServiceImpl`/`ContractWorkflowServiceImpl` 单测、E2E 接 CI、脚本 bash 化（§3）、生产化安全加固（默认口令、开放接口网络限制）。

## 6. 本地仓库形态说明（重要）

上一台开发机上 `backend/`、`frontend/` 是**独立 git 克隆**（分支 `clm-poc`，保留上游历史便于 diff/升级）；本仓库收录的是它们的**工作区快照**（无上游 .git）。你在 Mac 上克隆本仓库后是单仓库形态，直接改直接提交即可；若将来要升级上游版本，可重新克隆上游对比迁移（固定 commit 见 §2）。另外：`backend/sql/mysql/ruoyi-vue-pro.sql` 中上游演示云凭据已替换为占位符、其他数据库方言的 dump 未入库（GitHub 推送保护会拦截）——如需从上游重新引入这类文件，先做同样的净化。
