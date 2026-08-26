# AI-CLM · TuriX 合同管理系统（POC → 可演示版）

> 对标甄零一诺（OneContract）的企业合同管理系统，基于 RuoYi-Vue-Pro 单体二次开发。
> 本仓库是**全量快照**（根工程 + backend + frontend 源码），当前状态：**核心闭环全部验证通过、演示数据就绪、可直接演示**。
> 本 README 同时是给下一个 AI/开发会话的交接文档。原始立项交接见 `docs/UPSTREAM_HANDOVER.md`。

## 1. 现在做到了什么（均已实测验证）

- **主流程闭环**：融合起草中心（模板起草/上传起草/复制/续签）→ 合同草稿 → 上传不可变正文版本（SHA-256）→ 提交审批（绑定准确版本+表单快照，businessKey=binding id）→ BPM 两级审批（通过/驳回/撤销/重提）→ 上传盖章件归档 → 已签订。E2E 脚本 `scripts/e2e-api.ps1` 57 项断言、`scripts/e2e-onlyoffice-callback.ps1` 61 项断言全部通过；后端单测 41 项全绿。
- **界面对标一诺**：首页=合同工作台（任务计数桶+待办/合同/审批面板）；详情页文档为中心（docx-preview 浏览器直渲正文 + 右侧五页签 + 顶部阶段条 草稿→审批中→审批通过→已签订）；台账状态页签；角色化瘦身菜单（首页/审批中心/合同管理/流程配置/系统管理/基础设施）。
- **权限**：合同级 ACL（负责人/参与人/流程参与人），列表在 Mapper 层过滤，无超管旁路；追加式审计覆盖全生命周期。
- **表单**：合同类型版本化 FormCreate 扩展字段（发布后不可变），服务端 schema 校验（必填/未知字段）。
- **在线编辑**：ONLYOFFICE 适配代码完成（令牌保护的开放接口、幂等保存回调），已用回调模拟验证链路；真实 Document Server 未部署（本机无 Docker）。
- **演示数据**：6 类合同类型（带原创拟写的范本 DOCX）、12 家签约方、19 份合同铺满各生命周期阶段、真实审批轨迹、时间回拨成近两月分布。品牌已换为 TuriX（名称/logo/favicon/版权）。

## 2. 仓库结构

```
├── README.md                ← 本交接文档
├── docs/                    ← 全部设计与过程文档（重要，按需精读）
│   ├── UPSTREAM_HANDOVER.md   原始立项交接（锁定决策/验收标准/止损条件）
│   ├── CLM_POC_SPEC.md        核心 API 契约、状态机、权限规则（唯一契约）
│   ├── CLM_DEMO_SPEC.md       演示版改造契约（复制/续签/归档/范本/起草中心/详情页）
│   ├── CLM_ONLYOFFICE_SPEC.md 在线编辑设计 + 真实 Document Server 验收清单
│   ├── DELIVERY.md            POC 交付说明（验证证据、已知问题、下一步）
│   ├── DEMO_GUIDE.md          15 分钟演示动线 + 账号
│   ├── USER_GUIDE.md          界面操作指南（建相对方/表单/审批流/合同全流程）
│   ├── YINUO_REFERENCE.md     一诺测试环境实地梳理（三视角）+ 对标结论
│   ├── REQUIREMENTS_COVERAGE.md 客户 18 条需求覆盖度（✅3 🟡8 🟠4 🔴3）
│   ├── RUNBOOK_WINDOWS.md     本机运行手册（启动/重建/重置库/两个环境坑）
│   └── upstream-maps/         RuoYi-Vue-Pro 代码约定深度地图（二开必读）
├── sql/01..07_*.sql         ← 按序可重放：BPM 表/CLM 表/菜单字典/菜单裁剪/重构/演示升级/流程菜单优化
├── scripts/                 ← create-bpm-model / e2e-api / e2e-onlyoffice-callback /
│                              seed-demo-data / cleanup-e2e-data / gen_demo_docs.py
├── infra/                   ← env.ps1、start-*.ps1、build-backend.ps1、init-db.ps1、
│                              my.ini、maven-settings.xml、compose.onlyoffice.yaml（.env 不入库）
├── backend/                 ← ruoyi-vue-pro v2026.07(jdk17/21) @ec3f7cbf + 自建 yudao-module-clm
└── frontend/                ← yudao-ui-admin-vue3 v2026.07 @d4b521a1 + src/{api,views}/clm、工作台、品牌
```

> 注意：backend/frontend 在开发机上还是独立 git 克隆（分支 `clm-poc`，保留上游历史便于 diff/升级）；本仓库收录的是其工作区快照（不含上游 .git）。上游固定 commit 见上；不要跟 master。

## 3. 快速启动（Windows，无 Docker；其他平台参照改 infra 脚本）

