# CLM POC 在线编辑（ONLYOFFICE）实施规格 — 第二阶段 Gate

> 前提：基础闭环（上传/版本/权限/审批）已通过。本阶段只接 ONLYOFFICE Document Server；不做多人分支合并；"版本比较"按 README 13.2 明确降级为**并排查看 + 历史下载**，不包装成真正的 compare。
> 本机无 Docker，Document Server 未部署：代码按本规格实现并用"回调模拟测试"验证后端链路；真实 Document Server 验收见 §6。

## 1. 配置（`yudao-server/src/main/resources/application-clm-local.yaml` 追加；密钥来自环境变量）

```yaml
clm:
  onlyoffice:
    enabled: ${CLM_ONLYOFFICE_ENABLED:true}
    document-server-url: ${CLM_ONLYOFFICE_URL:http://127.0.0.1:8090}      # 浏览器访问 Document Server 的地址
    jwt-secret: ${CLM_ONLYOFFICE_JWT_SECRET:change-me-local-only}           # 与 Document Server 的 JWT_SECRET 一致
    callback-base-url: ${CLM_ONLYOFFICE_CALLBACK_BASE:http://host.docker.internal:48080}  # Document Server 访问后端的地址
    token-ttl-seconds: 3600
```
`framework/onlyoffice/config/ClmOnlyOfficeProperties`（`@ConfigurationProperties("clm.onlyoffice")`）。

## 2. 访问令牌 `onlyoffice/OnlyOfficeTokenService`

HMAC-SHA256 签名的紧凑令牌（自实现，避免额外依赖；格式 `base64url(payloadJson) + "." + base64url(hmac)`），payload：`{ "v": versionId, "d": documentId, "c": contractId, "u": userId, "t": tenantId, "m": "edit"|"view", "p": "file"|"callback", "exp": epochSeconds }`。
- `issue(...)` / `verify(token, expectedPurpose)`（过期、签名错误、purpose 不符均抛 `ONLINE_EDIT_TOKEN_INVALID 1_070_008_001`）。
- Document Server 的 JWT（给编辑器 config 签名、校验回调 body 的 `token` 字段或 `Authorization: Bearer`）使用标准 HS256 JWT：用 hutool `cn.hutool.jwt.JWTUtil`（hutool-all 已在依赖树）。

## 3. 后端接口

### 3.1 受登录保护 `controller/admin/onlineedit/OnlineEditController`（`/clm/online-edit`）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/config?versionId=&mode=edit|view` | `clm:contract:update`(edit) / `clm:contract:query`(view) | 校验：版本存在；`mode=edit` 需 `access.assertCanEdit(contract)` 且 `!version.frozen` 且 `contract.approvalStatus != RUNNING`，且该版本必须是其文档的 **当前版本**（`document.currentVersionId == versionId`，否则 `ONLINE_EDIT_NOT_CURRENT_VERSION`）；`mode=view` 需 `assertCanView`。文件类型仅 docx/doc/xlsx/xls/pptx/ppt（否则 `ONLINE_EDIT_UNSUPPORTED_TYPE`）。审计 `ONLINE_EDIT_OPEN`。返回 `OnlineEditConfigRespVO`： |

```json
{
  "enabled": true,
  "documentServerUrl": "http://127.0.0.1:8090",
  "config": {
    "documentType": "word",                      // word / cell / slide 按扩展名
    "document": {
      "fileType": "docx",
      "key": "clm-v{versionId}-{sha256前12位}",     // 同一版本稳定；保存生成新版本 → 新 key
      "title": "<fileName>",
      "url": "{callbackBase}/admin-api/clm/online-edit/file?token=<file token>",
      "permissions": { "edit": true|false, "download": canDownload, "print": canDownload, "review": true, "comment": true }
    },
    "editorConfig": {
      "mode": "edit"|"view",
      "lang": "zh-CN",
      "callbackUrl": "{callbackBase}/admin-api/clm/online-edit/callback?token=<callback token>",   // 仅 edit
      "user": { "id": "<userId>", "name": "<nickname>" },
      "customization": { "forcesave": true, "autosave": true }
    }
  },
  "token": "<HS256 JWT over config>"            // Document Server 开启 JWT 时必需
}
```

