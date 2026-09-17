# CLM POC 在线编辑（ONLYOFFICE）实施规格 — 第二阶段 Gate

> 前提：基础闭环（上传/版本/权限/审批）已通过。本阶段只接 ONLYOFFICE Document Server；不做多人分支合并；"版本比较"按 README 13.2 明确降级为**并排查看 + 历史下载**，不包装成真正的 compare。
> 上一交付会话没有 Docker，Document Server 当时未部署：代码先用"回调模拟测试"验证后端链路；真实 Document Server 验收见 §6。

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
| GET | `/clm/online-edit/file?token=` | `verify(token,"file")` → `TenantUtils.execute(tenantId, ...)`，重新读取合同并执行 `assertCanView(contract, token.u)` 后读取版本字节流；因此参与人撤权后，尚未过期的 file token 也立即失效。`Content-Disposition: attachment`。**不写下载审计**（属于编辑会话的内部拉取），但记录 debug 日志。 |
| POST | `/clm/online-edit/callback?token=` | `verify(token,"callback")`；若 `jwt-secret` 非空，校验 body `token` 字段或 `Authorization` 头的 JWT 签名（不臆造尚未用真实 DS 固化的 claims 结构），失败返回 `{"error":1}`。回调 `key` 必须严格等于 token 目标版本的 `clm-v{versionId}-{sha256前12位}`。`status ∈ {2,6}` 时，只允许从与 `document-server-url` **同 scheme、host、有效端口**的 HTTP(S) URL 拉取，拒绝 userinfo、fragment、跨源 URL 和所有 3xx；流式读取上限 50 MiB。随后计算原始字节 `sha256` → **幂等**：先比较当前版本的原始 SHA；若 forcesave 与 close 重新封装 OOXML 导致 ZIP SHA 不同，则仅对同一父版本生成的 `.docx/.xlsx/.pptx` 比较全部解压条目的“名称 + 长度 + 内容 SHA”。该比较只忽略 ZIP 时间戳、压缩方式和条目顺序，不归一化 XML；重复条目、路径穿越、损坏包、超过 10,000 条目或 200 MiB 解压量时保守地视为不同。真实内容不同才新建版本（`sourceType=ONLINE_EDIT`、`parentVersionId = token.v`、`remark = "ONLYOFFICE 保存 (status=…)"`、creator = token.u 对应用户），更新 `document.currentVersionId`，MAIN 时更新 `contract.currentDocumentVersionId`；审计 `ONLINE_EDIT_SAVE`（actorUserId=token.u）。若合同此时 `approvalStatus == RUNNING` 或目标父版本已冻结/已非当前版本，仍然创建新版本（审批中禁止的是"打开编辑"，回调是对已开启会话的收尾，不能丢数据），但审计 detail 记 `lateSave=true`。`status` 其它值不拉文件，但仍校验 token/JWT/key。 |

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
- 单测 `OnlineEditServiceImplTest`：callback key 不匹配、SSRF scheme/host/port/userinfo、3xx、声明/分块超大响应、file token 使用时撤权、OOXML 容器元数据去重、真实条目变化、重复条目与路径穿越拒绝。回调 HTTP 对完整响应体使用 60 秒可取消 deadline，不能在收到响应头后无限慢速读取。
- 回调模拟测试若没有真实 Document Server，必须在 configured `document-server-url` 同源启动一个只提供测试 DOCX 的本地 HTTP stub；不能再把 `:48080` 后端 file URL 当作 callback `url`。流程仍为：登录 → 上传 → 取 config/file token → 从 file URL 验证开放接口 → 从 DS-origin stub 拉取另一份内容 → 签名 callback → 验证新版本/幂等/审批锁定。

## 6. 真实 Document Server 8.2 Gate

当前 Gate 严格采用交接文档指定的 Community 8.2，并锁定已验证含 `linux/arm64` 的多架构索引 digest；9.4 仅作为后续升级候选，不与本轮 Gate 混跑。

`infra/compose.onlyoffice.yaml`：
```yaml
services:
  onlyoffice:
    image: onlyoffice/documentserver:8.2@sha256:fb1c76177e578918f0d7ad51eda5006d728b9f2f071f93d18054c1f91edec78b
    container_name: clm-onlyoffice
    restart: unless-stopped
    environment:
      JWT_ENABLED: "true"
      JWT_SECRET: ${CLM_ONLYOFFICE_JWT_SECRET:?set in infra/.env}
      JWT_HEADER: Authorization
    ports: ["127.0.0.1:8090:80"]
    extra_hosts: ["host.docker.internal:host-gateway"]
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://127.0.0.1/healthcheck || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 60s
```

验收项：真实 DOCX 打开（中文字体/批注/修订/页眉页脚/表格）、保存回调生成新版本、并发两人编辑同一版本、历史版本只读打开、无权与撤权后的 file token 被拒绝、过期/篡改 token 被拒绝。冻结/审批中是**拒绝新开 edit config**；已打开会话的晚到 callback 仍接收并标记 `lateSave=true`。

2026-08-26 本机 Gate 结果：Community 8.2 容器健康；真实 4 页中文 DOCX 打开并保存成功；admin 与 yudao 同时连接时编辑器显示 2 位协作者；历史 v1 只读打开；撤权前同一 file token 返回 DOCX，撤权后立即返回 `1070007000`；forcesave 创建新版本后，close 回调的 OOXML 条目内容完全相同，仅 ZIP 时间戳变化，逻辑包去重后未再生成重复版本。后端 CLM 模块最终 52 项单测全通过，两个 E2E 脚本分别 57/61 项检查全通过。

`permissions.download=false` 只能关闭 ONLYOFFICE 的下载/打印 UI，不能单独构成防抓包安全边界。当前本机 Gate 已将 8090 仅绑定 loopback；正式部署还必须在反向代理/容器网络层将 `/clm/online-edit/file|callback` 限制为 Document Server 网络身份，并配置 TLS。社区版 AGPLv3 义务在交付前单独确认。
