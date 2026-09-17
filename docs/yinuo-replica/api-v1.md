# 一诺复刻第一版 API 契约

本文锁定 W01–W06 与 CLM 后端第一版的接口边界。所有接口继续使用 RuoYi-Vue-Pro 的 `CommonResult<T>`，分页使用 `PageResult<T>`；ID 在 JSON 中按前端既有约定作为字符串传递，时间使用本地日期时间字符串。

## 通用不变量

- 草稿创建成功时 `contractNo = null`；只有首次成功提交才分配永久编号，撤回、退回、拒绝后重提沿用。
- `ContractRevision` 不可覆盖。任何正文、字段、参与方或重大承诺变化均新建修订，并要求调用方提供 `baseRevisionId`。
- 协同完成结论、审批业务单和审批任务决定均绑定精确 `revisionId`。
- 产品界面不得直接调用通用 BPM 审批 mutation；W06 只调用 `/clm/approval/**`。
- 第一版只到 `APPROVED`，不返回或生成签署、归档、履约任务。

## W01 工作台

### `GET /clm/workbench/summary`

返回：

```json
{
  "draftCount": 0,
  "collaborationTodoCount": 0,
  "approvalTodoCount": 0,
  "startedRunningCount": 0,
  "governanceIssueCount": 0
}
```

### `GET /clm/workbench/items`

查询参数：`type`、`pageNo`、`pageSize`。`type` 为 `DRAFT | COLLABORATION | APPROVAL | APPROVAL_DONE | STARTED | COPIED | GOVERNANCE`。`APPROVAL` 是本人当前待办并返回可操作 `taskId`；已办、发起和抄送项只返回 `contractId` 并进入 W06 历史模式。返回统一工作项分页，至少包含 `id`、`contractId`、`contractName`、`contractNo`、`revisionId`、`taskId`、`status`、`updatedTime`。

## W02 起草

### `GET /clm/template/published-page`

查询参数：`name`、`contractTypeId`、`pageNo`、`pageSize`。只返回已发布版本；每项至少包含 `id`、`code`、`name`、`contractTypeId`、`currentVersionId`、`currentVersionNo`、`fileName`。

### `POST /clm/contract/create-from-template`

请求：

```json
{
  "templateVersionId": "1",
  "name": "采购合同",
  "ownerUserId": "1"
}
```

返回 `contractId`。后端创建草稿、主文档和初始修订，但不分配合同编号。

### `POST /clm/contract/create-from-upload`

`multipart/form-data`：`file`、`name`、`contractTypeId`、可选 `ownerUserId`。返回 `contractId`，并把来源标为 `UPLOAD`。

## W03 合同台账

沿用 `GET /clm/contract/page`，新增兼容查询参数 `stageCode`、`deletedOnly`、`orgId`、`contractTypeId`、`counterpartyName`、`scope`。`scope` 为 `HANDLED | PARTICIPATED | APPROVED_BY_ME | AUTHORIZED_ORG`，必须在服务端 SQL 层分别按负责人、参与/协同关系、本人审批决定和有效组织+合同类型授权过滤；不传时仅返回上述口径的并集。列表项增加 `currentRevisionId`、`sourceMode`、`stageCode`、`deleted`；旧生命周期字段只作为兼容投影。

### `PUT /clm/contract/restore-draft?id={contractId}`

只允许恢复当前用户有恢复权限的逻辑删除草稿。

## W04 合同工作区

### 修订

- `GET /clm/contract/revision/list?contractId={id}`
- `GET /clm/contract/revision/compare?fromRevisionId={id}&toRevisionId={id}`
- `PUT /clm/contract/revision/save`

保存请求：

```json
{
  "contractId": "1",
  "baseRevisionId": "10",
  "name": "采购合同",
  "amount": 100000,
  "currency": "CNY",
  "startDate": "2026-08-27",
  "endDate": "2027-08-26",
  "ourPartyId": "1",
  "counterpartyIds": ["2"],
  "changeReason": "补充付款条件"
}
```

返回新 `revisionId` 和 `revisionNo`；基线过期返回明确并发冲突错误。

### 重大承诺

