-- 一诺复刻第一版：审批节点编辑幂等记录
ALTER TABLE `clm_party`
  ADD COLUMN `short_name` varchar(64) NULL COMMENT '我方主体简称，供合同编号使用' AFTER `name`;

ALTER TABLE `clm_numbering_rule_version`
  ADD COLUMN `include_party_short_name` bit NOT NULL DEFAULT b'1' COMMENT '编号是否包含我方主体简称' AFTER `prefix`;

CREATE TABLE IF NOT EXISTS `clm_approval_edit_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(64) NOT NULL,
  `approval_case_id` bigint NOT NULL,
  `contract_id` bigint NOT NULL,
  `base_revision_id` bigint NOT NULL,
  `result_revision_id` bigint NOT NULL,
  `result_approval_case_id` bigint NULL,
  `request_id` varchar(128) NOT NULL,
  `change_level` varchar(32) NOT NULL,
  `reason` varchar(1000) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0',
  `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clm_approval_edit_request` (`tenant_id`,`request_id`),
  KEY `idx_clm_approval_edit_task` (`tenant_id`,`task_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 审批节点编辑幂等记录';
