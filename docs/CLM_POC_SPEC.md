# CLM POC 实施规格（yudao-module-clm + 前端 clm 区域）

> 本文是后端、前端实现的唯一契约。字段名、路径、状态值必须与本文和 `sql/02_clm_tables.sql` 完全一致。
> 基线：RuoYi-Vue-Pro v2026.07(jdk17/21)，Spring Boot 3.5 / MyBatis-Plus / Flowable 8；前端 yudao-ui-admin-vue3 v2026.07。
> 参考资料：`maps/*.md`（仓库约定的读取报告）。

## 0. 已锁定的设计决策

| 决策 | 说明 |
|---|---|
| 独立模块 `yudao-module-clm`，包 `cn.iocoder.yudao.module.clm` | 依赖 `yudao-module-system`、`yudao-module-bpm`（同仓库模块，需显式 `<version>${revision}</version>`），以及 starter：web、security、mybatis、redis、biz-tenant、excel、test(test scope)。 |
| 错误码段 `1_070_xxx_xxx` | `enums/ErrorCodeConstants.java`。分组：000 合同类型、001 类型版本、002 签约方、003 合同、004 文档、005 流程绑定、006 参与人、007 权限、008 在线编辑。 |
| 所有 DO 继承 `TenantBaseDO` | 表均有 `tenant_id`。所有 `@TableField(typeHandler=...)` 的 DO 必须 `@TableName(value=..., autoResultMap = true)`。 |
| 文档内容私有存储 | 接口 `document/ClmDocumentStorage { String store(byte[] content, String sha256); byte[] load(String fileKey); }`，默认实现 `DbDocumentStorage` 写 `clm_document_blob`，`fileKey = blob.id` 字符串。**不使用** infra 的 `/infra/file/*/get/**` 公开路由。 |
| 审批状态值与 BPM 对齐 | `ClmApprovalStatusEnum`：NOT_SUBMITTED(0) RUNNING(1) APPROVED(2) REJECTED(3) CANCELED(4)；`ClmWorkflowBindingStatusEnum`：PREPARING(0) RUNNING(1) APPROVED(2) REJECTED(3) CANCELED(4)。BPM 事件 `status`（1/2/3/4）可直接映射。 |
| businessKey = `clm_workflow_binding.id` | 不是 contractId。监听器据此找 binding → contract。 |
| 流程监听器不限定单一 key | `workflow/ClmProcessInstanceStatusListener implements ApplicationListener<BpmProcessInstanceStatusEvent>`：当 `businessKey` 能解析为 binding 且 `binding.processInstanceId` 等于 `event.getId()` 时处理，否则忽略。用 `TenantUtils.execute(binding.tenantId, ...)` 不需要——事件在审批请求线程内同步发布，租户上下文已存在；但监听器内部查询 binding 时用 `TenantUtils.executeIgnore` 包裹再校验 tenant，避免上下文缺失时查不到。 |
| 无"超级管理员旁路" | 合同对象权限只看 owner / participant / 流程参与人。super_admin 不自动可见未授权合同。 |
| 合同编号 | 创建后生成：`"HT" + yyyyMMdd + "-" + String.format("%05d", id)`。 |

## 1. 枚举（`enums/` 包）

