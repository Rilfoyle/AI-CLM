# TuriX 合同管理系统（AI-CLM）

企业级合同全生命周期管理系统（CLM），覆盖 **起草 → 审批 → 签订归档 → 履约跟踪** 的核心链路，基于 [RuoYi-Vue-Pro](https://github.com/YunaiV/ruoyi-vue-pro)（Spring Boot 3 单体 + Vue 3 管理端）二次开发，产品形态对标甄零一诺（OneContract）。

## 功能特性

### 合同工作台
- 登录首页即工作台：待办审批 / 草稿 / 审批中 / 已驳回 / 待归档 / 已签订 六个任务计数桶，点击直达对应清单
- 我的待办、我负责的合同、最近审批记录三个面板，一屏掌握全部在办事项

### 融合起草中心（四种建合同方式）
- **模板起草**：从带范本的合同类型中选择，范本 DOCX 自动生成合同正文 v1
- **上传起草**：填写合同要素后上传线下拟好的文件作为正文
- **复制已有合同**：复用历史合同的要素、签约方与正文
- **合同续签**：对已签订合同一键生成续签草稿，自动建立"续签自"关联

### 合同管理
- 合同台账：状态页签（草稿/审批中/审批通过/已签订/已驳回）+ 多条件筛选，复制/续签关系可视
- 合同详情**以文档为中心**：左侧正文在线预览（DOCX 浏览器直渲、PDF/图片内嵌），右侧信息面板（基础信息/文档版本/参与人/审批记录/审计轨迹），顶部生命周期阶段条
- **文档版本不可覆盖**：每次上传/在线保存生成新版本，SHA-256 校验和全程可查，提交审批即冻结
- 归档定稿：审批通过后上传线下盖章件、登记签订/生效/到期日期，合同转入"已签订"

### 审批流程
- 审批中心：待办 / 已办 / 我的流程 / 抄送我的
- 画布式流程设计器（仿钉钉 SIMPLE 模式 + BPMN 模式，基于 Flowable 7/8）：审批人策略（指定成员/部门负责人/岗位/用户组/发起人自选/表达式）、条件分支（按金额/部门/合同类型路由）、或签/会签
- 审批与合同精确绑定：审批实例锁定提交时的正文版本与表单快照，驳回重提生成新记录、历史完整保留
- 审批任务页内嵌合同快照，审批人可直接查看要素、签约方并下载绑定正文

### 表单与合同类型
- 合同类型**版本化**管理：FormCreate 拖拽设计器配置扩展字段，发布后不可变，修改走新版本；历史合同永远按创建时的版本展示
- 扩展字段服务端校验（必填、未知字段拦截，绕过前端也拦得住）
- 合同类型可绑定范本文件与专属审批流程

### 权限与审计
- 合同级数据权限：仅 负责人 / 被授权参与人 / 流程参与人 可见，**管理员无旁路**；列表过滤在服务端 SQL 层完成
- 参与人授权（查看/编辑/下载/管理）实时生效
- 追加式审计轨迹：创建、修改、上传、下载、提审、审批结果、授权变更、归档、在线编辑全程留痕

### 在线编辑（ONLYOFFICE，代码就绪）
- 编辑器配置 + JWT 签名、令牌保护的文件拉取与保存回调、回调幂等生成新版本、审批中禁止编辑、版本并排查看
- 需自行部署 ONLYOFFICE Document Server（`infra/compose.onlyoffice.yaml`），链路已通过回调模拟测试验证

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
├── sql/         数据库脚本（01-07 按序执行：BPM 表 / CLM 表 / 菜单字典 / 菜单定制 / 演示升级）
├── scripts/     流程模型部署、E2E 测试、演示数据生成
├── infra/       本地环境脚本与配置（Windows PowerShell；含 ONLYOFFICE compose）
└── docs/        设计规格、操作指南、演示脚本、需求覆盖分析等全部文档
```

## 快速开始

**依赖**：JDK 17 · Maven 3.9+ · MySQL 8.0 · Redis · Node ≥ 20 · pnpm。

1. 建库并按序导入 SQL：上游基础库 `backend/sql/mysql/ruoyi-vue-pro.sql` → `sql/01` ~ `sql/07`（Flowable 的 `ACT_*` 表由后端首次启动自动创建）
2. 配置数据库连接：环境变量 `CLM_DB_URL / CLM_DB_USERNAME / CLM_DB_PASSWORD / CLM_REDIS_HOST / CLM_REDIS_PORT`（由 `backend/yudao-server/src/main/resources/application-clm-local.yaml` 读取）
3. 启动后端：`mvn -pl yudao-server -am clean package -DskipTests` 后运行 `yudao-server.jar --spring.profiles.active=local,clm-local`（端口 48080）
4. 启动前端：`cd frontend && pnpm install --frozen-lockfile && pnpm dev`（端口 3000）
5. 部署审批流程模型：运行 `scripts/create-bpm-model.ps1`（或参照其内容调用 `/admin-api/bpm/model` 接口）
6. 可选，生成演示数据：`python scripts/gen_demo_docs.py` + `scripts/seed-demo-data.ps1`

登录 `http://127.0.0.1:3000`：租户 `芋道源码`，账号 `admin / admin123`。

> Windows 一键脚本见 `infra/`（start-mysql / start-redis / build-backend / start-backend / start-frontend / init-db），运行手册见 `docs/RUNBOOK_WINDOWS.md`；macOS 初始搭建可参照 `docs/UPSTREAM_HANDOVER.md` §5（brew / docker compose）。`scripts/*.ps1` 为 PowerShell 脚本，非 Windows 环境可用 pwsh 运行或按其逻辑改写。

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

## 许可与合规说明

- 上游 RuoYi-Vue-Pro 主仓库为 MIT 协议；本仓库 `sql/01_bpm_tables.sql` 的 BPM 业务表 DDL 系依据 MIT 许可的实体类自行推导（官方 BPM SQL 有单独的授权条件，商用交付前请自行确认授权或评估替换 Warm-Flow）
- ONLYOFFICE Document Server 社区版为 AGPLv3，正式商用前请单独评估其义务
- `backend/sql/mysql/ruoyi-vue-pro.sql` 中上游示例云凭据已替换为占位符；上游其他数据库方言的 SQL dump（DM/Oracle/PG 等）未纳入本仓库，需要时从上游获取
