SET NAMES utf8mb4;

-- =====================================================================
-- 一诺复刻 V1：独立范本、不可变修订、重大承诺、提交编号/路由、审批修订责任链
-- 前置：01~07 已执行。本迁移只承担 P0 后端闭环，不创建协同/治理/P1 对象。
-- =====================================================================

ALTER TABLE `clm_contract`
  MODIFY COLUMN `contract_no` varchar(64) NULL COMMENT '永久合同编号（首次提交时分配）',
  ADD COLUMN `current_revision_id` bigint NULL COMMENT '当前不可变修订编号' AFTER `current_binding_id`,
  ADD COLUMN `source_mode` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'TEMPLATE/UPLOAD/MANUAL/COPY' AFTER `current_revision_id`,
  ADD COLUMN `stage_code` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/COLLABORATING/APPROVING/APPROVED' AFTER `source_mode`,
  ADD COLUMN `no_commitment_confirmed` bit NOT NULL DEFAULT b'0' COMMENT '已明确确认无重大承诺' AFTER `stage_code`,
  ADD COLUMN `numbering_rule_version_id` bigint NULL COMMENT '首次编号规则版本' AFTER `no_commitment_confirmed`,
  ADD UNIQUE KEY `uk_clm_contract_tenant_no` (`tenant_id`, `contract_no`),
  ADD KEY `idx_clm_contract_stage` (`tenant_id`, `stage_code`, `deleted`);

ALTER TABLE `clm_workflow_binding`
  ADD COLUMN `submitted_revision_id` bigint NULL COMMENT '提交时冻结修订' AFTER `contract_type_version_id`,
  ADD COLUMN `current_revision_id` bigint NULL COMMENT '审批业务单当前修订' AFTER `submitted_revision_id`,
  ADD COLUMN `approved_revision_id` bigint NULL COMMENT '审批通过的精确修订' AFTER `current_revision_id`,
  ADD COLUMN `route_version_id` bigint NULL COMMENT '命中的路由规则版本' AFTER `approved_revision_id`,
  ADD COLUMN `submit_request_id` varchar(64) NULL COMMENT '提交幂等键' AFTER `route_version_id`,
  ADD COLUMN `supersedes_case_id` bigint NULL COMMENT '替代的旧审批业务单' AFTER `submit_request_id`,
  ADD COLUMN `cancel_reason` varchar(512) NULL COMMENT '撤回原因' AFTER `supersedes_case_id`,
  ADD UNIQUE KEY `uk_clm_binding_submit_request` (`tenant_id`, `contract_id`, `submit_request_id`),
  ADD KEY `idx_clm_binding_process_instance` (`process_instance_id`);

