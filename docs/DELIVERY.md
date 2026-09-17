# CLM POC 交付说明（更新于 2026-08-29）

> 目标：对标甄零一诺的合同系统 POC，验证 RuoYi-Vue-Pro 作为底座能否低成本提供 **流程引擎 + 表单编辑 + 管理底座 + 文档在线编辑** 四个核心点。
> 本文只记录**实际做到并验证过**的内容；未验证的部分明确标注。

## 1. 一句话结论

在 Windows 完成基础纵向闭环后，又在 Apple Silicon macOS + Docker Desktop 上完成独立复现与真实 ONLYOFFICE Gate。当前 RuoYi-Vue-Pro v2026.07 单体 + 自建 `yudao-module-clm` 已跑通：
**合同类型（FormCreate 扩展字段）发布 → 直接创建合同草稿 → 上传不可变正文版本（SHA-256）→ 提交审批（绑定准确版本）→ RuoYi BPM 两级审批通过/驳回 → CLM 监听回写状态 → 合同级 ACL 与受控下载 → 审计轨迹**。
2026-08-26 POC 基线中，`scripts/e2e-api.ps1` 57 项检查和 `scripts/e2e-onlyoffice-callback.ps1` 61 项检查全部通过；固定 digest 的 ONLYOFFICE Community 8.2 已真实验证中文 DOCX 打开/保存、两用户实时协作、历史只读、撤权令牌立即失效及 forcesave→close 幂等。2026-08-28 增量已补上合同域流程定义列表/画布（当时菜单称“审批方案”）、合同类型表单配置与审批态动态字段。2026-08-29 菜单与术语对齐已完成自动化和真实浏览器验收；最终证据见 §5。

2026-08-29 用户侧菜单已按一诺原词重排为“合同拟制、审批管理、模板管理、基础设置、基础数据、工作交接事项、异常处理、系统管理”，旧菜单名“审批方案”已改为“审批管理 → 工作流设置 → 流程定义”。这轮不改变 API、权限键、数据库业务表、Flowable 事实或第一期边界，当前状态为**已完成 / 已验收**。

## 2. 四个核心点的落地情况

| 核心点 | 落地方式 | 验证 |
|---|---|---|
| 流程引擎（画布式编排） | RuoYi BPM（Flowable 8）继续作为流程事实源；用户从“流程定义”进入 CLM 合同域画布，只允许维护 `clm_contract_*` SIMPLE 模型，可保存草稿、发布新版本；“业务单据流程配置”仍独立负责新提交选流，运行中实例不迁移 | 真实浏览器已验证合同管理员的流程定义列表/编辑器和业务单据流程配置；业务、法务无配置菜单，系统管理员不获得合同配置入口；旧深链保留 query/hash |
| 表单编辑 | 复用前端 FormCreate 设计器；合同类型 **版本化** schema（草稿可编辑、发布后不可变、新建草稿版本复制）；流程定义不复制 schema。用户可从“基础设置 → 页面布局配置”直达同一合同表单能力，新建、编辑和审批页均按合同绑定的 `type_version_id` 加载动态字段，审批中编辑再受节点 `customData` 政策控制 | 服务端 Gate、前端检查和真实浏览器验收通过；合同管理员已验证页面布局配置动线。“页面布局配置”不是通用低代码或任意对象表单设计器 |
| 管理系统底座 | RuoYi 用户/部门/角色/菜单/租户/字典原样复用；CLM 使用 7000 段菜单/按钮权限，SQL12 保留流程画布权限，SQL13 迁移一诺菜单层级和父菜单授权 | SQL13 在临时数据库连续执行两次通过；四角色授权为 `37/31/66/44`，重复菜单、permission/component 漂移和可见孤儿节点均为 `0` |
| 文档在线编辑 | ONLYOFFICE 适配：编辑器配置 + JWT、撤权即失效的文件令牌、同源/无重定向/限流式大小的保存回调、不可变新版本、OOXML 逻辑包幂等、历史只读/并排查看（明确不是内容级比较） | Community 8.2 真实 Gate：4 页中文 DOCX 打开/保存；admin+yudao 显示 2 位协作者；历史 v1 只读；撤权后旧 file token 立即拒绝；forcesave 后 close 不重复建版本；回调模拟 61 项通过 |

2026-08-29 目标 canonical 路径如下；旧完整路径只允许作为隐藏跳转别名，不能生成第二个业务页面：