```java
ClmApprovalStatusEnum        NOT_SUBMITTED(0), RUNNING(1), APPROVED(2), REJECTED(3), CANCELED(4)
ClmLifecycleStatusEnum       DRAFT(1), APPROVED(2), EFFECTIVE(3), EXPIRED(4), TERMINATED(5), VOID(6)
ClmTypeVersionStatusEnum     DRAFT(0), PUBLISHED(1)
ClmWorkflowBindingStatusEnum PREPARING(0), RUNNING(1), APPROVED(2), REJECTED(3), CANCELED(4)
ClmPartyTypeEnum             COMPANY(1), INDIVIDUAL(2)
ClmContractPartyRoleEnum     OUR_SIDE, COUNTERPARTY, OTHER            (String code)
ClmDocumentRoleEnum          MAIN, ATTACHMENT                         (String code)
ClmDocumentSourceTypeEnum    UPLOAD, ONLINE_EDIT, MANUAL_FINAL        (String code)
ClmParticipantRoleEnum       OWNER, COLLABORATOR, VIEWER              (String code)
ClmAuditActionEnum           CONTRACT_CREATE, CONTRACT_UPDATE, CONTRACT_DELETE, CONTRACT_SUBMIT, APPROVAL_RESULT,
                             DOCUMENT_UPLOAD, DOCUMENT_DOWNLOAD, PARTICIPANT_UPDATE, TYPE_PUBLISH,
                             ONLINE_EDIT_OPEN, ONLINE_EDIT_SAVE
ClmAuditAggregateTypeEnum    CONTRACT, DOCUMENT, CONTRACT_TYPE
```
所有 Integer 枚举实现 `cn.iocoder.yudao.framework.common.core.ArrayValuable`（参考 `BpmProcessInstanceStatusEnum` 写法），供 `@InEnum` 校验。

## 2. 权限码（已在 `sql/03_clm_menus_dicts.sql` 落菜单）

```
clm:contract-type:query|create|update|delete|publish
clm:party:query|create|update|delete
clm:contract:query|create|update|delete|submit|download|manage-member
```
文档、参与人、审计、流程绑定接口使用 `clm:contract:*` 对应动作：查询类用 `query`，上传用 `update`，下载用 `download`，参与人保存用 `manage-member`。

## 3. REST API 契约（前缀 `/admin-api`，Controller 上 `@RequestMapping("/clm/...")`）

返回体一律 `CommonResult<T>`；分页 `PageResult<T>{list,total}`；时间 `LocalDateTime`（前端收到毫秒时间戳，用 `formatDate`），日期字段 `LocalDate`（JSON `yyyy-MM-dd`）。

### 3.1 合同类型 `/clm/contract-type`

| 方法 | 路径 | 权限 | 入参 | 出参 |
|---|---|---|---|---|
| POST | `/create` | create | `ContractTypeSaveReqVO{ code*, name*, description, status(默认0), sort, processDefinitionKey(默认 clm_contract_approval_v1) }` | `Long id`。同时创建 version 1（DRAFT，formConf=null, formFields=[]，processDefinitionKey 取入参）。 |
| PUT | `/update` | update | `ContractTypeSaveReqVO{ id*, name*, description, status, sort }`（code 不可改，忽略） | `Boolean` |
| DELETE | `/delete?id=` | delete | | `Boolean`；存在引用该类型的合同时抛 `CONTRACT_TYPE_IN_USE` |
| GET | `/get?id=` | query | | `ContractTypeRespVO{ id, code, name, description, status, sort, currentVersionId, currentVersionNo, draftVersionId, createTime }` |
| GET | `/page` | query | `ContractTypePageReqVO extends PageParam{ code, name, status }` | `PageResult<ContractTypeRespVO>` |
| GET | `/simple-list` | **无注解**（登录即可） | | `List<ContractTypeSimpleRespVO{ id, code, name, currentVersionId }>`：仅 status=0 且 currentVersionId 非空 |
| GET | `/version/list?typeId=` | query | | `List<ContractTypeVersionRespVO>` 按 versionNo 降序 |
| GET | `/version/get?id=` | **无注解** | | `ContractTypeVersionRespVO{ id, typeId, versionNo, status, formConf, formFields(List<String>), processDefinitionKey, remark, publishedTime, createTime }` |
| PUT | `/version/update` | update | `ContractTypeVersionSaveReqVO{ id*, formConf, formFields(List<String>), processDefinitionKey*, remark }` | `Boolean`；非 DRAFT 抛 `CONTRACT_TYPE_VERSION_NOT_DRAFT` |
| POST | `/version/publish?id=` | publish | | `Boolean`；DRAFT→PUBLISHED，`publishedTime=now`，`type.currentVersionId=id`，审计 TYPE_PUBLISH（aggregate CONTRACT_TYPE/typeId） |
| POST | `/version/create-draft?typeId=` | update | | `Long 新版本 id`；若已存在 DRAFT 抛 `CONTRACT_TYPE_DRAFT_EXISTS`；复制 currentVersion（无则空）的 formConf/formFields/processDefinitionKey，versionNo = max+1 |

