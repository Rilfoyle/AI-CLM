# 演示版改造规格（对标一诺主流程/界面）

> 基线：现有 `yudao-module-clm` + `frontend/src/{api,views}/clm`。本文是本轮改造的唯一契约，字段/路径必须一致。
> 参考：`docs/YINUO_REFERENCE.md`（对标结论）、`docs/CLM_POC_SPEC.md`（既有 API 契约）。
> DB 变更已执行：`sql/06_demo_upgrade.sql`（clm_contract.source_contract_id/relation_type；clm_contract_type.template_file_key/template_file_name；生命周期字典文案 2=审批通过、3=已签订；新字典 clm_contract_relation_type(COPY/RENEWAL)、审计动作 CONTRACT_ARCHIVE；新菜单 7040 起草中心 clm/contract/draftCenter/index → ClmContractDraftCenter）。

## 1. 新增/调整后端 API（前缀 /admin-api）

| 方法 | 路径 | 权限 | 契约 |
|---|---|---|---|
| POST | `/clm/contract/copy` | `clm:contract:create` | body `{ sourceContractId*, relationType*('COPY'|'RENEWAL'), title? }`。校验：源合同可见（assertCanView）。复制：title(缺省=源标题+"（复制）"或"（续签）")、typeId/typeVersionId(按源合同的 type 重新取当前发布版，无发布版则报 CONTRACT_TYPE_NOT_PUBLISHED)、amount/currency/dates/description/customData（customData 需按新版本 schema 过滤掉未知字段，缺必填不报错——草稿允许后补，提交时再校验→**注意**：现有 create 校验必填，此处内部创建绕过必填校验只做未知字段过滤）、parties 快照重建；owner=当前用户。若源合同有当前正文版本：复制其 blob 为新合同 MAIN v1（sourceType=UPLOAD，remark='复制自 '+源合同编号）。写 source_contract_id/relation_type。审计 CONTRACT_CREATE（detail 含 sourceContractId/relationType）。返回新合同 id。 |
| POST | `/clm/contract/archive` | `clm:contract:update` | multipart：`contractId*`, `file*`(盖章扫描件 pdf/docx/jpg/png/zip), `signDate?`, `effectiveDate?`, `expiryDate?`(yyyy-MM-dd)。前置：assertCanEdit 主体条件（owner/can_edit）、approvalStatus==APPROVED(2)、lifecycle==APPROVED(2)，否则 `CONTRACT_ARCHIVE_NOT_ALLOWED`(1_070_003_010,"仅审批通过的合同可归档定稿")。动作：新建 MAIN 版本 sourceType=MANUAL_FINAL、frozen=true、remark='线下盖章定稿'；更新 contract.currentDocumentVersionId、传入的日期字段、lifecycle=EFFECTIVE(3)；审计 CONTRACT_ARCHIVE(新增 ClmAuditActionEnum.CONTRACT_ARCHIVE)。返回versionId。 |
| POST | `/clm/contract-type/template/upload` | `clm:contract-type:update` | multipart：`typeId*`, `file*`(.docx/.doc/.pdf)。存 ClmDocumentStorage，写 template_file_key/name。返回 true。 |
| GET | `/clm/contract-type/template/download?typeId=` | 登录即可 | 流式返回范本文件（attachment）。无范本 404 或 `CONTRACT_TYPE_TEMPLATE_NOT_EXISTS`(1_070_000_010)。 |
| POST(改) | `/clm/contract/create` | 不变 | `ContractSaveReqVO` 增加 `useTypeTemplate?: Boolean`：为 true 且该类型有范本时，创建后自动以范本文件生成 MAIN v1（sourceType=UPLOAD，remark='由类型范本创建'），并回填 currentDocumentVersionId。 |
| GET(改) | `/clm/contract/get`、`/page` | 不变 | `ContractRespVO` 增加：`sourceContractId`、`sourceContractTitle`、`sourceContractNo`、`relationType`；permissions 增加 `canArchive`（=主体可编辑 && approvalStatus==2 && lifecycle==2）、`canCopy`(=canView)。 |
| GET(改) | `/clm/contract-type/simple-list` | 不变 | item 增加 `hasTemplate: Boolean`、`description`。 |

DocumentVersionRespVO 不变；下载/预览均走既有 `/clm/document/version/download?id=`。

## 2. 前端