CREATE TABLE `clm_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `contract_type_id` bigint NOT NULL,
  `current_version_id` bigint NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0停用 1启用',
  `description` varchar(512) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_template_code` (`tenant_id`,`code`,`deleted`),
  KEY `idx_clm_template_type` (`tenant_id`,`contract_type_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 独立合同范本';

CREATE TABLE `clm_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL, `version_no` int NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `file_key` varchar(255) NOT NULL, `file_name` varchar(255) NOT NULL,
  `mime_type` varchar(128) NOT NULL DEFAULT 'application/octet-stream', `file_size` bigint NOT NULL DEFAULT 0,
  `checksum_sha256` char(64) NULL, `published_time` datetime NULL, `remark` varchar(255) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_template_version` (`tenant_id`,`template_id`,`version_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 独立合同范本版本';

CREATE TABLE `clm_contract_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT, `contract_id` bigint NOT NULL, `revision_no` int NOT NULL,
  `base_revision_id` bigint NULL, `contract_type_version_id` bigint NOT NULL, `template_version_id` bigint NULL,
  `main_document_version_id` bigint NULL, `document_version_ids_json` json NOT NULL,
  `field_snapshot_json` json NOT NULL, `party_snapshot_json` json NOT NULL,
  `commitment_snapshot_json` json NOT NULL, `no_commitment` bit NOT NULL DEFAULT b'0',
  `change_source` varchar(32) NOT NULL, `change_reason` varchar(512) NOT NULL DEFAULT '',
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_revision_no` (`tenant_id`,`contract_id`,`revision_no`,`deleted`),
  KEY `idx_clm_revision_base` (`base_revision_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 不可变合同修订快照';

CREATE TABLE `clm_commitment` (
  `id` bigint NOT NULL AUTO_INCREMENT, `contract_id` bigint NOT NULL,
  `category` varchar(64) NOT NULL, `content` varchar(2000) NOT NULL,
  `owner_user_id` bigint NULL, `due_date` date NULL, `risk_level` varchar(32) NULL,
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), KEY `idx_clm_commitment_contract` (`tenant_id`,`contract_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 当前草稿重大承诺投影';

CREATE TABLE `clm_numbering_rule_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `rule_code` varchar(64) NOT NULL, `contract_type_id` bigint NULL,
  `version_no` int NOT NULL, `status` varchar(32) NOT NULL, `prefix` varchar(32) NOT NULL DEFAULT 'HT-',
  `date_pattern` varchar(32) NOT NULL DEFAULT 'yyyy', `separator` varchar(8) NOT NULL DEFAULT '-',
  `sequence_length` int NOT NULL DEFAULT 4, `reset_period` varchar(16) NOT NULL DEFAULT 'YEAR', `effective_time` datetime NULL,
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_number_rule_version` (`tenant_id`,`rule_code`,`version_no`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 编号规则版本';

CREATE TABLE `clm_number_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT, `rule_version_id` bigint NOT NULL, `period_key` varchar(16) NOT NULL,
  `current_value` bigint NOT NULL DEFAULT 0,
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_number_sequence` (`tenant_id`,`rule_version_id`,`period_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 合同编号序列';

CREATE TABLE `clm_routing_rule_version` (
  `id` bigint NOT NULL AUTO_INCREMENT, `rule_code` varchar(64) NOT NULL, `name` varchar(128) NOT NULL,
  `contract_type_id` bigint NULL, `version_no` int NOT NULL, `priority` int NOT NULL DEFAULT 100,
  `condition_json` json NOT NULL, `process_definition_key` varchar(64) NOT NULL,
  `status` varchar(32) NOT NULL, `effective_time` datetime NULL,
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_route_rule_version` (`tenant_id`,`rule_code`,`version_no`,`deleted`),
  KEY `idx_clm_route_type` (`tenant_id`,`contract_type_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 审批路由规则版本';

CREATE TABLE `clm_approval_task_revision_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT, `task_id` varchar(64) NOT NULL,
  `approval_case_id` bigint NOT NULL, `contract_id` bigint NOT NULL, `decision_revision_id` bigint NOT NULL,
  `action` varchar(16) NOT NULL, `reason` varchar(512) NOT NULL DEFAULT '', `request_id` varchar(64) NOT NULL,
  `creator` varchar(64) NOT NULL DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) NOT NULL DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit NOT NULL DEFAULT b'0', `tenant_id` bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_clm_approval_task_decision` (`tenant_id`,`task_id`,`deleted`),
  UNIQUE KEY `uk_clm_approval_request` (`tenant_id`,`request_id`,`deleted`),
  KEY `idx_clm_approval_contract` (`tenant_id`,`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CLM 审批任务决定修订绑定';

-- 兼容投影：旧合同业务阶段与来源。
UPDATE `clm_contract`
SET `stage_code` = CASE
  WHEN `approval_status` = 1 THEN 'APPROVING'
  WHEN `approval_status` = 2 THEN 'APPROVED'
  ELSE 'DRAFT' END,
  `source_mode` = CASE WHEN `source_contract_id` IS NOT NULL THEN 'COPY' ELSE 'MANUAL' END;

-- 旧合同类型范本一次性转成独立已发布范本，旧字段继续可读。
INSERT INTO `clm_template` (`code`,`name`,`contract_type_id`,`status`,`description`,`creator`,`tenant_id`)
SELECT CONCAT('LEGACY-TYPE-', t.`id`), CONCAT(t.`name`, '范本'), t.`id`, 1, '由合同类型范本迁移', '1', t.`tenant_id`
FROM `clm_contract_type` t
WHERE t.`deleted` = b'0' AND t.`template_file_key` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `clm_template` x WHERE x.`tenant_id`=t.`tenant_id` AND x.`code`=CONCAT('LEGACY-TYPE-',t.`id`) AND x.`deleted`=b'0');

INSERT INTO `clm_template_version` (`template_id`,`version_no`,`status`,`file_key`,`file_name`,`published_time`,`remark`,`creator`,`tenant_id`)
SELECT x.`id`, 1, 'PUBLISHED', t.`template_file_key`, COALESCE(t.`template_file_name`,'contract.docx'), NOW(), '旧类型范本迁移', '1', t.`tenant_id`
FROM `clm_template` x JOIN `clm_contract_type` t ON x.`contract_type_id`=t.`id` AND x.`tenant_id`=t.`tenant_id`
WHERE x.`code`=CONCAT('LEGACY-TYPE-',t.`id`) AND x.`deleted`=b'0'
  AND NOT EXISTS (SELECT 1 FROM `clm_template_version` v WHERE v.`template_id`=x.`id` AND v.`version_no`=1 AND v.`deleted`=b'0');

UPDATE `clm_template` x JOIN `clm_template_version` v ON v.`template_id`=x.`id` AND v.`version_no`=1 AND v.`deleted`=b'0'
SET x.`current_version_id`=v.`id` WHERE x.`current_version_id` IS NULL;

-- 旧合同形成 revision v1，之后所有写入只追加。
INSERT INTO `clm_contract_revision`
 (`contract_id`,`revision_no`,`base_revision_id`,`contract_type_version_id`,`template_version_id`,`main_document_version_id`,
  `document_version_ids_json`,`field_snapshot_json`,`party_snapshot_json`,`commitment_snapshot_json`,`no_commitment`,
  `change_source`,`change_reason`,`creator`,`tenant_id`)
SELECT c.`id`,1,NULL,c.`type_version_id`,NULL,c.`current_document_version_id`,
       COALESCE((SELECT JSON_ARRAYAGG(d.`current_version_id`) FROM `clm_document` d
                    WHERE d.`contract_id`=c.`id` AND d.`tenant_id`=c.`tenant_id`
                      AND d.`current_version_id` IS NOT NULL AND d.`deleted`=b'0'), JSON_ARRAY()),
       JSON_OBJECT('name',c.`title`,'amount',c.`amount`,'currency',c.`currency`,'startDate',c.`effective_date`,
                   'endDate',c.`expiry_date`,'description',c.`description`,'customData',c.`custom_data`,
                   'ownerUserId',c.`owner_user_id`,'ownerDeptId',c.`owner_dept_id`,
                   'contractTypeId',c.`type_id`,'sourceMode',c.`source_mode`),
       COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('partyId',p.`party_id`,'roleCode',p.`role_code`,
                    'sort',p.`sort`,'party',p.`party_snapshot`)) FROM `clm_contract_party` p
                    WHERE p.`contract_id`=c.`id` AND p.`tenant_id`=c.`tenant_id` AND p.`deleted`=b'0'), JSON_ARRAY()),
       JSON_ARRAY(),b'0','MIGRATION','08_yinuo_v1_schema','1',c.`tenant_id`