### 3.2 签约方 `/clm/party`

`PartySaveReqVO{ id, partyType*(1/2), name*, unifiedCreditCode, internalFlag*(Boolean), contactName, contactPhone, address, status(默认0), remark }`
- POST `/create` → Long；PUT `/update`；DELETE `/delete?id=`（被 clm_contract_party 引用时抛 `PARTY_IN_USE`）；GET `/get?id=` → `PartyRespVO`（同字段 + createTime）；GET `/page`（`PartyPageReqVO{ name, partyType, internalFlag, status }`）；GET `/simple-list?internalFlag=`（**无注解**；status=0；internalFlag 可空）→ `List<PartySimpleRespVO{ id, name, partyType, internalFlag, unifiedCreditCode }>`。

### 3.3 合同 `/clm/contract`

`ContractSaveReqVO`：
```
id, title*, typeId*, amount(BigDecimal), currency(默认 CNY), signDate, effectiveDate, expiryDate(LocalDate),
ownerUserId(空则当前用户), ownerDeptId(空则当前用户部门，取 SecurityFrameworkUtils.getLoginUserDeptId()),
description, customData(Map<String,Object>),
parties*: List<ContractPartyItemVO{ partyId*, roleCode*(OUR_SIDE/COUNTERPARTY/OTHER), sort }>
```
校验：至少 1 个 OUR_SIDE + 1 个 COUNTERPARTY（`CONTRACT_PARTY_REQUIRED`）；所有 partyId 存在；`customData` 按类型版本的 `formFields` 校验（见 §5）。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/create` | create | 解析 `typeVersionId = type.currentVersionId`（为空抛 `CONTRACT_TYPE_NOT_PUBLISHED`）；插入合同 → 生成 contractNo 回写；插入 parties（含 partySnapshot JSON：`{"name":..,"unifiedCreditCode":..,"partyType":..}`）；插入参与人 OWNER 行（ownerUserId，全部 can_* = true）；审计 CONTRACT_CREATE。返回 id。 |
| PUT | `/update` | update | `access.assertCanEdit`；`approvalStatus==NOT_SUBMITTED` 时允许改 typeId（重新解析版本），否则忽略 typeId；替换 parties；若 ownerUserId 变化：旧 OWNER 行改为 COLLABORATOR（保留），新 owner 插入/升级为 OWNER；审计 CONTRACT_UPDATE（detail 记录变更字段名列表）。 |
| DELETE | `/delete?id=` | delete | `assertCanManage` 且 approvalStatus ∈ {0,4} 且 lifecycle=DRAFT，否则 `CONTRACT_DELETE_NOT_ALLOWED`；逻辑删除 contract、parties、participants；审计 CONTRACT_DELETE。 |
| GET | `/get?id=` | query | `assertCanView` → `ContractRespVO`（§3.3.1） |
| GET | `/page` | query | `ContractPageReqVO extends PageParam{ title, contractNo, typeId, approvalStatus, lifecycleStatus, ownerUserId, createTime[] }` → **Mapper 层即过滤**：`owner_user_id = me OR id IN (select contract_id from clm_contract_participant where principal_type='USER' and principal_id=me and can_view=1 and deleted=0)`。行 VO 同 `ContractRespVO` 但不含 parties/currentDocumentVersion/permissions（可为空）。 |
| POST | `/submit` | submit | `ContractSubmitReqVO{ id*, startUserSelectAssignees(Map<String,List<Long>>), remark }` → 返回 `Long bindingId`。流程见 §4。 |
| GET | `/approval-preview?id=` | query | 返回 `{ processDefinitionKey, processDefinitionId, processDefinitionName }`（通过 `BpmProcessDefinitionService.getActiveProcessDefinition(key)` 查；无则 `processDefinitionId=null`），前端用于在提交前调用 BPM 的 `/bpm/process-instance/get-approval-detail` 展示审批人预览。 |

#### 3.3.1 `ContractRespVO`
```
id, contractNo, title, typeId, typeCode, typeName, typeVersionId, typeVersionNo,
ownerUserId, ownerUserName, ownerDeptId, ownerDeptName,
amount, currency, signDate, effectiveDate, expiryDate, description, customData(Map),
lifecycleStatus, approvalStatus, currentDocumentVersionId, currentBindingId, createTime, updateTime, creator,
parties: List<ContractPartyRespVO{ id, partyId, partyName, roleCode, sort, partySnapshot(Map) }>,
currentDocumentVersion: DocumentVersionRespVO | null,
currentBinding: WorkflowBindingRespVO | null,
permissions: { canView, canEdit, canDownload, canManage, canSubmit, canDelete, canCancelApproval }
```
`canSubmit` 规则：canEdit 的主体条件（owner 或 can_edit）且 approvalStatus != RUNNING 且 currentDocumentVersionId != null 且 **不是**（approvalStatus == APPROVED 且 currentBinding.documentVersionId == currentDocumentVersionId）。
`canCancelApproval`：approvalStatus == RUNNING 且 当前用户 == binding.creator（提交人）。
`ownerUserName`/`ownerDeptName` 用 `AdminUserApi.getUser` / `DeptApi.getDept`。

### 3.4 文档 `/clm/contract/document` 与 `/clm/document`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/clm/contract/document/upload` | update | `multipart/form-data`：`contractId*`, `file*`, `roleCode`(默认 MAIN), `remark`。`assertCanEdit`（approvalStatus==RUNNING 抛 `CONTRACT_LOCKED_BY_APPROVAL`）。扩展名白名单 docx/doc/pdf/xlsx/xls/pptx/ppt/txt/zip/png/jpg/jpeg；大小 ≤ 50MB（`DOCUMENT_FILE_TOO_LARGE`）。计算 `DigestUtil.sha256Hex`；`storage.store`；按 (contractId, roleCode) 取/建 `clm_document`（MAIN 唯一；ATTACHMENT 每次上传新建一个 document，name=文件名）；versionNo=max+1，parentVersionId=document.currentVersionId；更新 document.currentVersionId；roleCode=MAIN 时更新 contract.currentDocumentVersionId；审计 DOCUMENT_UPLOAD（aggregate DOCUMENT/documentId，contractId，detail: versionId, versionNo, fileName, sha256, size）。返回 `Long versionId`。 |
| GET | `/clm/contract/document/list?contractId=` | query | `assertCanView` → `List<DocumentRespVO{ id, contractId, roleCode, name, currentVersionId, status, createTime, versions: List<DocumentVersionRespVO> (按 versionNo 降序) }>` |
| GET | `/clm/document/version/get?id=` | query | 可见性同合同 → `DocumentVersionRespVO{ id, documentId, contractId, versionNo, parentVersionId, fileName, mimeType, fileSize, checksumSha256, sourceType, frozen, remark, creator, creatorName, createTime }` |
| GET | `/clm/document/version/download?id=` | download | `assertCanDownload(version)`；`storage.load`；响应头 `Content-Disposition: attachment; filename*=UTF-8''<urlencoded>`、`Content-Type`、`Content-Length`；审计 DOCUMENT_DOWNLOAD。用 `ServletUtils.writeAttachment` 或 `FileTypeUtils.writeAttachment`（infra）— 不引入 infra 依赖，自行写 response。 |