| 菜单 | canonical 路径 |
|---|---|
| 合同拟制 → 合同起草 / 合同查询 / 合同协同 | `/clm/drafting/draft-center`、`/clm/drafting/contract`、`/clm/drafting/collaboration` |
| 审批管理 → 审批中心 | `/clm/approval-management/approval` |
| 审批管理 → 工作流设置 → 流程定义 / 业务单据流程配置 | `/clm/approval-management/workflow-settings/process`、`/clm/approval-management/workflow-settings/routing` |
| 模板管理 | `/clm/template` |
| 基础设置 → 合同分类 / 页面布局配置 / 编码规则设置 / 数据权限规则 | `/clm/base-settings/contract-type`、`/clm/base-settings/page-layout`、`/clm/base-settings/numbering`、`/clm/base-settings/permission` |
| 基础数据 → 相对方信息 / 相对方导入 | `/clm/basic-data/directory`、`/clm/basic-data/import` |
| 工作交接事项 → 经办人变更 | `/clm/handover/owner-change` |
| 异常处理 → 配置异常 / 审批异常 | `/clm/exceptions/issues`、`/clm/exceptions/reconciliation` |
| 系统管理 | `/clm/system/*` |

## 3. 目录与关键文件

Windows 见 `docs/RUNBOOK_WINDOWS.md`；macOS/Linux 见 README 快速开始和 `infra/compose*.yaml`。代码：
- 后端 `backend/yudao-module-clm/`：`controller/admin/*`、`service/*`、`access/ContractAccessService`（对象级权限）、`workflow/ClmProcessInstanceStatusListener`（BPM 事件回写）、`document/ClmDocumentStorage`（私有 blob 存储）、`onlyoffice/*`。
- 前端 `frontend/src/views/clm/**`、`frontend/src/api/clm/**`，路由 `src/router/modules/remaining.ts`（`/clm` 组），字典 `src/utils/dict.ts`（`CLM_*`）。
- SQL `sql/01_bpm_tables.sql`（BPM 业务表 + 流程分类）、`sql/02_clm_tables.sql`（11 张 clm 表）、`sql/03_clm_menus_dicts.sql`（基础菜单/角色授权/字典）、`sql/12_yinuo_v1_process_designer.sql`（合同域流程画布权限）和 `sql/13_yinuo_menu_alignment.sql`（一诺菜单与术语迁移）。
- 规格 `docs/CLM_POC_SPEC.md`（API 契约、状态机、权限规则）、`docs/CLM_ONLYOFFICE_SPEC.md`。

## 4. 与 README 锁定决策的对照

| README 决策 | 状态 |
|---|---|
| 不建"合同申请"对象；发起即建 `clm_contract` 草稿 | ✅ |
| 不做电子签 | ✅ 未接任何签署能力 |
| RuoYi-Vue-Pro 单体 | ✅ |
| 独立 `yudao-module-clm`，不碰 `crm_contract` | ✅ |
| 主字段 + JSON 扩展字段，类型版本化 | ✅ `form_conf/form_fields` 与 `bpm_form` 同构，直接复用前端工具函数 |
| 业务表单接 BPM，流程变量只传标量 | ✅ `contractId/bindingId/amount/ownerDeptId/contractTypeCode/contractTitle` |
| 文档版本不可覆盖；审批绑定准确版本 + 校验和 | ✅ 提交时冻结并复核 SHA-256；binding 记录 `document_version_id + checksum` |
| 权限落到服务端 | ✅ 列表在 Mapper 层过滤；详情/下载/编辑/管理逐一对象校验；**无超级管理员旁路**（super_admin 对未授权合同同样不可见，E2E 已验证） |
| businessKey = binding id | ✅ |
| 文件保护方案 | 采用 README 13.1 方案 2：CLM 私有存储 `clm_document_blob`，不走 infra 的 `@PermitAll` 下载路由（infra 默认主存储配置本身是占位 S3，上传也不可用） |

## 5. 验证证据