- `GET /clm/contract/commitment?contractId={id}`
- `POST /clm/contract/commitment`
- `PUT /clm/contract/commitment`
- `DELETE /clm/contract/commitment?id={id}`
- `PUT /clm/contract/commitment/confirm-none?contractId={id}`

承诺项至少包含 `category`、`content`、`ownerUserId`、`dueDate`、`riskLevel`。增加、修改、删除或确认无重大承诺均创建新修订。

### 提交

沿用 `POST /clm/contract/submit`，请求增加必填幂等键 `submitRequestId` 和 `baseRevisionId`。返回：

```json
{
  "approvalCaseId": "1",
  "processInstanceId": "...",
  "contractNo": "HT-2026-0001",
  "submittedRevisionId": "10"
}
```

### 模板升级与本地 AI 审查

- `GET /clm/template/upgrade-preview?contractId={id}`：只提示比草稿当前来源版本更新的已发布版本，不修改合同。
- `PUT /clm/template/upgrade`：请求 `contractId`、`baseRevisionId`、`targetTemplateVersionId`；显式生成新正文版本和新合同修订。
- `POST /clm/ai-review/run`：只运行本地确定性适配器并绑定当前精确修订；外部模型未配置时不得发送正文。
- `GET /clm/ai-review/list?contractId={id}`、`PUT /clm/ai-review/finding/resolve?id={id}&resolution=ACCEPTED|IGNORED`：查询运行及其发现并接受或忽略；修订变化后旧结果显示过期。

## W05 法务协同

- `GET /clm/collaboration/page`：查询参数 `view = TODO | STARTED | COMPLETED`。
- `GET /clm/collaboration/get?id={caseId}`。
- `POST /clm/collaboration/start`：`contractId`、`revisionId`、`legalUserId`、`reason`。
- `POST /clm/collaboration/comment`：`caseId`、`revisionId`、`content`。
- `PUT /clm/collaboration/request-change`：`caseId`、`revisionId`、`content`。
- `PUT /clm/collaboration/complete`：`caseId`、`revisionId`、`conclusion`。
- `PUT /clm/collaboration/cancel`：`caseId`、`reason`。

完成结论仅对 `completedRevisionId` 有效；合同产生新修订后列表必须显示该结论已过期。

## W06 合同审批

### `GET /clm/approval/task/get?taskId={taskId}`

返回当前任务身份、合同摘要、`approvalCaseId`、`submittedRevisionId`、`currentRevisionId`、主文档、修订列表、参与方/承诺快照、历史意见、可用动作和节点编辑政策。

### 审批动作

- `PUT /clm/approval/task/approve`
- `PUT /clm/approval/task/reject`
- `PUT /clm/approval/task/return`

共同请求字段：`taskId`、`revisionId`、`reason`、`requestId`；退回另带 `targetActivityId`。后端必须校验当前 assignee，并在委托 Flowable 前唯一写入 `taskId -> decisionRevisionId`。

### 审批协作、节点编辑和发起人撤回

- `PUT /clm/approval/task/transfer`：`id`、`assigneeUserId`、`reason`。
- `PUT /clm/approval/task/copy`：`id`、`copyUserIds`、`reason`。
- `PUT /clm/approval/task/add-sign`：`id`、`userIds`、`type = before | after`、`reason`。
- `PUT /clm/approval/task/withdraw?taskId={taskId}`：仅按 Flowable 已验证规则撤回本人已办任务。
- `PUT /clm/approval/task/edit`：`taskId`、`requestId`、`contractId`、`baseRevisionId` 及允许修改的合同字段/参与方；服务端联合校验当前 assignee 和已发布节点编辑政策。非重大变化在原业务单继续，重大变化取消旧业务单并完整重走审批。
- `PUT /clm/approval/task/edit-document`：`multipart/form-data`，字段为 `taskId`、`requestId`、`contractId`、`baseRevisionId`、`changeReason`、`file`；仅接受 `doc/docx/pdf`。服务端校验当前 assignee 与节点 `document` 编辑政策，保存私有正文版本并生成不可变合同修订；重大/非重大语义与字段编辑一致。
- `PUT /clm/approval/case/withdraw`：`approvalCaseId`、`reason`；仅发起人可撤回合同当前运行审批，合同回到草稿但永久编号不回收。