### 2.1 API（`src/api/clm/contract/index.ts` 归 FE-B 所有；`contractType/index.ts` 归 FE-A）
- contract/index.ts 新增导出：`copyContract(data: { sourceContractId: number; relationType: 'COPY' | 'RENEWAL'; title?: string }): Promise<number>`；`archiveContract(formData: FormData)`（request.upload → /clm/contract/archive，返回 CommonResult）；`previewVersionBlob(id: number): Promise<Blob>`（request.download /clm/document/version/download）。ContractVO/Permissions 接口补新字段。
- contractType/index.ts 新增：`uploadTypeTemplate(formData: FormData)`、`downloadTypeTemplate(typeId, fileName)`（blob 另存）、SimpleVO 补 `hasTemplate/description`。

### 2.2 起草中心 `src/views/clm/contract/draftCenter/index.vue`（FE-A）
name `ClmContractDraftCenter`（菜单 7040 已建，无需加路由）。仿一诺"融合起草中心"：
- 标题"融合起草中心"+ 副题；右上：我的草稿(→台账 query approvalStatus=0)、前往合同台账。
- 分组一"常规快速起草"两张大卡：**选择标准模板起草**（打开模板选择 Dialog：列出 getContractTypeSimpleList() 中 hasTemplate 的类型卡片[名称/描述/「预览下载」]，选中→push ClmContractCreate query `{ typeId, useTemplate: 1 }`）；**已有文件上传起草**（push ClmContractCreate query `{ upload: 1 }`）。
- 分组二"基于已有合同起草"两张卡：**复制已有合同** / **合同续签**——都打开选择合同 Dialog（getContractPage 搜索列表，续签默认筛 lifecycleStatus=3，行点选）→ 弹确认（可改标题，默认 源标题+（复制/续签））→ copyContract → push 详情。
- create 页配合（FE-A 改 `create/index.vue`）：读 query.typeId 预选类型；query.useTemplate='1' 时保存草稿请求带 useTypeTemplate:true，成功提示"已用类型范本生成正文 v1"；query.upload='1' 时保存后不直接跳详情，而是先弹上传正文对话框（复用详情页上传逻辑或直接跳详情并自动打开上传）。简化实现允许：upload=1 仅在保存成功后 message 提示"请在详情页上传合同正文"并跳详情。
- 台账 index.vue（FE-A）：顶部【新建】改为跳起草中心；加状态 Tab 行（全部/草稿(approvalStatus=0&lifecycle=1)/审批中(1)/审批通过(2&lifecycle=2)/已签订(lifecycle=3)/已驳回(3)），点击设置 queryParams 并 getList；列表"标题"列下方小字显示关联（relationType 存在时：`[复制|续签]自 {sourceContractNo}`）。
- 类型管理页（FE-A `contractType/index.vue`）：操作列加「范本」：Dialog 内上传/替换/下载范本（uploadTypeTemplate/downloadTypeTemplate），列表加"范本"列（有/无）。

### 2.3 详情页文档化改造 `src/views/clm/contract/detail/**`（FE-B，可整页重写）
布局对标一诺：
- **顶部横条**：返回按钮+标题+状态标签（approval/lifecycle dict-tag）+ 编号/类型/负责人小字；右侧动作按钮组（编辑/提交审批/撤销审批/上传盖章件归档/复制/续签/删除，按 permissions 显隐：canArchive→归档，canCopy→复制/续签）。下方**阶段条**（el-steps 横向小号）：`草稿 → 审批中 → 审批通过 → 已签订`，active 规则：lifecycle==3→4格全亮；approval==2→3；approval==1→2；否则 1；approval==3(驳回) 时第2步标 error 文案"已驳回"。
- **主体两栏**：左 60-65% 文档预览卡：工具条（当前版本 v{n} 下拉可切版本 / 文件名 / 下载按钮），预览区：`.docx` 用 `docx-preview` 的 `renderAsync(blob, container)`（已安装依赖 docx-preview，import { renderAsync } from 'docx-preview'）；`.pdf` 用 `URL.createObjectURL(blob)` + `<iframe>`；其他类型显示占位（图标+"该格式暂不支持预览，请下载查看"）。无正文时空态+【上传正文】按钮。
- 右 35-40% 信息面板（el-tabs 或锚点分节，保留现有五块内容但重组）：`基础信息`（descriptions 紧凑双列+签约方表+扩展字段只读 form-create+关联合同行：relationType 时显示"[复制/续签]自 源编号(链接跳源合同详情)"）/`文档版本`（版本表：v号/文件名/大小/来源 dict/冻结/时间/操作[预览(切换左侧)/下载]；上传正文新版本/上传附件按钮）/`参与人`/`审批记录`/`审计轨迹`——这五块尽量复用现有 components，只改容器与联动（版本表"预览"点击 emit 给左侧预览器）。
- 归档 Dialog（FE-B）：上传盖章件(文件必填)+签订/生效/到期日期三个可选日期 → archiveContract → 成功后刷新（阶段条到"已签订"）。
- 详情加载：预览默认当前版本；切换版本调 previewVersionBlob。
- 保留 BPM 视图组件 `bpm/index.vue` 不动。