前置：JDK 17、Maven 3.9.x（配 `infra/maven-settings.xml` 阿里云镜像）、MySQL 8.0.x、Redis、Node ≥20 + pnpm。

```powershell
# 1) infra/.env（不入库，需自建）：CLM_DB_PASSWORD=xxx 及可选 CLM_ONLYOFFICE_* 变量，参照 infra/env.ps1 读取的键
powershell -File infra\start-mysql.ps1      # 初始化并启动 MySQL（首次自动 initialize + 设密码 + 建库）
powershell -File infra\start-redis.ps1
powershell -File infra\init-db.ps1 -Force   # 导入 上游基础SQL + sql/01..07（ACT_* 由后端首启自动建）
powershell -File infra\build-backend.ps1    # mvn -pl yudao-server -am clean package
powershell -File infra\start-backend.ps1    # 48080
cd frontend && pnpm install --frozen-lockfile && cd ..
powershell -File infra\start-frontend.ps1   # 3000
powershell -File scripts\create-bpm-model.ps1   # 部署审批流程 clm_contract_approval_v1
python scripts\gen_demo_docs.py                 # 生成范本 DOCX（需 pip install python-docx）
powershell -File scripts\seed-demo-data.ps1     # 灌演示数据（走真实 API）
```

登录 `http://127.0.0.1:3000`：租户 `芋道源码`，`admin/admin123`（终审）、`yudao/admin123`(法务一审)、`test/admin123`(无权用户)。验证：`mvn -pl yudao-module-clm test`、`scripts/e2e-api.ps1`。

## 4. 关键设计决策（改代码前必读，勿推翻）

1. 合同是第一业务轴，无"合同申请"对象；`approval_status`(0-4 与 BPM 实例状态同值) 与 `lifecycle_status`(1 草稿/2 审批通过/3 已签订/4 到期/5 终止/6 作废) 两条正交状态轴。
2. `clm_workflow_binding` 一次流程一条，businessKey=binding id（非合同 id）；监听器按 businessKey+processInstanceId 双校验，不限定单一流程 key。
3. 文档版本永不覆盖；提交审批即冻结并复核 SHA-256；内容存 CLM 私有表 `clm_document_blob`（不走 infra 的 @PermitAll 文件路由，上游默认文件主配置本身是坏的占位 S3）。
4. 对象权限只认 负责人/参与人/流程参与人，super_admin 无旁路；列表过滤在 Mapper SQL 里。
5. 错误码段 `1_070_*`；菜单 id 7000-7999；字典 `clm_*`；所有 DO 继承 TenantBaseDO（上游租户拦截器默认拦所有表，新表必须带 tenant_id）。
6. BPM 建表 SQL 是从 MIT 许可的实体类自行推导的（`sql/01`），官方 SQL 有星球会员限制 —— **商用交付前需确认授权或替换 Warm-Flow**（唯一法务阻塞）。

## 5. 环境坑（浪费过时间的，别再踩）

- 本机 JDK17 `Pipe.open()` 走 AF_UNIX，默认 Temp 目录下 connect 失败：所有 JVM 必须带 `-Djdk.net.unixdomain.tmpdir=<短路径>`（infra 脚本已内置；surefire 用 `-DargLine` 传）。
- PowerShell 5.1 会拆分带点参数（`-Dmaven.x=y`、`-h127.0.0.1`）→ 引号包裹或走 `cmd /c`；无 BOM 的 .ps1 中文按 GBK 读 → 所有脚本已存为 UTF-8 with BOM。
- git-bash 的 curl 内联中文 JSON 会乱码 → 请求体写入 UTF-8 文件再 `--data-binary @file`。
- 前端 v2026.07 调用的 `/bpm/comment/*` 在后端该 tag（实为 2026.06 内容）不存在：流程详情页会弹一条"工作流模块已禁用"提示，无害。

## 6. 待办与下一步（按优先级）

1. **ONLYOFFICE Gate**：在有 Docker 的机器 `docker compose -f infra/compose.onlyoffice.yaml up -d`，按 `docs/CLM_ONLYOFFICE_SPEC.md §6` 验收真实 DOCX 打开/保存回调/并发。
2. **BPM SQL 授权决策**（见 §4.6）。
3. 客户需求 🟡 批（约 1 周）：编码规则拼我方简称、到期/归档提醒（Quartz+站内信）、相对方 Excel 批量导入、重大承诺必填配置化——清单与方案见 `docs/REQUIREMENTS_COVERAGE.md`。
4. 🔴 批需先决策：钉钉对接（凭证/主数据归属/免登）、审批中可编辑（字段级权限+改动留痕方案）。
5. 工程：补 ContractServiceImpl/ContractWorkflowServiceImpl 单测；E2E 接 CI；生产化安全加固（默认口令、开放接口网络限制）。
