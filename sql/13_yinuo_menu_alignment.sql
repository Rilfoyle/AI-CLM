SET NAMES utf8mb4;

-- =====================================================================
-- 一诺复刻第一版：菜单层级与业务术语对齐
--
-- 约束：
--   1. 只重排现有一期能力，不修改 API 权限标识、Vue 组件或后端 URL。
--   2. “页面布局配置”复用合同分类版本的表单设计能力，不创建第二份 schema。
--   3. 四个产品角色的菜单授权在本迁移中精确重建，新增父菜单不能扩大子能力。
--   4. 可重复执行；重复执行不会累积 system_role_menu 行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 新增一诺式业务分组和页面布局直达入口
-- ---------------------------------------------------------------------
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  (7502, '合同拟制', '', 1, 1, 7000, 'drafting', 'ep:edit-pen', '', '',
   0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7500, '基础设置', '', 1, 4, 7000, 'base-settings', 'ep:setting', '', '',
   0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7501, '工作交接事项', '', 1, 6, 7000, 'handover', 'ep:switch', '', '',
   0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7504, '页面布局配置', '', 2, 2, 7500, 'page-layout', 'ep:grid',
   'clm/contractType/layout/index', 'ClmContractTypeLayout',
   0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`),
  `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
  `icon` = VALUES(`icon`), `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`), `visible` = VALUES(`visible`), `keep_alive` = VALUES(`keep_alive`),
  `always_show` = VALUES(`always_show`), `updater` = '1', `update_time` = NOW(), `deleted` = b'0';

-- ---------------------------------------------------------------------
-- 2) 已有页面按一诺层级重排；权限标识和组件保持原值
-- ---------------------------------------------------------------------
UPDATE `system_menu`
SET `name` = '合同起草', `sort` = 1, `parent_id` = 7502, `path` = 'draft-center',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7040;

UPDATE `system_menu`
SET `name` = '合同查询', `sort` = 2, `parent_id` = 7502, `path` = 'contract',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7030;

UPDATE `system_menu`
SET `name` = '合同协同', `sort` = 3, `parent_id` = 7502, `path` = 'collaboration',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7060;

UPDATE `system_menu`
SET `name` = '审批管理', `sort` = 2, `parent_id` = 7000, `path` = 'approval-management',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7100;

UPDATE `system_menu`
SET `name` = '审批中心', `sort` = 1, `parent_id` = 7100, `path` = 'approval',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7070;

UPDATE `system_menu`
SET `name` = '工作流设置', `sort` = 2, `parent_id` = 7100, `path` = 'workflow-settings',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7101;

UPDATE `system_menu`
SET `name` = '流程定义', `sort` = 1, `parent_id` = 7101, `path` = 'process',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7311;

UPDATE `system_menu`
SET `name` = '业务单据流程配置', `sort` = 2, `parent_id` = 7101, `path` = 'routing',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7304;

UPDATE `system_menu`
SET `name` = '模板管理', `sort` = 3, `parent_id` = 7000, `path` = 'template',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7302;

UPDATE `system_menu`
SET `name` = '合同分类', `sort` = 1, `parent_id` = 7500, `path` = 'contract-type',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7010;

UPDATE `system_menu`
SET `name` = '编码规则设置', `sort` = 3, `parent_id` = 7500, `path` = 'numbering',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7303;

UPDATE `system_menu`
SET `name` = '数据权限规则', `sort` = 4, `parent_id` = 7500, `path` = 'permission',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7305;

UPDATE `system_menu`
SET `name` = '基础数据', `sort` = 5, `parent_id` = 7000, `path` = 'basic-data',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7102;

UPDATE `system_menu`
SET `name` = '相对方信息', `sort` = 1, `parent_id` = 7102, `path` = 'directory',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7020;

UPDATE `system_menu`
SET `name` = '相对方导入', `sort` = 2, `parent_id` = 7102, `path` = 'import',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7308;

UPDATE `system_menu`
SET `name` = '经办人变更', `sort` = 1, `parent_id` = 7501, `path` = 'owner-change',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7310;

UPDATE `system_menu`
SET `name` = '异常处理', `sort` = 7, `parent_id` = 7000, `path` = 'exceptions',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7103;

UPDATE `system_menu`
SET `name` = '配置异常', `sort` = 1, `parent_id` = 7103, `path` = 'issues',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7306;

UPDATE `system_menu`
SET `name` = '审批异常', `sort` = 2, `parent_id` = 7103, `path` = 'reconciliation',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7309;

UPDATE `system_menu`
SET `name` = '系统管理', `sort` = 8, `parent_id` = 7000, `path` = 'system',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7200;

