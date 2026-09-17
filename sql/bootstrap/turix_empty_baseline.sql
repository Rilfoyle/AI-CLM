SET NAMES utf8mb4;

-- =====================================================================
-- TuriX clean local baseline (bootstrap/reset only; never run as an upgrade)
--
-- This migration is intentionally destructive. It converts the upstream
-- database dump into one empty TuriX tenant and removes all sample tenants,
-- users, organizations, business rows, logs, demo OAuth clients and demo
-- tables. Product menus, the four CLM roles and the five fixed local accounts
-- are reconstructed below.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- Preserve the known local admin password hash before replacing sample users.
SET @turix_local_password = (
  SELECT `password` FROM `system_users` WHERE `id` = 1 LIMIT 1
);
SET @turix_local_password = COALESCE(
  @turix_local_password,
  '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG'
);

-- Fail closed when this bootstrap is accidentally run without the expected
-- fresh dump and product migrations. Silent partial initialization is unsafe.
DROP PROCEDURE IF EXISTS `turix_assert_bootstrap_prerequisites`;
DELIMITER $$
CREATE PROCEDURE `turix_assert_bootstrap_prerequisites`()
BEGIN
  IF (SELECT COUNT(*) FROM `system_tenant` WHERE `id` = 1) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TuriX bootstrap requires system_tenant id=1';
  END IF;
  IF (SELECT COUNT(*) FROM `system_users` WHERE `id` = 1) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TuriX bootstrap requires system_users id=1';
  END IF;
  IF (SELECT COUNT(*) FROM `system_role`
      WHERE `tenant_id` = 1 AND `deleted` = b'0'
        AND `code` IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TuriX bootstrap requires all four SQL09 product roles';
  END IF;
  IF (SELECT COUNT(DISTINCT `code`) FROM `system_role`
      WHERE `tenant_id` = 1 AND `deleted` = b'0'
        AND `code` IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin')) <> 4 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TuriX bootstrap requires one unique row per product role code';
  END IF;
  IF (SELECT COUNT(*) FROM `system_oauth2_client` WHERE `client_id` = 'default') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TuriX bootstrap requires the default OAuth client';
  END IF;
  IF (SELECT COUNT(*) FROM `infra_file_config` WHERE `id` = 4) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'TuriX bootstrap requires database file config id=4';
  END IF;
END$$
DELIMITER ;
CALL `turix_assert_bootstrap_prerequisites`();
DROP PROCEDURE `turix_assert_bootstrap_prerequisites`;

-- Clear every tenant-scoped business/runtime table. Identity and role tables
-- are rebuilt explicitly after the generic sweep. system_menu is global.
DROP PROCEDURE IF EXISTS `turix_clear_tenant_rows`;
DELIMITER $$
CREATE PROCEDURE `turix_clear_tenant_rows`()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE table_name_value VARCHAR(128);
  DECLARE table_cursor CURSOR FOR
    SELECT DISTINCT c.`TABLE_NAME`
    FROM `information_schema`.`COLUMNS` c
    JOIN `information_schema`.`TABLES` t
      ON t.`TABLE_SCHEMA` = c.`TABLE_SCHEMA`
     AND t.`TABLE_NAME` = c.`TABLE_NAME`
     AND t.`TABLE_TYPE` = 'BASE TABLE'
    WHERE c.`TABLE_SCHEMA` = DATABASE()
      AND c.`COLUMN_NAME` = 'tenant_id'
      AND c.`TABLE_NAME` NOT IN (
        'system_dept',
        'system_role',
        'system_role_menu',
        'system_user_role',
        'system_users'
      );
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN table_cursor;
  clear_loop: LOOP
    FETCH table_cursor INTO table_name_value;
    IF done = 1 THEN
      LEAVE clear_loop;
    END IF;
    SET @clear_sql = CONCAT('DELETE FROM `', REPLACE(table_name_value, '`', '``'), '`');
    PREPARE clear_statement FROM @clear_sql;
    EXECUTE clear_statement;
    DEALLOCATE PREPARE clear_statement;
  END LOOP;
  CLOSE table_cursor;
END$$
DELIMITER ;
CALL `turix_clear_tenant_rows`();
DROP PROCEDURE `turix_clear_tenant_rows`;

-- One local tenant only.
DELETE FROM `system_tenant` WHERE `id` <> 1;
UPDATE `system_tenant`
SET `name` = 'TuriX',
    `contact_user_id` = 1,
    `contact_name` = 'TuriX 管理员',
    `contact_mobile` = '',
    `status` = 0,
    `websites` = '127.0.0.1:3000,localhost:3000',
    `package_id` = 0,
    `expire_time` = '2099-12-31 23:59:59',
    `account_count` = 100,
    `updater` = '1',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 1;
DELETE FROM `system_tenant_package`;

-- Clean product organization.
DELETE FROM `system_dept`;
INSERT INTO `system_dept`
  (`id`,`name`,`parent_id`,`sort`,`leader_user_id`,`phone`,`email`,`status`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  (100, 'TuriX',       0,   0, 1,      '', '', 0, '1', NOW(), '1', NOW(), b'0', 1),
  (101, '合同业务部', 100, 10, 200001, '', '', 0, '1', NOW(), '1', NOW(), b'0', 1),
  (102, '法务部',     100, 20, 200002, '', '', 0, '1', NOW(), '1', NOW(), b'0', 1),
  (103, '合同管理部', 100, 30, 200003, '', '', 0, '1', NOW(), '1', NOW(), b'0', 1),
  (104, '信息技术部', 100, 40, 200004, '', '', 0, '1', NOW(), '1', NOW(), b'0', 1);

-- Keep only the platform super administrator and the four product roles that
-- sql/09_yinuo_v1_roles_menus.sql has already created and authorized.
DELETE FROM `system_role`
WHERE `tenant_id` <> 1
   OR `code` NOT IN (
     'super_admin', 'clm_business', 'clm_legal',
     'clm_contract_admin', 'clm_system_admin'
   );
UPDATE `system_role`
SET `name` = 'TuriX 超级管理员', `sort` = 1, `data_scope` = 1,
    `data_scope_dept_ids` = '', `status` = 0, `type` = 1,
    `remark` = 'TuriX 本地平台超级管理员', `updater` = '1',
    `update_time` = NOW(), `deleted` = b'0'
WHERE `tenant_id` = 1 AND `code` = 'super_admin';

-- Five deterministic local accounts. All use admin123 in the local baseline;
-- production deployments must rotate these passwords immediately.
DELETE FROM `system_users`;
INSERT INTO `system_users`
  (`id`,`username`,`password`,`nickname`,`remark`,`dept_id`,`post_ids`,`email`,
   `mobile`,`sex`,`avatar`,`status`,`login_ip`,`login_date`,`creator`,`create_time`,
   `updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  (1,      'admin',         @turix_local_password, 'TuriX 管理员', '本地平台管理员', 100, '[]', '', '', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 1),
  (200001, 'business',      @turix_local_password, '业务经办人',   '一期产品角色账号', 101, '[]', '', '', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 1),
  (200002, 'legal',         @turix_local_password, '法务',         '一期产品角色账号', 102, '[]', '', '', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 1),
  (200003, 'contractadmin', @turix_local_password, '合同管理员',   '一期产品角色账号', 103, '[]', '', '', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 1),
  (200004, 'systemadmin',   @turix_local_password, '系统管理员',   '一期产品角色账号', 104, '[]', '', '', 0, '', 0, '', NULL, '1', NOW(), '1', NOW(), b'0', 1);
ALTER TABLE `system_users` AUTO_INCREMENT = 200005;

DELETE FROM `system_user_role`;
INSERT INTO `system_user_role`
  (`user_id`,`role_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 1, `id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'super_admin' AND `deleted` = b'0'
UNION ALL
SELECT 200001, `id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_business' AND `deleted` = b'0'
UNION ALL
SELECT 200002, `id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_legal' AND `deleted` = b'0'
UNION ALL
SELECT 200003, `id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_contract_admin' AND `deleted` = b'0'
UNION ALL
SELECT 200004, `id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_system_admin' AND `deleted` = b'0';

-- Remove every upstream feature/menu. The super administrator therefore sees
-- only the CLM product plus the minimum user/role/department administration.
DELETE FROM `system_menu`
WHERE `id` NOT BETWEEN 7000 AND 7999
  AND `id` NOT IN (
    1, 100, 101, 103,
    1001, 1002, 1003, 1004, 1005, 1006, 1007,
    1008, 1009, 1010, 1011, 1012,
    1017, 1018, 1019, 1020,
    1063, 1064, 1065
  );
DELETE rm
FROM `system_role_menu` rm
LEFT JOIN `system_role` r ON r.`id` = rm.`role_id`
LEFT JOIN `system_menu` m ON m.`id` = rm.`menu_id`
WHERE r.`id` IS NULL
   OR m.`id` IS NULL
   OR r.`tenant_id` <> 1
   OR r.`code` NOT IN ('clm_business','clm_legal','clm_contract_admin','clm_system_admin');

-- Contract workflow category only; no OA sample category or process model.
DELETE FROM `bpm_category`;
INSERT INTO `bpm_category`
  (`name`,`code`,`description`,`status`,`sort`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  ('合同管理', 'contract', 'TuriX 合同审批流程', 0, 1, '1', NOW(), '1', NOW(), b'0', 1);

-- The default OAuth client is required by username/password login. Keep one
-- local client, remove all SSO demos and upstream branding.
DELETE FROM `system_oauth2_access_token`;
DELETE FROM `system_oauth2_refresh_token`;
DELETE FROM `system_oauth2_approve`;
DELETE FROM `system_oauth2_code`;
DELETE FROM `system_oauth2_client` WHERE `client_id` <> 'default';
UPDATE `system_oauth2_client`
SET `secret` = SHA2(CONCAT(UUID(), UUID(), NOW(6), RAND()), 256),
    `name` = 'TuriX 管理端',
    `logo` = '',
    `description` = 'TuriX 本地管理端登录客户端',
    `status` = 0,
    `redirect_uris` = '[]',
    `authorized_grant_types` = '[]',
    `scopes` = '[]',
    `auto_approve_scopes` = '[]',
    `authorities` = '[]',
    `resource_ids` = '[]',
    `additional_information` = '{}',
    `updater` = '1',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `client_id` = 'default';

-- Remove global communication examples and any stored upstream message/file
-- payloads that are not covered by the generic tenant_id sweep.
DELETE FROM `system_mail_log`;
DELETE FROM `system_mail_template`;
DELETE FROM `system_mail_account`;
DELETE FROM `system_sms_log`;
DELETE FROM `system_sms_template`;
DELETE FROM `system_sms_channel`;
DELETE FROM `system_notify_template`;
DELETE FROM `infra_file_content`;
DELETE FROM `infra_file`;

-- Keep one working database-backed file store without upstream cloud samples.
DELETE FROM `infra_file_config` WHERE `id` <> 4;
UPDATE `infra_file_config`
SET `name` = 'TuriX 数据库存储',
    `remark` = '',
    `master` = b'1',
    `config` = '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"http://127.0.0.1:48080"}',
    `updater` = '1',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = 4;

-- Remove global generator/job/config samples while retaining the two settings
-- required by account management and registration control.
DELETE FROM `infra_codegen_column`;
DELETE FROM `infra_codegen_table`;
DELETE FROM `infra_job_log`;
DELETE FROM `infra_job`;
DELETE FROM `infra_config`
WHERE `config_key` NOT IN ('system.user.init-password', 'system.user.register-enabled');
UPDATE `infra_config`
SET `category` = 'system', `type` = 1, `name` = '账号初始密码',
    `value` = 'admin123', `visible` = b'0',
    `remark` = 'TuriX 本地初始化密码；生产环境必须修改',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `config_key` = 'system.user.init-password';
UPDATE `infra_config`
SET `category` = 'system', `type` = 1, `name` = '用户注册开关',
    `value` = 'false', `visible` = b'0', `remark` = '关闭公开注册',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `config_key` = 'system.user.register-enabled';

-- Retain only dictionaries used by TuriX account/role administration, the
-- contract workflow and CLM screens. The upstream dump contains hundreds of
-- unrelated OA, mall, CRM, ERP, IoT, MES, WMS and IM dictionary records.
-- system_data_scope is present as data in the upstream dump without a matching
-- type row, so reconstruct that product dependency explicitly.
INSERT INTO `system_dict_type`
  (`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
VALUES
  (1070000, '数据范围', 'system_data_scope', 0, 'TuriX 角色数据权限', '1', NOW(), '1', NOW(), b'0', NULL)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `status` = 0, `remark` = VALUES(`remark`),
  `updater` = '1', `update_time` = NOW(), `deleted` = b'0', `deleted_time` = NULL;

DELETE FROM `system_dict_data`
WHERE `dict_type` NOT IN (
  'common_status',
  'system_user_sex',
  'system_role_type',
  'system_data_scope',
  'bpm_model_type',
  'bpm_model_form_type',
  'bpm_task_candidate_strategy',
  'bpm_process_instance_status',
  'bpm_task_status',
  'bpm_process_listener_type',
  'bpm_process_listener_value_type'
)
AND `dict_type` NOT LIKE 'clm\_%';
DELETE FROM `system_dict_type`
WHERE `type` NOT IN (
  'common_status',
  'system_user_sex',
  'system_role_type',
  'system_data_scope',
  'bpm_model_type',
  'bpm_model_form_type',
  'bpm_task_candidate_strategy',
  'bpm_process_instance_status',
  'bpm_task_status',
  'bpm_process_listener_type',
  'bpm_process_listener_value_type'
)
AND `type` NOT LIKE 'clm\_%';
DELETE d
FROM `system_dict_data` d
LEFT JOIN `system_dict_type` t ON t.`type` = d.`dict_type` AND t.`deleted` = b'0'
WHERE t.`id` IS NULL;

-- Drop code-generator demonstration tables supplied by the upstream dump.
DROP TABLE IF EXISTS `yudao_demo01_contact`;
DROP TABLE IF EXISTS `yudao_demo02_category`;
DROP TABLE IF EXISTS `yudao_demo03_course`;
DROP TABLE IF EXISTS `yudao_demo03_grade`;
DROP TABLE IF EXISTS `yudao_demo03_student`;

SET FOREIGN_KEY_CHECKS = 1;
