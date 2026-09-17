# MIF Revision Map

| Feedback | Type | Changes original design? | Smallest change | Files |
|---|---|---:|---|---|
| 不使用“芋道源码”租户登录 | ia / backend | no | 将本地默认租户固定为 `TuriX`，并提供带破坏性确认门槛的本地初始化 | `frontend/.env*`、登录组件、`sql/bootstrap/turix_empty_baseline.sql`、`scripts/reset-turix-local.ps1` |
| 去掉芋道相关内容 | copy / visual | no | 清除用户可见品牌、登录元信息、水印和流程模型展示文案；保留底层兼容包名与开源许可 | `frontend/index.html`、登录组件、后端配置、BPM 模型、交付文档 |
| 去掉 mock / 演示数据 | state / backend | yes | 清空上游示例租户、用户、组织、消息日志和全部 CLM 业务样例，仅保留运行配置与四角色空租户 | `sql/12_*`、初始化/验证脚本、演示指南 |

## Module Impact

- Active / affected module：登录、租户身份、产品品牌、初始化数据基线。
- `plan.md` update：第一版实施结果保留；最终基线由“固定演示合同”改为“TuriX 空业务租户”。
- `prd.md` update：不修改核心对象、角色、流程或一期边界。
- Artifact root：`docs/yinuo-replica/`。
- Open item update：真实客户公司名未单独提供，本地自有租户采用项目品牌 `TuriX`，并允许脚本参数覆盖。

## Apply Now

- 默认登录租户与用户可见品牌统一为 TuriX。
- 建立幂等的干净租户 SQL 和跨平台执行/验证脚本。
- 删除当前本地数据库中的上游样例租户及全部业务 mock 数据。
- 建立业务、法务、合同管理员、系统管理员四个本地产品账号，不创建合同、参与方、模板或测试流程实例。
- 重新验证登录、菜单裁剪、空态、角色隔离和前后端构建。

## Hold / Needs Decision

- 底层 `cn.iocoder.yudao` Java 包名、Maven artifactId 和上游版权注释不做全仓重命名；它们不出现在产品界面，贸然修改会扩大构建与升级风险。
- 开源许可和第三方归属说明依法保留，不作为产品品牌展示。