-- 按钮名称同步业务术语；permission、parent_id 和动作语义保持不变。
UPDATE `system_menu`
SET `name` = CASE `id`
    WHEN 7011 THEN '合同分类查询'
    WHEN 7012 THEN '合同分类创建'
    WHEN 7013 THEN '合同分类更新'
    WHEN 7014 THEN '合同分类删除'
    WHEN 7015 THEN '合同分类发布'
    WHEN 7021 THEN '相对方信息查询'
    WHEN 7022 THEN '相对方信息创建'
    WHEN 7023 THEN '相对方信息更新'
    WHEN 7024 THEN '相对方信息删除'
    WHEN 7111 THEN '模板管理查询'
    WHEN 7112 THEN '模板管理维护'
    WHEN 7113 THEN '模板管理发布'
    WHEN 7121 THEN '编码规则设置查询'
    WHEN 7122 THEN '编码规则设置维护'
    WHEN 7123 THEN '编码规则设置发布'
    WHEN 7131 THEN '业务单据流程配置查询'
    WHEN 7132 THEN '业务单据流程配置维护'
    WHEN 7133 THEN '业务单据流程配置发布'
    WHEN 7141 THEN '数据权限规则查询'
    WHEN 7142 THEN '数据权限规则维护'
    WHEN 7143 THEN '数据权限规则发布'
    WHEN 7151 THEN '配置异常查询'
    WHEN 7152 THEN '配置异常处理'
    WHEN 7161 THEN '相对方导入查询'
    WHEN 7162 THEN '相对方导入'
    WHEN 7163 THEN '相对方导入确认'
    WHEN 7171 THEN '审批异常查询'
    WHEN 7172 THEN '审批异常业务确认'
    WHEN 7173 THEN '审批异常技术重放'
    WHEN 7181 THEN '经办人变更查询'
    WHEN 7182 THEN '活动任务重分配'
    WHEN 7191 THEN '流程定义查询'
    WHEN 7192 THEN '流程定义维护'
    WHEN 7193 THEN '流程定义发布'
  END,
  `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` IN (
  7011,7012,7013,7014,7015,7021,7022,7023,7024,
  7111,7112,7113,7121,7122,7123,7131,7132,7133,
  7141,7142,7143,7151,7152,7161,7162,7163,
  7171,7172,7173,7181,7182,7191,7192,7193
);

-- ---------------------------------------------------------------------
-- 3) 精确重建四个产品角色授权；父菜单只随已有子能力授予
-- ---------------------------------------------------------------------
DELETE rm
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
WHERE r.`tenant_id` = 1
  AND r.`code` IN ('clm_business', 'clm_legal', 'clm_contract_admin', 'clm_system_admin');

-- 业务经办人：合同拟制和本人审批；不授予工作流设置、基础设置或系统管理。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  7000, 7502, 7040, 7030, 7060, 7100, 7070,
  7080, 7081, 7082, 7083, 7084, 7085, 7086, 7087, 7088, 7089, 7090, 7091,
  7031, 7032, 7033, 7034, 7035, 7036, 7037,
  7061, 7062, 7063, 7066,
  7071, 7072, 7073, 7074, 7075, 7077, 7078
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_business' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 法务：合同查询、合同协同和本人审批；不授予合同起草或任何配置能力。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  7000, 7502, 7030, 7060, 7100, 7070,
  7080, 7083, 7084, 7085, 7086, 7087, 7088, 7089, 7090, 7091, 7031, 7033, 7036,
  7061, 7063, 7064, 7065,
  7071, 7072, 7073, 7074, 7075, 7076, 7077, 7078
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_legal' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 合同管理员：合同元数据及一期治理配置；仍无合同正文下载、编辑或提交权限。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  7000, 7502, 7030, 7100, 7070,
  7101, 7311, 7304, 7302,
  7500, 7010, 7504, 7303, 7305,
  7102, 7020, 7308, 7501, 7310, 7103, 7306, 7309,
  7080, 7081, 7031, 7083,
  7071, 7072, 7073, 7074, 7075, 7077, 7078,
  7011, 7012, 7013, 7014, 7015,
  7021, 7022, 7023, 7024,
  7111, 7112, 7113, 7121, 7122, 7123, 7131, 7132, 7133,
  7141, 7142, 7143, 7151, 7152, 7161, 7162, 7163,
  7171, 7172, 7181, 7182, 7191, 7192, 7193
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_contract_admin' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 系统管理员：组织、账号、集成和本人审批任务；审批异常仅保留技术重放视图。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  1, 100, 101, 103,
  1001, 1002, 1003, 1004, 1005, 1006, 1007,
  1008, 1009, 1010, 1011, 1012, 1063, 1064, 1065,
  1017, 1018, 1019, 1020,
  7000, 7100, 7070, 7080, 7071,
  7103, 7309, 7171, 7173,
  7200, 7401, 7402, 7403, 7211, 7212, 7221, 7222, 7223, 7231, 7232, 7233
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_system_admin' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 旧 common 角色继续不能兜底获得 CLM 菜单。
DELETE rm
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
WHERE r.`tenant_id` = 1 AND r.`code` = 'common'
  AND rm.`menu_id` BETWEEN 7000 AND 7999;
