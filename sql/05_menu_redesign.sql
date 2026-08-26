SET NAMES utf8mb4;

-- =====================================================================
-- CLM POC 菜单重构（与用户确认的方案，2026-08-24）：
--   首页(合同工作台) / 审批中心 / 合同管理 / 流程配置 / 系统管理(瘦身) / 基础设施(仅运维必需)
-- 仅改 parent/sort/visible/name，不删数据；恢复方法见文件末尾
-- =====================================================================

-- 1) 审批中心（待办/已办/我的流程/抄送）提升为顶级，紧跟首页
UPDATE `system_menu` SET `parent_id` = 0, `path` = '/approval', `icon` = 'ep:stamp', `sort` = 4,
       `updater` = '1', `update_time` = NOW()
WHERE `id` = 1200;

-- 2) 隐藏「发起流程」（合同统一从合同台账新建；后续接通用审批时再放开）
UPDATE `system_menu` SET `visible` = b'0', `updater` = '1', `update_time` = NOW() WHERE `id` = 2720;

-- 3) 合同管理排在审批中心之后
UPDATE `system_menu` SET `sort` = 6, `updater` = '1', `update_time` = NOW() WHERE `id` = 7000;

-- 4) 「工作流程」改名「流程配置」，常用配置项上移一级
UPDATE `system_menu` SET `name` = '流程配置', `sort` = 8, `updater` = '1', `update_time` = NOW() WHERE `id` = 1185;
UPDATE `system_menu` SET `parent_id` = 1185, `updater` = '1', `update_time` = NOW()
WHERE `id` IN (1193, 2714, 1209, 2721, 2724);   -- 流程模型/流程分类/用户分组/流程实例/流程任务
UPDATE `system_menu` SET `visible` = b'0', `updater` = '1', `update_time` = NOW()
WHERE `id` IN (1186, 1187, 2726, 2731);          -- 中间层「流程管理」+ 流程表单/监听器/表达式（开发向）

-- 5) 系统管理瘦身：隐藏 租户管理/OAuth2.0/三方登录/地区管理/短信/邮箱（保留 通知公告+站内信、审计日志）
UPDATE `system_menu` SET `visible` = b'0', `updater` = '1', `update_time` = NOW()
WHERE `id` IN (1224, 1261, 2447, 2083, 1093, 2130);

-- 6) 基础设施只留运维必需：定时任务(110)/文件管理(1243)/API 日志(1083)/监控中心(2740)
UPDATE `system_menu` SET `visible` = b'0', `updater` = '1', `update_time` = NOW()
WHERE `id` IN (115, 1255, 1070, 114, 116, 2525, 106);
-- 115 代码生成 / 1255 数据源配置 / 1070 代码生成案例 / 114 表单构建 / 116 API 接口 / 2525 WebSocket / 106 配置管理

-- 恢复示例：
-- UPDATE `system_menu` SET `visible` = b'1' WHERE `id` = 115;                          -- 恢复某入口
-- UPDATE `system_menu` SET `parent_id` = 1185, `path` = 'task', `sort` = 20 WHERE `id` = 1200;  -- 审批中心放回工作流程
-- UPDATE `system_menu` SET `parent_id` = 1186 WHERE `id` IN (1193,2714,1209,2721,2724);
-- UPDATE `system_menu` SET `name` = '工作流程', `sort` = 50 WHERE `id` = 1185;

-- 7) 审批中心内部排序：待办 > 已办 > 我的流程 > 抄送我的
UPDATE `system_menu` SET `sort` = 1 WHERE `id` = 1207;
UPDATE `system_menu` SET `sort` = 2 WHERE `id` = 1208;
UPDATE `system_menu` SET `sort` = 3 WHERE `id` = 1201;
UPDATE `system_menu` SET `sort` = 4 WHERE `id` = 2713;