### 3.2 供 Document Server 访问 `controller/admin/onlineedit/OnlineEditOpenController`（类级 `@TenantIgnore`，方法 `@PermitAll`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/clm/online-edit/file?token=` | `verify(token,"file")` → `TenantUtils.execute(tenantId, ...)` 读取版本字节流；`Content-Disposition: attachment`。**不写下载审计**（属于编辑会话的内部拉取），但记录 debug 日志。 |
| POST | `/clm/online-edit/callback?token=` | `verify(token,"callback")`；若 `jwt-secret` 非空，校验 body `token` 字段或 `Authorization` 头的 JWT（`JWTUtil.verify`），失败返回 `{"error":1}`。解析 body：`status`、`url`、`key`、`users`、`forcesavetype`。`status ∈ {2,6}` 时：用 `HttpUtil.downloadBytes(url)`（hutool）拉取新文件 → `sha256` → **幂等**：若该文档当前版本的 `checksumSha256 == sha256` 则不新建版本（返回 error 0）；否则新建版本（`sourceType=ONLINE_EDIT`、`parentVersionId = token.v`、`remark = "ONLYOFFICE 保存 (status=…)"`、creator = token.u 对应用户），更新 `document.currentVersionId`，MAIN 时更新 `contract.currentDocumentVersionId`；审计 `ONLINE_EDIT_SAVE`（actorUserId=token.u）。若合同此时 `approvalStatus == RUNNING` 或目标父版本已冻结且文档当前版本 != 父版本… 仍然创建新版本（审批中禁止的是"打开编辑"，回调是对已开启会话的收尾，不能丢数据），但审计 detail 记 `lateSave=true`。`status` 其它值只记日志。**始终**返回 `{"error":0}`（除 token/JWT 失败）。 |

两个开放接口必须经 `SecurityConfiguration` 的 `AuthorizeRequestsCustomizer` 放行（`buildAdminApi("/clm/online-edit/file")`、`/callback`）——同时保留 `@PermitAll` 注解。

### 3.3 版本并排查看（降级的"比较"）

无新后端接口：前端用两次 `/config?mode=view` 打开两个只读编辑器并排显示。

## 4. 前端

- `src/api/clm/onlineEdit/index.ts`：`getOnlineEditConfig(versionId, mode)`。
- `src/views/clm/contract/onlineEdit/index.vue`（路由 `ClmContractOnlineEdit`：`contract/online-edit?versionId=&mode=`，hidden，activeMenu `/clm/contract`）：
  1. 调 `getOnlineEditConfig`；`enabled=false` 或 `documentServerUrl` 不可达时显示 el-result 提示"未配置 ONLYOFFICE Document Server"。
  2. 动态注入 `<script src="{documentServerUrl}/web-apps/apps/api/documents/api.js">`（失败 → 提示）。
  3. `new window.DocsAPI.DocEditor('clm-oo-editor', { ...config, token, width:'100%', height:'100%', events: { onDocumentStateChange, onError, onRequestClose } })`。
  4. 顶部条：文件名、版本号、模式标签、按钮「返回合同」。
- `src/views/clm/contract/compare/index.vue`（路由 `ClmContractCompare`：`contract/compare?left=&right=`）：左右两个只读编辑器（各自 `DocEditor` 实例，placeholder id 不同），顶部显示 v{n} ↔ v{m}、sha256 与上传时间；标题明确写"并排查看（非内容级比较）"。
- `ContractDocuments.vue` 增加：对当前版本且 `permissions.canEdit && !frozen && approvalStatus != 1` 显示「在线编辑」；每个版本显示「预览」（view）；多选两个版本后「并排查看」。
- `.env.local`：`VITE_ONLYOFFICE_URL` 不需要（URL 由后端下发）。

## 5. 测试

- 单测 `OnlyOfficeTokenServiceTest`：签发/校验/过期/篡改。
- 集成验证脚本 `scripts/e2e-onlyoffice-callback.ps1`（无 Document Server 也可跑）：登录 → 上传 v1 → `GET /config?mode=edit` 取 `callbackUrl` 与 `document.url` → 从 `document.url` 直接 GET（应得到字节，证明开放接口 + token 可用；篡改 token 应 401/错误）→ 构造回调 `{"key":..,"status":2,"url":"<document.url 但指向另一份内容：先上传 v2 再取其 file token 的 url>","users":["1"]}` 并带 HS256 JWT → 期望 `{"error":0}` 且版本列表出现 `sourceType=ONLINE_EDIT` 的新版本；再次发送同一回调 → 不新增版本（幂等）；审批中时 `GET /config?mode=edit` 应被拒绝。

## 6. 真实 Document Server 验收（待有 Docker 的机器执行）

`infra/compose.onlyoffice.yaml`：
```yaml
services:
  onlyoffice:
    image: onlyoffice/documentserver:8.2   # 固定后记录 digest
    container_name: clm-onlyoffice
    environment:
      JWT_ENABLED: "true"
      JWT_SECRET: ${CLM_ONLYOFFICE_JWT_SECRET:?set in infra/.env}
      JWT_HEADER: Authorization
    ports: ["8090:80"]
    extra_hosts: ["host.docker.internal:host-gateway"]
```
验收项：真实 DOCX 打开（中文字体/批注/修订/页眉页脚/表格）、保存回调生成新版本、并发两人编辑同一版本、历史版本只读打开、无权/过期/冻结/审批中均被拒绝。社区版 AGPLv3 义务在交付前单独确认。