### 3.5 参与人 `/clm/contract/participant`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/list?contractId=` | query | `assertCanView` → `List<ContractParticipantRespVO{ id, contractId, principalType, principalId, principalName, roleCode, canView, canEdit, canDownload, canManage, createTime }>` |
| PUT | `/save` | manage-member | `ContractParticipantSaveReqVO{ contractId*, participants: List<Item{ principalType(默认 USER), principalId*, roleCode*(COLLABORATOR/VIEWER), canView, canEdit, canDownload, canManage }> }`；`assertCanManage`；OWNER 行不可通过此接口增删改（忽略 roleCode=OWNER 的项，保留现有 OWNER 行）；其余做差异更新（`CollectionUtils.diffList`）；审计 PARTICIPANT_UPDATE（detail: added/updated/removed principalIds）。 |

### 3.6 审计 `/clm/contract/audit-event`

GET `/page?contractId=&pageNo=&pageSize=` | query | `assertCanView` → `PageResult<AuditEventRespVO{ id, aggregateType, aggregateId, contractId, action, actorUserId, actorName, detailJson(String), occurredAt }>` 按 occurredAt 降序。

### 3.7 流程绑定 `/clm/workflow-binding`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/list?contractId=` | query | `assertCanView` → `List<WorkflowBindingRespVO{ id, contractId, purpose, processDefinitionKey, processDefinitionId, processInstanceId, documentVersionId, documentVersionNo, contractTypeVersionId, checksumSha256, status, resultReason, creator, creatorName, createTime, finishedTime }>` 按 id 降序 |
| GET | `/get?id=` | query | **BPM 业务表单详情用**。授权：`assertCanViewBinding`（合同可见 **或** 当前用户是该流程实例的参与人，见 §6）。返回 `WorkflowBindingDetailRespVO{ binding: WorkflowBindingRespVO, contract: ContractRespVO(不含 permissions 以外字段照常；permissions.canDownload 依据 §6), formSnapshot(Map<String,Object>), documentVersion: DocumentVersionRespVO, formConf(String), formFields(List<String>) }` — formConf/formFields 来自 `binding.contractTypeVersionId`。 |

