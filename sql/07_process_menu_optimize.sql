SET NAMES utf8mb4;

-- =====================================================================
-- 流程配置菜单优化（参考一诺：设计项与运维项分层，名称去技术化）
--   流程配置
--   ├─ 审批流程设计（原 流程模型 1193）
--   ├─ 流程分类（2714）
--   ├─ 审批人分组（原 用户分组 1209）
--   └─ 运行监控（新 7050，二级目录）
--      ├─ 流程实例（2721，排障）
--      └─ 流程任务（2724，排障）
-- =====================================================================

-- 1) 改名 + 排序
UPDATE `system_menu` SET `name` = '审批流程设计', `sort` = 1, `updater` = '1', `update_time` = NOW() WHERE `id` = 1193;
UPDATE `system_menu` SET `sort` = 2, `updater` = '1', `update_time` = NOW() WHERE `id` = 2714;
UPDATE `system_menu` SET `name` = '审批人分组', `sort` = 3, `updater` = '1', `update_time` = NOW() WHERE `id` = 1209;

-- 2) 新增「运行监控」二级目录并把实例/任务挪进去
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7050, '运行监控', '', 1, 90, 1185, 'monitor', 'ep:data-line', NULL, NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7050);

UPDATE `system_menu` SET `parent_id` = 7050, `sort` = 1, `updater` = '1', `update_time` = NOW() WHERE `id` = 2721;  -- 流程实例
UPDATE `system_menu` SET `parent_id` = 7050, `sort` = 2, `path` = 'process-task', `updater` = '1', `update_time` = NOW() WHERE `id` = 2724;  -- 流程任务（顺手修上游 path 拼写 process-tasnk）

-- 3) 普通角色授权到新目录（与其余 clm 菜单口径一致）
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 2, 7050, '1', NOW(), '1', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE `role_id`=2 AND `menu_id`=7050 AND `deleted`=b'0');

-- 恢复：
-- UPDATE `system_menu` SET `name`='流程模型' WHERE `id`=1193;
-- UPDATE `system_menu` SET `name`='用户分组' WHERE `id`=1209;
-- UPDATE `system_menu` SET `parent_id`=1185, `sort`=10 WHERE `id`=2721;
-- UPDATE `system_menu` SET `parent_id`=1185, `sort`=11, `path`='process-tasnk' WHERE `id`=2724;
-- UPDATE `system_menu` SET `visible`=b'0' WHERE `id`=7050;
