SET NAMES utf8mb4;

-- =====================================================================
-- 一诺复刻第一版：CLM 审批方案设计入口与合同管理员授权
--
-- 只增加治理菜单与权限；发布新定义后，已运行流程实例仍继续使用原定义，
-- 本迁移不修改流程定义、流程实例或合同类型绑定。
-- =====================================================================

-- 审批方案位于编号规则之后；原审批路由、合同权限顺延。
UPDATE `system_menu`
SET `sort` = 5, `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7304;

UPDATE `system_menu`
SET `sort` = 6, `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7305;

INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (7311, '审批方案', '', 2, 4, 7101, 'process', 'ep:share',
   'clm/governance/process/index', 'ClmGovernanceProcess',
   0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7191, '审批方案查询', 'clm:governance:process:query', 3, 1, 7311, '', '', '', '',
   0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7192, '审批方案维护', 'clm:governance:process:update', 3, 2, 7311, '', '', '', '',
   0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7193, '审批方案发布', 'clm:governance:process:publish', 3, 3, 7311, '', '', '', '',
   0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`),
  `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
  `icon` = VALUES(`icon`), `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`), `visible` = VALUES(`visible`), `keep_alive` = VALUES(`keep_alive`),
  `always_show` = VALUES(`always_show`), `updater` = '1', `update_time` = NOW(), `deleted` = b'0';

-- 恢复既有软删除授权，再补齐缺失授权，保证可重复执行。
UPDATE `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
SET rm.`deleted` = b'0', rm.`updater` = '1', rm.`update_time` = NOW()
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_contract_admin' AND r.`deleted` = b'0'
  AND rm.`tenant_id` = 1 AND rm.`menu_id` IN (7311, 7191, 7192, 7193);

INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (7311, 7191, 7192, 7193) AND m.`deleted` = b'0'
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_contract_admin' AND r.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM `system_role_menu` rm
    WHERE rm.`tenant_id` = 1 AND rm.`role_id` = r.`id` AND rm.`menu_id` = m.`id`
  );