### `GET /clm/approval/history?contractId={contractId}`

返回跨审批业务单的责任链；每个任务意见都包含其实际决定的 `revisionId`，重大重审保留旧实例但标记为 `REVISION_SUPERSEDED`。

## G02-G06、G09 与 S01-S03

- 范本：`/clm/governance/template/{page|get|save-draft|publish|disable|file}`。
- 编号：`/clm/governance/numbering/{page|get|save-draft|preview|publish|disable}`；一期发布规则必须包含我方主体简称号段。
- 路由：`/clm/governance/routing/{page|get|save-draft|precheck|publish|disable}`；发布前校验 BPM 定义与候选人配置。
- 权限政策/具体用户授权：`/clm/permission/**`；G05 只发布政策，S01 是用户有效范围唯一写入口。
- 治理阻断与流程对账：`/clm/governance-issue/**`、`/clm/reconciliation/**`；`PUT /clm/reconciliation/confirm` 由合同管理员提交 `runId`、`bindingId`、`opinion`，只在该运行的差异报告中固化确认状态、确认人和时间，不触发外部系统、不修改合同正文或审批结果；`POST /clm/reconciliation/run` 仍只供系统管理员技术运行/重放，且不得手工选择审批结果。
- 钉钉沙箱：`/clm/integration/dingtalk/status`、`run-sandbox`、`run/page`、`delivery/page`、`delivery/retry`。无凭据时持久化 `NOT_CONFIGURED` 报告且 `externalRequestSent=false`。

## G07-G08 参与方目录与批量导入

- `GET /clm/party/duplicate-candidates`：可选 `internalFlag`、`partyType`，按统一信用代码优先、规范化名称次之返回重复候选组。
- `PUT /clm/party/merge`：请求 `sourcePartyId`、`targetPartyId`。只切换草稿/协同且未在审批中的活引用，审批中和已审批合同只计入 `protectedContractCount`；不可变修订快照永不改写，重复调用幂等。
- `GET /clm/party-import/template`、`POST /clm/party-import/upload`：下载真实 Excel 模板并上传最多 5000 行的 xls/xlsx，上传字段为 `file`、`jobKey`。
- `GET /clm/party-import/page`、`GET /clm/party-import/get?id={id}`、`GET /clm/party-import/item/page?jobId={id}`：查询作业和逐行校验/去重结果。
- `PUT /clm/party-import/item/update`、`PUT /clm/party-import/confirm?id={id}&requestId={key}`、`PUT /clm/party-import/retry-failed?id={id}&requestId={key}`、`GET /clm/party-import/failed-file?id={id}`：修正行、幂等确认、仅重试失败项和安全导出失败行。

## G10 离职交接

- `POST /clm/handover/open/refresh`：请求 `sourceUserId`，仅供系统集成入口创建/刷新交接异常；响应只含 `caseId`、状态和计数，不返回合同或任务内容。
- `GET /clm/handover/page`、`GET /clm/handover/get?id={id}`：合同管理员查看交接单、经办合同与仍活动的 CLM 审批任务必要元数据。
- `PUT /clm/handover/reassign`：请求 `caseId`、`targetUserId`、必填 `reason`、可选 `itemIds`。服务端校验目标用户启用且组织、合同类型两维有效范围均覆盖；合同负责人和活动任务逐项受控重分配，保留原 assignee、逐项结果和统一审计，不冒充离职用户调用普通转交。

## 兼容与错误处理

- 旧 `/clm/workflow-binding/**`、合同类型范本字段、`currentDocumentVersionId` 继续可读，作为新模型兼容投影。
- 旧 `/clm/contract/bpm/index.vue` 只重定向到 W06；不得继续承载 mutation。
- 复制、续签、归档相关旧服务可保留回归测试，但菜单、权限和 W 页面不暴露。
- 外部 AI、钉钉未配置时返回可诊断的 `NOT_CONFIGURED`，不得伪造成功或发送真实合同正文。