### 2.4 工作台任务桶（FE-A，改 `src/views/Home/Index.vue` 的统计卡区域）
将四张卡扩为一诺式"任务中心"桶（横排 6 桶，点击跳台账对应 Tab/审批中心）：`待办审批`(todo total→/approval/todo)、`草稿`、`审批中`、`已驳回`、`待归档`(approval=2&lifecycle=2)、`已签订`(lifecycle=3)。数字接口全部用现有 page API pageSize=1 取 total（带 ownerUserId=me 的口径保持）。下方三个列表面板不动。

## 3. Mock 数据（DATA agent）
脚本 `scripts/seed-demo-data.ps1`（UTF-8 with BOM，PowerShell 5.1，参照 e2e-api.ps1 的 Login/Api/Upload 帮助函数）+ `scripts/gen_demo_docs.py`（python-docx）：
1. `pip install python-docx`；生成 8 份**原创通用条款**的合同 DOCX 到 `runtime/demo-docs/`（自拟条款文字，禁止摘抄现实范本）：采购合同范本、销售合同范本、技术服务合同范本、房屋租赁合同范本、劳务外包合同范本、保密协议范本、设备维保合同范本、供货补充协议；每份 2-4 页、含标题/编号占位/双方抬头/6-10 条条款/落款签章区，中文正式文风。
2. 用户 nickname 美化（PUT /system/user/update：yudao→"李静"、hrmgr→"王慧"、test→"赵晓敏"；不改 admin）。若 update 接口校验复杂可跳过并输出提示。
3. 合同类型 6 个（编码 code：purchase/sales/tech-service/lease/outsourcing/nda；名称：采购合同/销售合同/技术服务合同/房屋租赁合同/劳务外包合同/保密协议），各配 2-4 个扩展字段（如 采购合同：交付日期(date,必填)/验收标准(textarea)/质保期月数(number)；销售：回款账期天数/是否含运费；技术服务：服务周期/验收里程碑；租赁：租期月数/押金；外包：结算方式；NDA：保密期限年），发布 v1；上传对应范本 DOCX（template/upload）。
4. 签约方 12 个：我方 2（灵犀科技（杭州）有限公司、灵犀数智研究院），相对方 10（华南云创信息技术有限公司、启明智造设备股份有限公司、蓝湾建设工程集团、中恒律证咨询（北京）事务所、锦程物流供应链有限公司、天工精密仪器有限公司、绿洲物业服务集团、迅捷软件开发有限公司、恒信人力资源服务有限公司、卓远设计咨询工作室），带编造统一社会信用代码/联系人。
5. 合同 ~18 份，标题真实感（如"2026年度服务器采购合同""华东区白银销售框架合同"等按类型起名），金额 3万~860万 分布，签约方搭配合理；状态铺开：草稿 4、审批中 3（停在不同节点：1 个待法务[yudao]、2 个已过法务待负责人[admin]）、已驳回 1（驳回后未重提）、审批通过待归档 3、已签订(已归档,含盖章件 PDF 占位--用 python 生成简单 PDF 或用 docx 转?：直接再传一份 DOCX 也可，remark 盖章扫描件) 6、复制/续签关系至少 2 对（1 份"续签"自某已签订合同）。走真实 API：创建→传正文→提交→用对应审批人账号(密码统一 admin123，先重置)审批。
6. 收尾 SQL（脚本内直连 mysql 执行）：把本次创建的 clm_* 行的 create_time/update_time、clm_audit_event.occurred_at、bindings 时间随机回拨 1~75 天（保持相对顺序：同合同 audit 时间随 create_time 平移），让台账/审计像真实运营数据。
7. 全程输出 PASS/FAIL 摘要；可重复执行（编码用日期戳避重）。

## 4. 验收（主会话执行）
后端单测通过→重建→重启；前端 tsc/eslint 干净；跑 seed；浏览器走演示脚本：工作台桶→起草中心四路径→模板起草生成 v1→详情页左侧 DOCX 渲染→提交审批→审批→归档→已签订；截图留档。
