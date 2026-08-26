SET NAMES utf8mb4;

-- =====================================================================
-- 演示版升级（对标一诺主流程）：合同关联、类型范本、生命周期文案、归档动作
-- =====================================================================

-- 1) 合同关联（复制/续签来源）
ALTER TABLE `clm_contract`
  ADD COLUMN `source_contract_id` bigint NULL COMMENT '来源合同编号（复制/续签）' AFTER `current_binding_id`,
  ADD COLUMN `relation_type` varchar(32) NULL COMMENT '与来源合同的关系：COPY 复制 / RENEWAL 续签' AFTER `source_contract_id`;

-- 2) 合同类型绑定范本文件（模板起草用）
ALTER TABLE `clm_contract_type`
  ADD COLUMN `template_file_key` varchar(255) NULL COMMENT '范本文件存储键（ClmDocumentStorage）' AFTER `current_version_id`,
  ADD COLUMN `template_file_name` varchar(255) NULL COMMENT '范本文件名' AFTER `template_file_key`;

-- 3) 生命周期文案对齐一诺（值不变，仅改显示）
UPDATE `system_dict_data` SET `label` = '审批通过' WHERE `dict_type` = 'clm_lifecycle_status' AND `value` = '2';
UPDATE `system_dict_data` SET `label` = '已签订'   WHERE `dict_type` = 'clm_lifecycle_status' AND `value` = '3';

-- 4) 新增字典：合同关联类型 + 归档审计动作
INSERT INTO `system_dict_type` (`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`)
SELECT 1070011, 'CLM 合同关联类型', 'clm_contract_relation_type', 0, NULL, '1', NOW(), '1', NOW(), b'0', NULL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'clm_contract_relation_type');

INSERT INTO `system_dict_data` (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1070211, 1, '复制', 'COPY', 'clm_contract_relation_type', 0, 'info', '', NULL, '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='clm_contract_relation_type' AND `value`='COPY');
INSERT INTO `system_dict_data` (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1070212, 2, '续签', 'RENEWAL', 'clm_contract_relation_type', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='clm_contract_relation_type' AND `value`='RENEWAL');
INSERT INTO `system_dict_data` (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 1070202, 12, '归档定稿', 'CONTRACT_ARCHIVE', 'clm_audit_action', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type`='clm_audit_action' AND `value`='CONTRACT_ARCHIVE');

-- 5) 菜单：合同管理下新增「起草中心」（放在台账前面）
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
SELECT 7040, '起草中心', '', 2, 0, 7000, 'draft-center', 'ep:edit-pen', 'clm/contract/draftCenter/index', 'ClmContractDraftCenter',
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `id` = 7040);
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 2, 7040, '1', NOW(), '1', NOW(), b'0', 1
WHERE NOT EXISTS (SELECT 1 FROM `system_role_menu` WHERE `role_id`=2 AND `menu_id`=7040 AND `deleted`=b'0');