## 4. 提交审批（`service/workflow/ContractWorkflowServiceImpl.submit`，`@Transactional`）

1. `contract = getRequired(id)`；`access.assertCanSubmit(contract)`（主体条件同 canEdit + RBAC 已在 Controller）。
2. 状态：approvalStatus == RUNNING → `CONTRACT_APPROVAL_RUNNING`；lifecycle ∉ {DRAFT, APPROVED} → `CONTRACT_STATUS_NOT_ALLOW_SUBMIT`；currentDocumentVersionId == null → `CONTRACT_MAIN_DOCUMENT_REQUIRED`；approvalStatus==APPROVED 且 currentBinding.documentVersionId == currentDocumentVersionId → `CONTRACT_NOTHING_TO_APPROVE`。
3. 必填：title、≥1 OUR_SIDE、≥1 COUNTERPARTY；`customData` 再次按 schema 校验。
4. `version = documentVersion(currentDocumentVersionId)`；`bytes = storage.load(version.fileKey)`；`sha256Hex(bytes)` 必须等于 `version.checksumSha256`，否则 `DOCUMENT_CHECKSUM_MISMATCH`；若 `!frozen` → 置 `frozen=true`。
5. `typeVersion = get(contract.typeVersionId)`；`key = typeVersion.processDefinitionKey`。
6. 插入 binding：status=PREPARING，purpose=APPROVAL，processDefinitionKey=key，documentVersionId，contractTypeVersionId，checksumSha256=version.checksumSha256，formSnapshot = JSON：
   ```json
   { "contract": { "id","contractNo","title","typeId","typeCode","typeName","amount","currency","signDate","effectiveDate","expiryDate","ownerUserId","ownerUserName","description","customData" },
     "parties": [ { "partyId","roleCode","name","unifiedCreditCode","partyType" } ],
     "documentVersion": { "id","versionNo","fileName","fileSize","checksumSha256" },
     "submitRemark": "..." }
   ```
7. `variables = { "contractId": Long, "bindingId": Long, "amount": Double(null→0), "ownerDeptId": Long, "contractTypeCode": String, "contractTitle": String }`。
8. `processInstanceId = bpmProcessInstanceApi.createProcessInstance(userId, new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(key).setVariables(variables).setBusinessKey(String.valueOf(binding.getId())).setStartUserSelectAssignees(req.getStartUserSelectAssignees()))`。
9. 更新 binding：processInstanceId、processDefinitionId（`BpmProcessDefinitionService.getActiveProcessDefinition(key).getId()`，可为空则不填）、status=RUNNING；更新 contract：approvalStatus=RUNNING，currentBindingId=binding.id。
10. 审计 CONTRACT_SUBMIT（detail: bindingId, processInstanceId, documentVersionId, versionNo, checksum）。

