# CLM POC 交付说明（2026-08-24）

> 目标：对标甄零一诺的合同系统 POC，验证 RuoYi-Vue-Pro 作为底座能否低成本提供 **流程引擎 + 表单编辑 + 管理底座 + 文档在线编辑** 四个核心点。
> 本文只记录**实际做到并验证过**的内容；未验证的部分明确标注。

## 1. 一句话结论

在 Windows 本机（无 Docker）用 RuoYi-Vue-Pro v2026.07 单体 + 自建 `yudao-module-clm`，跑通了 README 的纵向闭环：
**合同类型（FormCreate 扩展字段）发布 → 直接创建合同草稿 → 上传不可变正文版本（SHA-256）→ 提交审批（绑定准确版本）→ RuoYi BPM 两级审批通过/驳回 → CLM 监听回写状态 → 合同级 ACL 与受控下载 → 审计轨迹**。
`scripts/e2e-api.ps1` 57 项断言全部通过，`scripts/e2e-onlyoffice-callback.ps1` 61 项断言全部通过；前端页面（台账/类型/签约方/设计器/新建/详情/BPM 业务表单）在浏览器中可用。ONLYOFFICE 在线编辑代码已实现并用回调模拟验证了后端链路，但**真实 Document Server 未在本机部署**（无 Docker），属遗留 Gate。

## 2. 四个核心点的落地情况

| 核心点 | 落地方式 | 验证 |
|---|---|---|
| 流程引擎（画布式编排） | RuoYi BPM（Flowable 8）已启用；SIMPLE 仿钉钉设计器 + BPMN 设计器均可用；POC 流程 `clm_contract_approval_v1`（发起人 → 法务审批[yudao] → 负责人审批[admin] → 结束）由 `scripts/create-bpm-model.ps1` 脚本化创建并发布 | E2E：通过/驳回/重提；待办、流程详情页内嵌合同业务表单 |
| 表单编辑 | 复用前端 FormCreate 设计器；合同类型 **版本化** schema（草稿可编辑、发布后不可变、新建草稿版本复制）；合同固定引用 `type_version_id`；后端按 schema 校验 `custom_data`（必填/未知字段） | E2E：发布后 update 被拒；未知字段/必填字段服务端拦截；UI 设计器可打开编辑 |
| 管理系统底座 | RuoYi 用户/部门/角色/菜单/租户/字典原样复用；CLM 新增 20 条菜单（7000 段）、10 个字典 | UI 登录、菜单、按钮权限 |
| 文档在线编辑 | ONLYOFFICE 适配：编辑器配置 + JWT、文件拉取/保存回调的开放接口（令牌保护）、回调生成新版本（幂等）、并排查看（明确不是内容级比较） | 回调模拟脚本 `scripts/e2e-onlyoffice-callback.ps1`：61 项断言通过（令牌/JWT 校验、回调生成 ONLINE_EDIT 新版本、重复回调幂等、审批中拒绝编辑、晚到保存审计）；真实 Document Server **未验证** |

## 3. 目录与关键文件

见 `docs/RUNBOOK_WINDOWS.md`（启动/重建/重置数据库）。代码：
- 后端 `backend/yudao-module-clm/`（约 100 个 Java 文件，单测 35 项）：`controller/admin/*`、`service/*`、`access/ContractAccessService`（对象级权限）、`workflow/ClmProcessInstanceStatusListener`（BPM 事件回写）、`document/ClmDocumentStorage`（私有 blob 存储）、`onlyoffice/*`。
- 前端 `frontend/src/views/clm/**`、`frontend/src/api/clm/**`，路由 `src/router/modules/remaining.ts`（`/clm` 组），字典 `src/utils/dict.ts`（`CLM_*`）。
- SQL `sql/01_bpm_tables.sql`（BPM 业务表 + 流程分类）、`sql/02_clm_tables.sql`（11 张 clm 表）、`sql/03_clm_menus_dicts.sql`（菜单/角色授权/字典）。
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

