SET NAMES utf8mb4;

-- =====================================================================
-- 一诺复刻第一版：法务协同、治理恢复与 P1 可替换适配层
-- 依赖：01-09 已执行。本脚本只创建新增权威记录，不复制 Flowable 任务/意见。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `clm_collaboration_case` (
  `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '协同业务单编号',
  `contract_id`           bigint       NOT NULL                COMMENT '合同编号',
  `requested_revision_id` bigint       NOT NULL                COMMENT '发起时合同修订编号',
  `completed_revision_id` bigint                DEFAULT NULL   COMMENT '完成结论绑定的合同修订编号',
  `initiator_user_id`     bigint       NOT NULL                COMMENT '发起人',
  `legal_user_id`         bigint       NOT NULL                COMMENT '当前法务处理人',
  `status`                varchar(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/CHANGE_REQUESTED/COMPLETED/CANCELED',
  `reason`                varchar(1000)         DEFAULT ''     COMMENT '协同说明',
  `conclusion`            varchar(2000)         DEFAULT ''     COMMENT '完成结论',
  `finished_time`         datetime              DEFAULT NULL   COMMENT '完成或取消时间',
  `creator`               varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`               varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`             bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_collaboration_contract` (`tenant_id`, `contract_id`, `status`),
  KEY `idx_collaboration_legal` (`tenant_id`, `legal_user_id`, `status`),
  KEY `idx_collaboration_initiator` (`tenant_id`, `initiator_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 法务协同业务单';

CREATE TABLE IF NOT EXISTS `clm_collaboration_event` (
  `id`            bigint        NOT NULL AUTO_INCREMENT COMMENT '事件编号',
  `case_id`       bigint        NOT NULL                COMMENT '协同业务单编号',
  `contract_id`   bigint        NOT NULL                COMMENT '合同编号',
  `revision_id`   bigint                 DEFAULT NULL   COMMENT '事件实际查看/操作的合同修订编号',
  `event_type`    varchar(32)   NOT NULL                COMMENT 'START/COMMENT/REPLY/REQUEST_CHANGE/COMPLETE/CANCEL',
  `actor_user_id` bigint        NOT NULL                COMMENT '操作人',
  `content`       varchar(4000)          DEFAULT ''     COMMENT '说明或意见',
  `creator`       varchar(64)            DEFAULT ''     COMMENT '创建者',
  `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)            DEFAULT ''     COMMENT '更新者',
  `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)        NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`     bigint        NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_collaboration_event_case` (`tenant_id`, `case_id`, `id`),
  KEY `idx_collaboration_event_contract` (`tenant_id`, `contract_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 法务协同追加事件';

CREATE TABLE IF NOT EXISTS `clm_governance_issue` (
  `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '治理异常编号',
  `issue_type`         varchar(64)   NOT NULL                COMMENT 'TEMPLATE_INVALID/NUMBER_CONFLICT/ROUTE_ZERO/ROUTE_MULTIPLE/NO_ASSIGNEE/PROCESS_MISMATCH/HANDOVER_REQUIRED',
  `status`             varchar(32)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/IGNORED',
  `contract_id`        bigint                 DEFAULT NULL   COMMENT '关联合同编号',
  `approval_binding_id` bigint                DEFAULT NULL   COMMENT '关联审批业务单编号',
  `source_ref`         varchar(255)           DEFAULT ''     COMMENT '规则或运行标识',
  `summary`            varchar(1000) NOT NULL                COMMENT '可面向业务展示的阻断摘要',
  `detail_json`        text                                  COMMENT '不含合同正文和密钥的诊断详情',
  `owner_user_id`      bigint                 DEFAULT NULL   COMMENT '当前处理人',
  `resolved_by`        bigint                 DEFAULT NULL   COMMENT '解决人',
  `resolved_time`      datetime               DEFAULT NULL   COMMENT '解决时间',
  `creator`            varchar(64)            DEFAULT ''     COMMENT '创建者',
  `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)            DEFAULT ''     COMMENT '更新者',
  `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)        NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`          bigint        NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_governance_open_source` (`tenant_id`, `issue_type`, `source_ref`, `status`),
  KEY `idx_governance_status` (`tenant_id`, `status`, `issue_type`, `id`),
  KEY `idx_governance_contract` (`tenant_id`, `contract_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 治理阻断与恢复记录';

CREATE TABLE IF NOT EXISTS `clm_reconciliation_run` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '对账运行编号',
  `run_key`         varchar(128) NOT NULL                COMMENT '幂等运行键',
  `trigger_type`    varchar(32)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULED/REPLAY',
  `status`          varchar(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCEEDED/PARTIAL/FAILED',
  `scanned_count`   int          NOT NULL DEFAULT 0      COMMENT '扫描数量',
  `issue_count`     int          NOT NULL DEFAULT 0      COMMENT '异常数量',
  `repaired_count`  int          NOT NULL DEFAULT 0      COMMENT '幂等修复数量',
  `report_json`     text                                 COMMENT '必要标识、步骤和结果，不包含正文或审批意见',
  `finished_time`   datetime              DEFAULT NULL   COMMENT '完成时间',
  `creator`         varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reconciliation_run_key` (`tenant_id`, `run_key`),
  KEY `idx_reconciliation_status` (`tenant_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 流程一致性对账运行';

CREATE TABLE IF NOT EXISTS `clm_permission_policy_version` (
  `id`                    bigint       NOT NULL AUTO_INCREMENT COMMENT '政策版本编号',
  `version_no`            int          NOT NULL                COMMENT '版本号',
  `status`                varchar(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/INACTIVE',
  `role_capabilities_json` text                                 COMMENT '固定产品角色能力上限',
  `scope_limits_json`      text                                 COMMENT '可分配组织和合同类型范围上限',
  `node_edit_policy_json`  text                                 COMMENT '有限审批节点编辑预设',
  `remark`                varchar(500)          DEFAULT ''     COMMENT '版本说明',
  `published_by`           bigint               DEFAULT NULL   COMMENT '发布人',
  `published_time`         datetime             DEFAULT NULL   COMMENT '发布时间',
  `creator`                varchar(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`            datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                varchar(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`            datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`              bigint      NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_policy_version` (`tenant_id`, `version_no`),
  KEY `idx_permission_policy_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同权限政策不可变版本';

CREATE TABLE IF NOT EXISTS `clm_user_scope` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '用户合同域授权编号',
  `user_id`           bigint       NOT NULL                COMMENT '用户编号',
  `role_code`         varchar(64)  NOT NULL                COMMENT 'clm_business/clm_legal/clm_contract_admin/clm_system_admin',
  `org_scope_json`    text                                 COMMENT '明确授权的组织编号集合',
  `type_scope_json`   text                                 COMMENT '明确授权的合同类型编号集合',
  `policy_version_id` bigint       NOT NULL                COMMENT '授权时引用的政策版本',
  `effective_from`    datetime              DEFAULT NULL   COMMENT '生效时间',
  `effective_to`      datetime              DEFAULT NULL   COMMENT '失效时间',
  `status`            varchar(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `creator`           varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`           varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`         bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_scope_role` (`tenant_id`, `user_id`, `role_code`),
  KEY `idx_user_scope_active` (`tenant_id`, `user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 用户产品角色与具体数据范围';

CREATE TABLE IF NOT EXISTS `clm_user_scope_item` (
  `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '授权范围项编号',
  `user_scope_id` bigint      NOT NULL                COMMENT '用户合同域授权编号',
  `scope_type`    varchar(32) NOT NULL                COMMENT 'ORG/CONTRACT_TYPE',
  `scope_id`      bigint      NOT NULL                COMMENT '组织或合同类型编号',
  `creator`       varchar(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`       varchar(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`     bigint      NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_scope_item` (`tenant_id`, `user_scope_id`, `scope_type`, `scope_id`),
  KEY `idx_user_scope_match` (`tenant_id`, `scope_type`, `scope_id`, `user_scope_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 用户授权组织与合同类型范围项';

CREATE TABLE IF NOT EXISTS `clm_saved_filter` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '保存筛选编号',
  `user_id`     bigint       NOT NULL                COMMENT '用户编号',
  `scene_code`  varchar(64)  NOT NULL                COMMENT '页面场景，例如 CONTRACT_LEDGER',
  `name`        varchar(128) NOT NULL                COMMENT '筛选名称',
  `filter_json` text         NOT NULL                COMMENT '受白名单约束的筛选条件',
  `default_flag` bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否默认',
  `creator`     varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saved_filter_name` (`tenant_id`, `user_id`, `scene_code`, `name`),
  KEY `idx_saved_filter_scene` (`tenant_id`, `user_id`, `scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 用户保存筛选';

CREATE TABLE IF NOT EXISTS `clm_ai_review_run` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT 'AI 运行编号',
  `contract_id`     bigint       NOT NULL                COMMENT '合同编号',
  `revision_id`     bigint       NOT NULL                COMMENT '精确合同修订编号',
  `run_type`        varchar(32)  NOT NULL                COMMENT 'SUMMARY/EXTRACTION/RISK_REVIEW',
  `provider_code`   varchar(64)  NOT NULL DEFAULT 'LOCAL_DETERMINISTIC' COMMENT '适配器编码',
  `status`          varchar(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCEEDED/FAILED',
  `input_fingerprint` char(64)   NOT NULL                COMMENT '输入指纹，不保存正文',
  `result_json`     text                                 COMMENT '结构化运行结果',
  `error_message`   varchar(1000)         DEFAULT ''     COMMENT '可诊断错误，不含密钥和正文',
  `finished_time`   datetime              DEFAULT NULL   COMMENT '完成时间',
  `creator`         varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ai_run_revision` (`tenant_id`, `contract_id`, `revision_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM AI 修订级运行记录';

CREATE TABLE IF NOT EXISTS `clm_ai_review_finding` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '发现编号',
  `run_id`         bigint       NOT NULL                COMMENT 'AI 运行编号',
  `contract_id`    bigint       NOT NULL                COMMENT '合同编号',
  `revision_id`    bigint       NOT NULL                COMMENT '合同修订编号',
  `finding_type`   varchar(64)  NOT NULL                COMMENT '发现类型',
  `severity`       varchar(16)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO/LOW/MEDIUM/HIGH',
  `title`          varchar(255) NOT NULL                COMMENT '标题',
  `detail`         varchar(4000)         DEFAULT ''     COMMENT '结果说明',
  `locator_json`   text                                 COMMENT '正文定位信息',
  `resolution`     varchar(32)           DEFAULT NULL   COMMENT 'ACCEPTED/IGNORED',
  `resolved_by`    bigint                DEFAULT NULL   COMMENT '处理人',
  `resolved_time`  datetime              DEFAULT NULL   COMMENT '处理时间',
  `creator`        varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_ai_finding_run` (`tenant_id`, `run_id`, `id`),
  KEY `idx_ai_finding_revision` (`tenant_id`, `contract_id`, `revision_id`, `resolution`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM AI 修订级审查发现';

CREATE TABLE IF NOT EXISTS `clm_party_import_job` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '导入作业编号',
  `job_key`        varchar(128) NOT NULL                COMMENT '幂等作业键',
  `file_name`      varchar(255) NOT NULL                COMMENT '上传文件名',
  `status`         varchar(32)  NOT NULL DEFAULT 'VALIDATING' COMMENT 'VALIDATING/PREVIEW_READY/IMPORTING/PARTIAL/SUCCEEDED/FAILED',
  `mapping_json`   text                                 COMMENT '确认的字段映射',
  `total_count`    int          NOT NULL DEFAULT 0      COMMENT '总行数',
  `success_count`  int          NOT NULL DEFAULT 0      COMMENT '成功数',
  `failed_count`   int          NOT NULL DEFAULT 0      COMMENT '失败数',
  `finished_time`  datetime              DEFAULT NULL   COMMENT '完成时间',
  `creator`        varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_party_import_job_key` (`tenant_id`, `job_key`),
  KEY `idx_party_import_status` (`tenant_id`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 相对方批量导入作业';

CREATE TABLE IF NOT EXISTS `clm_party_import_item` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '导入行编号',
  `job_id`         bigint       NOT NULL                COMMENT '导入作业编号',
  `row_no`         int          NOT NULL                COMMENT 'Excel 行号',
  `source_json`    text         NOT NULL                COMMENT '该行经安全解析后的字段',
  `duplicate_party_id` bigint            DEFAULT NULL   COMMENT '匹配到的重复主体编号',
  `status`         varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/VALID/IMPORTED/FAILED/SKIPPED',
  `error_message`  varchar(1000)         DEFAULT ''     COMMENT '逐行错误',
  `party_id`       bigint                DEFAULT NULL   COMMENT '导入成功后的主体编号',
  `creator`        varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_party_import_row` (`tenant_id`, `job_id`, `row_no`),
  KEY `idx_party_import_item_status` (`tenant_id`, `job_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 相对方导入逐行结果';

CREATE TABLE IF NOT EXISTS `clm_integration_run` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '集成运行编号',
  `integration_type` varchar(64) NOT NULL               COMMENT 'DINGTALK_ORG_SYNC/DINGTALK_SSO_CHECK',
  `run_key`        varchar(128) NOT NULL                COMMENT '幂等运行键',
  `mode`           varchar(32)  NOT NULL DEFAULT 'SANDBOX' COMMENT 'SANDBOX/REAL',
  `status`         varchar(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/PREVIEW_READY/SUCCEEDED/PARTIAL/FAILED/NOT_CONFIGURED',
  `summary_json`   text                                 COMMENT '差异、冲突、重试与结果摘要',
  `error_message`  varchar(1000)         DEFAULT ''     COMMENT '错误说明，不含密钥',
  `finished_time`  datetime              DEFAULT NULL   COMMENT '完成时间',
  `creator`        varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_run_key` (`tenant_id`, `integration_type`, `run_key`),
  KEY `idx_integration_run_status` (`tenant_id`, `integration_type`, `status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 外部集成运行与沙箱报告';

CREATE TABLE IF NOT EXISTS `clm_integration_delivery` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '投递编号',
  `delivery_key`    varchar(160) NOT NULL                COMMENT '消息幂等键',
  `channel`         varchar(32)  NOT NULL DEFAULT 'DINGTALK' COMMENT '投递渠道',
  `message_type`    varchar(32)  NOT NULL                COMMENT 'TODO/RESULT/CANCEL',
  `recipient_user_id` bigint     NOT NULL                COMMENT '接收用户',
  `contract_id`     bigint                DEFAULT NULL   COMMENT '关联合同',
  `task_id`         varchar(64)           DEFAULT NULL   COMMENT '关联 Flowable 任务',
  `deep_link_path`  varchar(500)          DEFAULT ''     COMMENT '站内鉴权路径，不含 token',
  `status`          varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/CANCELED/FAILED/NOT_CONFIGURED',
  `attempt_count`   int          NOT NULL DEFAULT 0      COMMENT '尝试次数',
  `next_retry_time` datetime              DEFAULT NULL   COMMENT '下次重试时间',
  `last_error`      varchar(1000)         DEFAULT ''     COMMENT '最后错误，不含密钥',
  `creator`         varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_integration_delivery_key` (`tenant_id`, `delivery_key`),
  KEY `idx_integration_delivery_retry` (`tenant_id`, `status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 钉钉待办和结果消息投递记录';

CREATE TABLE IF NOT EXISTS `clm_handover_case` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '离职交接业务单编号',
  `source_user_id`  bigint       NOT NULL                COMMENT '离职用户',
  `target_user_id`  bigint                DEFAULT NULL   COMMENT '接手用户',
  `status`          varchar(32)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PROCESSING/COMPLETED/CANCELED',
  `reason`          varchar(1000)         DEFAULT ''     COMMENT '交接原因',
  `handled_by`      bigint                DEFAULT NULL   COMMENT '合同管理员',
  `finished_time`   datetime              DEFAULT NULL   COMMENT '完成时间',
  `creator`         varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`       bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_handover_case_status` (`tenant_id`, `status`, `id`),
  KEY `idx_handover_source` (`tenant_id`, `source_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 离职业务交接异常';

CREATE TABLE IF NOT EXISTS `clm_handover_item` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '交接项编号',
  `case_id`            bigint       NOT NULL                COMMENT '交接业务单编号',
  `item_type`          varchar(32)  NOT NULL                COMMENT 'CONTRACT/ACTIVE_TASK',
  `contract_id`        bigint                DEFAULT NULL   COMMENT '合同编号',
  `task_id`            varchar(64)           DEFAULT NULL   COMMENT '活动任务编号',
  `original_assignee`  bigint       NOT NULL                COMMENT '原负责人或审批人',
  `target_user_id`     bigint                DEFAULT NULL   COMMENT '接手人',
  `status`             varchar(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/TRANSFERRED/SKIPPED/FAILED',
  `result_message`     varchar(1000)         DEFAULT ''     COMMENT '处理结果',
  `creator`            varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`          bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_handover_item` (`tenant_id`, `case_id`, `item_type`, `contract_id`, `task_id`),
  KEY `idx_handover_item_status` (`tenant_id`, `case_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 离职交接合同与活动任务项';