### 4.1 监听器 `workflow/ClmProcessInstanceStatusListener`

```java
@Component
public class ClmProcessInstanceStatusListener implements ApplicationListener<BpmProcessInstanceStatusEvent> {
    onApplicationEvent(event):
        Long bindingId = tryParseLong(event.getBusinessKey()); if null return;
        binding = bindingMapper.selectById(bindingId)  (TenantUtils.executeIgnore 包裹)
        if binding == null || !Objects.equals(binding.getProcessInstanceId(), event.getId()) return;
        contractWorkflowService.handleProcessResult(binding, event.getStatus(), event.getReason());
}
```
`handleProcessResult`（`@Transactional`，用 `TenantUtils.execute(binding.getTenantId(), ...)`）：
- status 1 RUNNING → binding.status=RUNNING（幂等）；
- 2 APPROVE → binding.status=APPROVED, finishedTime=now, resultReason；contract.approvalStatus=APPROVED，lifecycle=APPROVED；
- 3 REJECT → binding REJECTED；contract.approvalStatus=REJECTED（lifecycle 不变）；
- 4 CANCEL → binding CANCELED；contract.approvalStatus=CANCELED；
- 审计 APPROVAL_RESULT（actorUserId=null，actorName="BPM"，detail: bindingId, processInstanceId, status, reason）。

## 5. 扩展字段校验 `service/contracttype/ContractFormSchemaValidator`

`validate(List<String> formFields, Map<String,Object> customData)`：
- 解析每条 rule JSON（`JsonUtils.parseTree`），递归收集 `field`（含 `children[]`）；required = `$required==true` 或 `validate[]` 中任一 `required==true`。
- formFields 为空：customData 必须为空（null 或空 Map），否则 `CONTRACT_CUSTOM_FIELD_UNKNOWN`。
- customData 中存在 schema 外的 key → `CONTRACT_CUSTOM_FIELD_UNKNOWN`（参数 key）。
- required 字段缺失或为空字符串/空数组 → `CONTRACT_CUSTOM_FIELD_REQUIRED`（参数 title 或 field）。

## 6. 对象权限 `access/ContractAccessService`

```java
boolean canView(ContractDO c, Long userId)      // owner || participant.can_view || isProcessParticipant(c, userId)
boolean canEdit(ContractDO c, Long userId)      // (owner || participant.can_edit) && approvalStatus != RUNNING && lifecycle in (DRAFT, APPROVED)
boolean canDownload(ContractDO c, Long userId)  // owner || participant.can_download || isProcessParticipant(c, userId)
boolean canManage(ContractDO c, Long userId)    // owner || participant.can_manage
void assertCanView/Edit/Download/Manage(...)    // 不满足抛 CONTRACT_ACCESS_DENIED (1_070_007_000, "无权访问该合同")
boolean isProcessParticipant(ContractDO c, Long userId)
```
`isProcessParticipant`：取该合同全部 binding 的 processInstanceId（非空），对每个用 Flowable `HistoryService`：
`historyService.createHistoricTaskInstanceQuery().processInstanceId(pid).taskAssignee(String.valueOf(userId)).count() > 0` 或 `.taskInvolvedUser(String.valueOf(userId)).count() > 0`；另外流程发起人（`historyService.createHistoricProcessInstanceQuery().processInstanceId(pid).startedBy(String.valueOf(userId))`）也算。
所有判断先保证合同属于当前租户（MP 租户插件已保证查不到跨租户数据 → `selectById` 为 null 即 `CONTRACT_NOT_EXISTS`）。

## 7. 审计 `service/audit/ClmAuditService`

`record(ClmAuditAggregateTypeEnum type, Long aggregateId, Long contractId, ClmAuditActionEnum action, Map<String,Object> detail)`：actorUserId = `SecurityFrameworkUtils.getLoginUserId()`（可空），actorName = `getLoginUserNickname()`（空则 "BPM"/"SYSTEM"），occurredAt = now，detailJson = JsonUtils.toJsonString(detail)。**同事务**写入（不用异步）。