- macOS 基线：JDK 17.0.20.1、Maven 3.9.16、Node 23.11.0、pnpm 11.24.0、PowerShell 7.6.5；MySQL 8.0.33、Redis 7、ONLYOFFICE Community 8.2 三个容器健康。
- BPM Gate：基础 SQL + `sql/01`~`07` 导入；Flowable 表由后端首次启动创建；`scripts/create-bpm-model.ps1` 发布 `clm_contract_approval_v1` 两节点流程。
- 后端回调安全基线：下载对整个响应体设置 60 秒可取消 deadline，并在流式读取阶段限制 50 MiB；当前 CLM 单测与 package 结果以下方 2026-08-28 最终回归为准。
- 纵向闭环：`scripts/e2e-api.ps1` → `ALL CHECKS PASSED`（日志 `runtime/logs/e2e-api-run*.log`）。覆盖 README §16 用例 1–11。
- 在线编辑回调链路：`scripts/e2e-onlyoffice-callback.ps1` → `ALL CHECKS PASSED`（61 项，日志 `runtime/logs/e2e-onlyoffice-final.log`）。
- 前端生产构建：`pnpm build:local` 成功；CLM 路径 eslint/prettier 通过。
- 当前后端最终回归：`mvn -pl yudao-module-clm test` → `Tests run: 144, Failures: 0, Errors: 0`（`144/144`），CLM 后端 package 通过。
- 当前前端最终回归：`pnpm ts:check`、`pnpm build:local` 与本轮变更文件定向 ESLint 全部通过。
- 当前数据与核心闭环：`scripts/seed-demo-data.ps1` 首轮 `39/39`、第二轮全部 `REUSE`；`scripts/e2e-yinuo-v1.ps1` → `54/54`，脚本完成最终清理，测试污染为 `0`。
- 2026-08-28 真实浏览器历史验收：合同管理员已完成当时名为“审批方案”的列表/画布/连续草稿保存、合同类型版本与表单设计器验收；真实审批任务已完成绑定类型版本动态字段的只读展示。按 `customData` 政策编辑、非重大变更续行和重大变更重启由 `scripts/e2e-yinuo-v1.ps1` 验证。
- 2026-08-29 SQL13 验收：在临时数据库连续执行两次通过；业务、法务、合同管理员、系统管理员菜单授权数为 `37/31/66/44`；重复菜单、permission/component 漂移、可见孤儿节点均为 `0`。
- 2026-08-29 真实浏览器验收：合同管理员完整信息架构、流程定义列表/编辑器、页面布局配置和业务单据流程配置全部可用；业务、法务无配置菜单；系统管理员只显示审批管理、异常处理、系统管理，合同正文 API 返回业务码 `403`；旧深链跳转保留 query/hash。
- 前端既有浏览器基线：登录、工作台、台账、详情、真实在线编辑、两人协作和历史只读均已实测。
- 演示基线：4 个合同类型、4 份原创合同模板、6 份演示合同；最终重置后保留确定性演示数据。E2E 清理脚本会物理删除其专属合同、子表与 blob，不再把测试残留计入演示基线。

## 6. 已知问题与遗留

1. **BPM 建表 SQL 的授权**：官方 `bpm-*.sql` 仅向芋道星球成员提供。本 POC 的 `sql/01_bpm_tables.sql` 是根据仓库内 MIT 许可的 `*DO.java` 实体类自行推导的 DDL。正式客户交付前仍需确认授权路径，或评估替换 Warm-Flow；这是外部法务决策，不在本轮代码中臆断。
2. **ONLYOFFICE 商用许可**：本轮只验证 Community 8.2 技术可行性；AGPLv3、品牌展示、分发/网络使用和升级义务需在正式交付前由法务/采购确认。
3. **生产网络边界**：本机 8090 仅绑定 loopback；`permissions.download=false` 只是 UI 控制。正式部署必须用反向代理/容器网络把 `/clm/online-edit/file|callback` 限制为 Document Server 网络身份并启用 TLS。
4. **前后端版本小幅错位**：前端 v2026.07 调用的 `/bpm/comment/*` 在后端该 tag 不存在；流程评论为空但不影响审批。
5. **并发边界**：真实两人同源协作已通过，forcesave→close 已按 OOXML 内容去重；数据库层仍没有面向“两个完全并发回调”的行锁/唯一约束集成测试，正式高并发上线前应补 MySQL 并发测试和版本号竞争保护。
6. **未做**：电子签、条款库、AI 审查、部门/角色级参与人、Excel 导出，以及内容级版本 diff/compare；当前只提供历史只读与并排查看。
7. `admin/admin123` 等默认口令仅限本机演示。

本地完全重置必须同时删除本项目的 MySQL/Redis/ONLYOFFICE Compose 卷；只重置数据库可能复用稳定文档 key 并命中旧的 Document Server 会话缓存。具体破坏性命令见 README，`cleanup-e2e-data.ps1` 只清理测试前缀数据。

## 7. 建议的下一步

1. 决定 BPM SQL 与 ONLYOFFICE 的正式授权/采购路径。
2. 把后端单测和两个 E2E 脚本接入 CI，并补 MySQL 并发 callback 集成测试。
3. 用脱敏真实合同覆盖中文字体、复杂表格、修订/批注和盖章页，形成可签字的 UAT 记录。
4. 用脱敏真实合同类型与按金额/部门分支的流程定义替换默认演示配置，并对条件、会签/或签和运行中版本固定语义形成客户 UAT 记录。
