# TuriX 合同管理系统（AI-CLM）

基于 [RuoYi-Vue-Pro](https://github.com/YunaiV/ruoyi-vue-pro) 二次开发的合同管理系统。一诺复刻第一版范围锁定为 **起草 → 法务协同 → 审批 → APPROVED 精确修订**；不包含用章、签署、归档、履约、变更、续签或关闭。

> 2026-08-29 菜单与术语已按一诺原词完成重排并通过自动化与真实浏览器验收，入口为“合同拟制、审批管理、模板管理、基础设置、基础数据、工作交接事项、异常处理、系统管理”。本轮不改变对象、权限、API 或一期范围。

## 功能特性

### 业务工作区 W01-W06

- W01 工作台按当前角色聚合待提交、待协同、待审批和我发起的任务，不展示无权限入口。
- W02 支持已发布标准模板起草和非标文件上传。草稿不占永久编号，第一次成功提交时分配并在驳回、撤回、重提后复用。
- W03 合同查询覆盖本人经办、参与、审批和授权组织范围，含保存筛选、审批进度和仅负责人可见的已删除草稿墓碑。
- W04 以不可覆盖的修订和文档版本为中心，包含参与方、重大承诺、本地 AI 审查、在线编辑、审计和提交校验。
- W05 法务协同绑定精确修订；非标、正文变化或高风险合同必须先取得当前修订的有效法务结论。
- W06 审批任务绑定实际审阅修订，支持受控轻微/重大编辑、退回、拒绝、同意和跨实例历史；重大编辑废弃旧审批单并重走全流程。

### 合同配置与系统 G01-G10 / S01-S03

- 用户从“审批管理 → 工作流设置 → 流程定义 / 业务单据流程配置”维护合同流程，从“基础设置 → 合同分类 / 页面布局配置 / 编码规则设置 / 数据权限规则”维护合同配置；底层版本对象和 API 不改。
- “页面布局配置”只维护合同分类草稿版本的合同表单，不是通用低代码页面或任意对象表单设计器。
- 模板、流程、编码和数据权限政策均版本化发布；相对方支持查重、合并和 Excel 预览确认导入。
- 配置异常、审批异常和经办人变更均有独立权限。G09 将合同管理员的业务确认与系统管理员的技术重放分开，两者都不能手改审批结果。
- 四个产品角色为业务、法务、合同管理员和系统管理员。系统管理员固定 `NONE/NONE` 合同范围，不能查看合同正文。
- 未配置外部 AI 或钉钉凭据时，系统分别使用确定性本地 AI 和 `NOT_CONFIGURED` 沙箱记录，不伪造外部调用成功。

### 版本、权限与审计

- 合同正文、结构化字段、参与方和重大承诺都进入不可变修订快照；提交、协同结论和审批决定引用精确 `revisionId`。
- 合同级访问同时受对象参与关系、角色能力和组织+合同类型范围约束；菜单可见不等于正文可见。
- 追加式审计覆盖创建、修改、上传、在线保存、法务协同、提交、审批、授权、导入、交接和系统沙箱。

### 当前验收基线（2026-08-29）

- CLM 后端单测 `144/144`；演示数据首轮 `39/39`、第二轮全部 `REUSE`；一诺第一版专项 E2E `54/54` 并完成最终清理；前端 `ts:check`、`build:local` 和定向 ESLint 全部通过。
- SQL13 在临时数据库连续执行两次通过；业务、法务、合同管理员、系统管理员菜单授权数分别为 `37/31/66/44`，重复菜单、permission/component 漂移和可见孤儿节点均为 `0`。
- 真实浏览器已验证合同管理员完整信息架构、流程定义列表/编辑器、页面布局配置和业务单据流程配置；业务、法务无配置菜单；系统管理员只显示审批管理、异常处理、系统管理，直接读取合同正文返回业务码 `403`；旧深链跳转保留原 query/hash。

### 在线编辑（ONLYOFFICE，真实 Gate 已通过）
- 编辑器配置 + JWT 签名、令牌保护且撤权即失效的文件拉取、同源受限保存回调、回调幂等生成新版本、审批中禁止新开编辑、历史版本只读/并排查看
- Apple Silicon + Docker Desktop 上已用固定 digest 的 ONLYOFFICE Community 8.2 验证中文 DOCX、保存、两人实时协作、历史只读和撤权；详见 `docs/DELIVERY.md`

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 · Spring Boot 3.5 · MyBatis-Plus · Flowable（BPM）· Redis · MySQL 8 |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · FormCreate · bpmn-js · docx-preview |
| 底座 | RuoYi-Vue-Pro v2026.07（用户/部门/角色/菜单/租户/字典/BPM 全部复用） |
| 业务模块 | 自研 `yudao-module-clm`（合同域全部业务）|

## 目录结构

```
├── backend/     后端（RuoYi-Vue-Pro + yudao-module-clm 合同模块）
├── frontend/    前端（yudao-ui-admin-vue3 + src/{api,views}/clm 合同界面）
├── sql/         普通迁移 01-13；bootstrap/ 仅用于带确认门槛的本地空库重建
├── scripts/     TuriX 本地重置、流程模型部署；E2E/演示种数仅按需显式执行
├── infra/       本地环境脚本与配置（Docker Compose + Windows PowerShell + macOS env loader）
└── docs/        设计规格、操作指南、演示脚本、需求覆盖分析等全部文档
```

## 快速开始

**依赖**：JDK 17 · Maven 3.9+ · Docker Desktop · Node ≥ 20.19 · pnpm ≥ 8.6 · PowerShell 7 · Python 3（生成 DOCX 需 `python-docx`）。

1. `cp infra/.env.example infra/.env`，替换数据库密码和 ONLYOFFICE JWT secret；`infra/.env` 已被 Git 忽略。
2. 启动依赖：`docker compose --env-file infra/.env -f infra/compose.yaml -f infra/compose.onlyoffice.yaml up -d`。
3. 确认后端端口 48080 未监听，然后执行 `pwsh -NoProfile -File scripts/reset-turix-local.ps1 -ConfirmDestructive`。脚本只面向本项目本地环境：重建数据库、精确执行 SQL 01-13、应用 TuriX 空基线、清 Redis，并同步重建 ONLYOFFICE 缓存卷。
4. 首次安装本地 SNAPSHOT 依赖：`cd backend && mvn -pl yudao-module-clm -am install -DskipTests`；再执行 `mvn -pl yudao-server -am clean package -DskipTests`。
5. 回到仓库根目录执行 `. infra/env.sh`，运行 `java -jar backend/yudao-server/target/yudao-server.jar --spring.profiles.active=local,clm-local`（端口 48080）。
6. 后端健康后执行 `pwsh -NoProfile -File scripts/create-bpm-model.ps1`，部署 TuriX 默认合同审批配置。脚本会把法务和负责人 ID 参数化，不写入合同或演示业务数据。
7. 前端：`cd frontend && pnpm install --frozen-lockfile && pnpm dev`（端口 3000）。
8. `gen_demo_docs.py`、`seed-*.ps1` 和 `e2e-*.ps1` 均为显式测试/演示工具，不属于客户空基线，不应在正式初始化后自动执行。

登录 `http://127.0.0.1:3000`，租户为 `TuriX`，登录页不预填账号密码。本地初始密码均为 `admin123`：产品账号为 `business`、`legal`、`contractadmin`、`systemadmin`；`admin` 仅作为平台维护账号。生产部署必须立即轮换密码。

> Windows 一键脚本见 `infra/`，运行手册见 `docs/RUNBOOK_WINDOWS.md`；本轮 macOS 的实测状态和边界见 `docs/DELIVERY.md`。核心 `scripts/*.ps1` 建议统一使用 PowerShell 7；路径、临时目录和 Docker/MySQL 客户端会按平台解析。

### 验证

```bash
cd backend
mvn -pl yudao-module-clm test
cd ../frontend
pnpm build:local
cd ..

pwsh -NoProfile -File scripts/e2e-yinuo-v1.ps1
pwsh -NoProfile -File scripts/e2e-api.ps1

# 回调模拟器需要独占配置中的 Document Server 端口；真实容器运行时先临时停止它
docker stop clm-onlyoffice
pwsh -NoProfile -File scripts/e2e-onlyoffice-callback.ps1
docker start clm-onlyoffice

pwsh -NoProfile -File scripts/cleanup-e2e-data.ps1
```

E2E 会临时写入测试前缀数据。执行后必须运行对应清理，并在交付前再次执行 TuriX 空基线重置与空表验证，不能把测试中间态当作客户数据。历史演示验证证据见 [`docs/yinuo-replica/demo-baseline.md`](docs/yinuo-replica/demo-baseline.md)。

### 完全重置为 TuriX 客户空基线

> **破坏性操作**：以下脚本会永久删除本项目本地 `ruoyi-vue-pro` 数据库中的所有租户和业务数据，并清理 Redis 与 ONLYOFFICE 缓存。只可用于可丢弃的 AI-CLM 本机环境。脚本会拒绝在 48080 仍有监听时运行；macOS launchd 必须先 `bootout`，不能只杀进程后让它自动拉起。

```bash
launchctl bootout "gui/$(id -u)" "$HOME/Library/LaunchAgents/com.turix.aiclm.backend.plist" 2>/dev/null || true
pwsh -NoProfile -File scripts/reset-turix-local.ps1 -ConfirmDestructive
```

随后重新启动后端（让 Flowable 创建引擎元数据），再部署默认合同审批配置。`sql/bootstrap/turix_empty_baseline.sql` 绝不能加入普通升级通配；`cleanup-e2e-data.ps1` 只清测试前缀，不能替代客户空基线重置。

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/USER_GUIDE.md` | 界面操作指南（建签约方/设计表单/配审批流/合同全流程） |
| `docs/DEMO_GUIDE.md` | 15 分钟演示动线与演示账号 |
| `docs/CLM_POC_SPEC.md` | 核心 API 契约、数据模型、状态机、权限规则 |
| `docs/CLM_DEMO_SPEC.md` | 起草中心/详情页/归档/范本等演示版能力的契约 |
| `docs/CLM_ONLYOFFICE_SPEC.md` | 在线编辑设计与 Document Server 验收清单 |
| `docs/DELIVERY.md` | 交付说明（验证证据、已知问题） |
| `docs/REQUIREMENTS_COVERAGE.md` | 客户需求覆盖度分析 |
| `docs/YINUO_REFERENCE.md` | 一诺产品功能与界面梳理（对标参考） |
| `docs/UPSTREAM_HANDOVER.md` | 原始立项方案与锁定决策 |
| `docs/yinuo-replica/prd.md` | 一诺第一版对象、角色、流程、42 项功能和 20 个主页面 |
| `docs/yinuo-replica/api-v1.md` | 第一版 API、状态与权限契约 |
| `docs/yinuo-replica/demo-baseline.md` | 幂等演示数据与当前终验证据 |

## 许可与合规说明

- 上游 RuoYi-Vue-Pro 主仓库为 MIT 协议；本仓库 `sql/01_bpm_tables.sql` 的 BPM 业务表 DDL 系依据 MIT 许可的实体类自行推导（官方 BPM SQL 有单独的授权条件，商用交付前请自行确认授权或评估替换 Warm-Flow）
- ONLYOFFICE Document Server 社区版为 AGPLv3，正式商用前请单独评估其义务
- `backend/sql/mysql/ruoyi-vue-pro.sql` 中上游示例云凭据已替换为占位符；上游其他数据库方言的 SQL dump（DM/Oracle/PG 等）未纳入本仓库，需要时从上游获取