## 8. 后端文件清单（全部必须存在）

```
yudao-module-clm/pom.xml
src/main/java/cn/iocoder/yudao/module/clm/
  package-info.java
  enums/ErrorCodeConstants.java  + 上述枚举（enums/contract, enums/document, enums/workflow, enums/party ...）
  framework/package-info.java
  framework/web/config/ClmWebConfiguration.java          // GroupedOpenApi "clm"
  dal/dataobject/{contracttype/ContractTypeDO, contracttype/ContractTypeVersionDO, party/PartyDO, contract/ContractDO, contract/ContractPartyDO, contract/ContractParticipantDO, document/DocumentDO, document/DocumentVersionDO, document/DocumentBlobDO, workflow/WorkflowBindingDO, audit/AuditEventDO}
  dal/mysql/...Mapper（每个 DO 一个，extends BaseMapperX）
  document/ClmDocumentStorage.java, document/DbDocumentStorage.java
  access/ContractAccessService.java (+Impl)
  service/contracttype/{ContractTypeService,Impl, ContractFormSchemaValidator}
  service/party/{PartyService,Impl}
  service/contract/{ContractService,Impl, ContractParticipantService,Impl}
  service/document/{DocumentService,Impl}
  service/workflow/{ContractWorkflowService,Impl}, workflow/ClmProcessInstanceStatusListener
  service/audit/{ClmAuditService,Impl}
  controller/admin/contracttype/ContractTypeController + vo/
  controller/admin/party/PartyController + vo/
  controller/admin/contract/{ContractController, ContractDocumentController, ContractParticipantController, ContractAuditEventController} + vo/
  controller/admin/document/DocumentController + vo/
  controller/admin/workflow/WorkflowBindingController + vo/
src/test/java/cn/iocoder/yudao/module/clm/
  service/contracttype/ContractFormSchemaValidatorTest.java   // 纯单元测试（无 DB）
  service/party/PartyServiceImplTest.java                     // BaseDbUnitTest
  service/contracttype/ContractTypeServiceImplTest.java       // BaseDbUnitTest：创建→草稿→发布→发布后 update 抛错→create-draft
  service/document/DocumentServiceImplTest.java               // BaseDbUnitTest + mock access：同名上传两次得 v1/v2，v1 checksum 不变；frozen 版本不可被覆盖（无覆盖接口，断言新版本 id 不同）
src/test/resources/{application-unit-test.yaml, logback.xml, sql/create_tables.sql(H2, 全部 11 张表), sql/clean.sql}
```
Service 单测中对 `ContractAccessService`、`BpmProcessInstanceApi`、`AdminUserApi`、`DeptApi`、`HistoryService` 用 `@MockBean`。

## 9. 前端文件清单