FROM `clm_contract` c
WHERE c.`deleted`=b'0' AND NOT EXISTS (SELECT 1 FROM `clm_contract_revision` r
      WHERE r.`contract_id`=c.`id` AND r.`tenant_id`=c.`tenant_id` AND r.`deleted`=b'0');

UPDATE `clm_contract` c JOIN `clm_contract_revision` r
  ON r.`contract_id`=c.`id` AND r.`tenant_id`=c.`tenant_id` AND r.`revision_no`=1 AND r.`deleted`=b'0'
SET c.`current_revision_id`=r.`id` WHERE c.`current_revision_id` IS NULL;

UPDATE `clm_workflow_binding` b JOIN `clm_contract` c ON c.`id`=b.`contract_id` AND c.`tenant_id`=b.`tenant_id`
SET b.`submitted_revision_id`=c.`current_revision_id`, b.`current_revision_id`=c.`current_revision_id`,
    b.`approved_revision_id`=IF(b.`status`=2,c.`current_revision_id`,NULL)
WHERE b.`submitted_revision_id` IS NULL;

-- 每个已有租户一个默认永久编号规则；新租户由服务首次提交时按同样规则创建。
INSERT INTO `clm_numbering_rule_version`
 (`rule_code`,`contract_type_id`,`version_no`,`status`,`prefix`,`date_pattern`,`separator`,`sequence_length`,`reset_period`,`effective_time`,`creator`,`tenant_id`)
SELECT 'DEFAULT',NULL,1,'PUBLISHED','HT-','yyyy','-',4,'YEAR',NOW(),'1',t.`tenant_id`
FROM (SELECT DISTINCT `tenant_id` FROM `clm_contract_type` WHERE `deleted`=b'0') t
WHERE NOT EXISTS (SELECT 1 FROM `clm_numbering_rule_version` r WHERE r.`tenant_id`=t.`tenant_id` AND r.`rule_code`='DEFAULT' AND r.`deleted`=b'0');

-- 当前发布合同类型版本转成一条无条件默认路由；后续由管理员版本化维护。
INSERT INTO `clm_routing_rule_version`
 (`rule_code`,`name`,`contract_type_id`,`version_no`,`priority`,`condition_json`,`process_definition_key`,`status`,`effective_time`,`creator`,`tenant_id`)
SELECT CONCAT('TYPE-',t.`id`),CONCAT(t.`name`,'默认审批路由'),t.`id`,1,100,JSON_OBJECT(),v.`process_definition_key`,'PUBLISHED',NOW(),'1',t.`tenant_id`
FROM `clm_contract_type` t JOIN `clm_contract_type_version` v ON v.`id`=t.`current_version_id` AND v.`tenant_id`=t.`tenant_id`
WHERE t.`deleted`=b'0' AND v.`deleted`=b'0'
  AND NOT EXISTS (SELECT 1 FROM `clm_routing_rule_version` r WHERE r.`tenant_id`=t.`tenant_id`
      AND r.`rule_code`=CONCAT('TYPE-',t.`id`) AND r.`deleted`=b'0');