- 基线：`runtime/logs/build-baseline.log`、`backend.log`（"项目启动成功"），API 登录 `admin` 成功。
- BPM Gate：`sql/01_bpm_tables.sql` 导入；启动后 `ACT_*` 39 张、`FLW_*` 6 张自动建表；`scripts/create-bpm-model.ps1` 输出 `DEPLOYED: definitionId=clm_contract_approval_v1:…`。
- 单测：`mvn -pl yudao-module-clm test` → `Tests run: 35, Failures: 0, Errors: 0`。
- 纵向闭环：`scripts/e2e-api.ps1` → `ALL CHECKS PASSED`（日志 `runtime/logs/e2e-api-run*.log`）。覆盖 README §16 用例 1–11。
- 在线编辑回调链路：`scripts/e2e-onlyoffice-callback.ps1` → `ALL CHECKS PASSED`（61 项，日志 `runtime/logs/e2e-onlyoffice-final.log`）。
- 前端生产构建：`pnpm build:local` 成功（`runtime/logs/frontend-build.log`）。
- 前端：`vue-tsc` 对 `src/views/clm`、`src/api/clm` 无错误；eslint/prettier 通过；浏览器实测登录、台账、详情（五个 Tab）、类型设计器、新建合同（选类型后动态字段出现）、BPM 流程详情内嵌业务表单、在线编辑页在无 Document Server 时的降级提示。

## 6. 已知问题与遗留

1. **ONLYOFFICE Document Server 未部署**：本机无 Docker。`infra/compose.onlyoffice.yaml` 已备好；在有 Docker 的机器按 `docs/CLM_ONLYOFFICE_SPEC.md` §6 验收（真实 DOCX 打开/保存回调/历史/并发/越权）。"版本比较"按 README 要求降级为并排查看，未宣称内容级 compare。
2. **BPM 建表 SQL 的授权**：官方 `bpm-*.sql` 仅向芋道星球成员提供。本 POC 的 `sql/01_bpm_tables.sql` 是我根据仓库内 MIT 许可的 `*DO.java` 实体类**自行推导**的 DDL（未复制任何来源不明的 SQL）。正式对客户交付前，请按 README §6/§17 与作者确认商用/交付授权（或购买星球会员取得官方 SQL 并替换）。
3. **前后端版本小幅错位**：前端 v2026.07 调用的 `/bpm/comment/*`（流程评论）在后端该 tag（内容为 2026.06 发布）不存在，流程详情页会弹一条"工作流模块已禁用"提示并且"流程评论"为空，不影响审批。可升级后端到含该功能的提交或在前端隐藏该 Tab。
4. **编码**：PowerShell 5.1 执行无 BOM 的 .ps1 会把中文字面量按 GBK 读取；`scripts/*.ps1` 已统一保存为 UTF-8 with BOM。早期 E2E 运行产生过几条乱码标题的测试数据，已逻辑删除。
5. **未做**：ATTACHMENT 之外的文档类型语义、手工归档（MANUAL_FINAL 上传入口未暴露到 UI，后端 sourceType 已预留）、部门/角色级参与人（仅 USER）、Excel 导出、并发编辑冲突处理（无 `row_version` 乐观锁）。
6. **安全加固**：开放接口 `/clm/online-edit/file|callback` 仅靠短期 HMAC 令牌 + Document Server JWT；生产前应加 IP/网络限制与令牌一次性化。`admin/admin123` 等默认口令仅限本机。

## 7. 建议的下一步

1. 在有 Docker 的机器跑 ONLYOFFICE Gate（2–3 天预算，README §17 止损规则不变）。
2. 决定 BPM SQL 授权路径（购买/确认 vs. 换 Warm-Flow），这是商用交付前唯一的法务阻塞。
3. 把 E2E 脚本接入 CI（后端单测 + `e2e-api.ps1`），并补 `ContractServiceImpl`/`ContractWorkflowServiceImpl` 的单测。
4. 用真实合同类型与审批流（按金额/部门分支）替换 POC 的两节点固定流程——SIMPLE 设计器已支持条件分支。