```
src/api/clm/contractType/index.ts   // 类型 + 版本接口，导出接口类型 ContractTypeVO, ContractTypeVersionVO
src/api/clm/party/index.ts
src/api/clm/contract/index.ts       // contract + document + participant + auditEvent + approvalPreview，导出 ContractVO, ContractPartyItemVO, DocumentVO, DocumentVersionVO, ParticipantVO, AuditEventVO
src/api/clm/workflowBinding/index.ts
src/views/clm/contractType/index.vue            // 列表 + ContractTypeForm.vue（新增/编辑）+ 版本抽屉 VersionDrawer.vue（列表、发布、新建草稿、设计表单入口）
src/views/clm/contractType/ContractTypeForm.vue
src/views/clm/contractType/VersionDrawer.vue
src/views/clm/contractType/editor/index.vue     // FcDesigner 设计扩展字段：query.id = versionId；保存（update）/ 保存并发布；processDefinitionKey 选择（el-select allow-create，选项来自 DefinitionApi.getProcessDefinitionList({ suspensionState: 1 })，label=name(key)）
src/views/clm/party/index.vue + PartyForm.vue
src/views/clm/contract/index.vue                // 台账：筛选 标题/编号/类型/审批状态/生命周期；新建→ push({ name: 'ClmContractCreate' })；行：详情 push({ name:'ClmContractDetail', params:{ id } })、删除
src/views/clm/contract/create/index.vue         // 新建/编辑（query.id 有值为编辑）：核心字段 + 类型选择后渲染 form-create（动态字段，v-model 绑定 customData）+ 我方/相对方多选（PartyApi.getSimpleList）+ 保存草稿 → push 详情
src/views/clm/contract/detail/index.vue         // 详情：头部（标题/编号/状态 dict-tag/操作按钮）+ el-tabs：基本信息 | 正文与附件 | 参与人 | 审批记录 | 审计轨迹
src/views/clm/contract/detail/components/ContractBasicInfo.vue      // descriptions + 只读 form-create（fApi.disabled(true)）
src/views/clm/contract/detail/components/ContractDocuments.vue      // el-upload(:auto-upload=false 或 :http-request 自定义) 调 ContractApi.uploadDocument(FormData)；版本表：版本号/文件名/大小/sha256(缩略+复制)/来源/冻结/上传人/时间/下载
src/views/clm/contract/detail/components/ContractParticipants.vue   // 列表 + 编辑对话框（UserSelect：/system/user/simple-list via UserApi.getSimpleUserList），角色与 4 个权限开关
src/views/clm/contract/detail/components/ContractApprovals.vue      // binding 列表；"查看流程" push({ name: 'BpmProcessInstanceDetail', query: { id: processInstanceId } })；"撤销审批" 调 ProcessInstanceApi.cancelProcessInstanceByStartUser(processInstanceId, reason)
src/views/clm/contract/detail/components/ContractAuditEvents.vue    // 分页表
src/views/clm/contract/detail/components/SubmitApprovalDialog.vue  // 提交审批：显示将绑定的版本与 sha256；审批人预览（调 /bpm/process-instance/get-approval-detail, 与 bpm/oa/leave/create.vue 同法处理 startUserSelectTasks）；确认 → ContractApi.submitContract
src/views/clm/contract/bpm/index.vue            // BPM 业务表单查看组件：props.id = bindingId（也支持 query.id）；加载 WorkflowBindingApi.get → 展示快照（核心字段/签约方/动态字段只读/绑定版本 + 下载按钮）
src/router/modules/remaining.ts                 // 新增 /clm 分组（hidden）：contract/create (ClmContractCreate), contract/detail/:id (ClmContractDetail, props), contract-type/editor (ClmContractTypeEditor)
src/utils/dict.ts                               // DICT_TYPE 新增 CLM_* 10 项（值与 sql/03 一致）
.env.local                                      // 追加 VITE_APP_CAPTCHA_ENABLE=false
```
下载实现：`request.download({ url: '/clm/document/version/download', params: { id } })` 得到 Blob → `window.URL.createObjectURL` + `<a download=fileName>` 点击（在 `src/api/clm/contract/index.ts` 内提供 `downloadVersionFile(id, fileName)` 工具）。
文件大小显示用 `src/utils/index.ts` 的 `formatFileSize`（若不存在则本地实现）。
前端权限指令：按钮用 `v-hasPermi`，同时根据 `permissions.*` 控制禁用/隐藏。
组件命名 `defineOptions({ name: 'ClmContract' })` 等需与菜单 `component_name` 一致（ClmContract、ClmContractType、ClmParty）。

## 10. BPM 流程模型（启动后经 API/UI 创建，不在代码中）

- key `clm_contract_approval_v1`，name `合同审批流程`，category `contract`，type SIMPLE(20)，formType CUSTOM(20)，
  `formCustomCreatePath = /clm/contract/create`，`formCustomViewPath = /clm/contract/bpm/index.vue`，managerUserIds=[1]。
- 节点：发起人 → 审批节点「法务审批」（candidateStrategy USER，指定用户 id 100 `yudao`）→ 审批节点「负责人审批」（指定用户 1 `admin`）→ 结束。
