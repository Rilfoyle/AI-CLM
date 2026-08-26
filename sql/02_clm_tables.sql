SET NAMES utf8mb4;

-- =====================================================================
-- CLM 合同管理 POC 业务表（全部租户隔离：DO 继承 TenantBaseDO）
-- 约定：主键 bigint 自增；deleted 逻辑删除；creator/updater 为用户编号字符串
-- =====================================================================

-- ---------------------------------------------------------------------
-- 合同类型（身份 + 当前发布版本指针）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_contract_type` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code`               varchar(64)  NOT NULL                COMMENT '类型编码（租户内唯一）',
  `name`               varchar(128) NOT NULL                COMMENT '类型名称',
  `description`        varchar(512)          DEFAULT ''     COMMENT '描述',
  `status`             tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0 开启 1 关闭（CommonStatusEnum）',
  `sort`               int          NOT NULL DEFAULT 0      COMMENT '排序',
  `current_version_id` bigint                DEFAULT NULL   COMMENT '当前发布版本编号（clm_contract_type_version.id），为空表示尚未发布',
  `creator`            varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`          bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同类型';

-- ---------------------------------------------------------------------
-- 合同类型版本（FormCreate 扩展字段 schema，发布后不可修改）
-- form_conf / form_fields 与 bpm_form.conf / fields 同构，可直接复用前端 formCreate 工具函数
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_contract_type_version` (
  `id`                     bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type_id`                bigint       NOT NULL                COMMENT '合同类型编号',
  `version_no`             int          NOT NULL                COMMENT '版本号，从 1 开始递增',
  `status`                 tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0 草稿 1 已发布（ClmTypeVersionStatusEnum）',
  `form_conf`              text                                 COMMENT 'FormCreate 表单配置（option）JSON 字符串',
  `form_fields`            text                                 COMMENT 'FormCreate 字段规则数组（JacksonTypeHandler，List<String>，每项为 rule 的 JSON 串）',
  `process_definition_key` varchar(64)  NOT NULL DEFAULT 'clm_contract_approval_v1' COMMENT '审批使用的 BPM 流程定义 KEY',
  `remark`                 varchar(255)          DEFAULT ''     COMMENT '版本说明',
  `published_time`         datetime              DEFAULT NULL   COMMENT '发布时间',
  `creator`                varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`            datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`              bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_version` (`type_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同类型版本';

-- ---------------------------------------------------------------------
-- 签约方（我方主体与相对方统一建模）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_party` (
  `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `party_type`          tinyint      NOT NULL DEFAULT 1      COMMENT '主体类型：1 企业 2 个人（ClmPartyTypeEnum）',
  `name`                varchar(255) NOT NULL                COMMENT '名称',
  `unified_credit_code` varchar(64)           DEFAULT ''     COMMENT '统一社会信用代码 / 证件号',
  `internal_flag`       bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否我方主体',
  `contact_name`        varchar(64)           DEFAULT ''     COMMENT '联系人',
  `contact_phone`       varchar(32)           DEFAULT ''     COMMENT '联系电话',
  `address`             varchar(512)          DEFAULT ''     COMMENT '地址',
  `status`              tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0 开启 1 关闭',
  `remark`              varchar(255)          DEFAULT ''     COMMENT '备注',
  `creator`             varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`             varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`           bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_name` (`tenant_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 签约方';

-- ---------------------------------------------------------------------
-- 合同（第一业务轴；发起即创建草稿，无"合同申请"对象）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_contract` (
  `id`                          bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_no`                 varchar(64)            DEFAULT NULL   COMMENT '合同编号（创建后生成，租户内唯一）',
  `title`                       varchar(255)  NOT NULL                COMMENT '合同标题',
  `type_id`                     bigint        NOT NULL                COMMENT '合同类型编号',
  `type_version_id`             bigint        NOT NULL                COMMENT '合同类型版本编号（固定引用，不随类型最新配置漂移）',
  `owner_user_id`               bigint        NOT NULL                COMMENT '负责人用户编号',
  `owner_dept_id`               bigint                 DEFAULT NULL   COMMENT '负责人部门编号',
  `amount`                      decimal(18,2)          DEFAULT NULL   COMMENT '合同金额',
  `currency`                    varchar(8)    NOT NULL DEFAULT 'CNY'  COMMENT '币种',
  `sign_date`                   date                   DEFAULT NULL   COMMENT '签订日期',
  `effective_date`              date                   DEFAULT NULL   COMMENT '生效日期',
  `expiry_date`                 date                   DEFAULT NULL   COMMENT '到期日期',
  `custom_data`                 text                                  COMMENT '扩展字段（JacksonTypeHandler，Map<String,Object> JSON）',
  `description`                 text                                  COMMENT '合同说明',
  `lifecycle_status`            tinyint       NOT NULL DEFAULT 1      COMMENT '生命周期状态：1 草稿 2 已批准 3 已生效 4 已到期 5 已终止 6 作废（ClmLifecycleStatusEnum）',
  `approval_status`             tinyint       NOT NULL DEFAULT 0      COMMENT '审批状态：0 未提交 1 审批中 2 审批通过 3 审批驳回 4 已取消（ClmApprovalStatusEnum，与 BPM 流程实例状态同值）',
  `current_document_version_id` bigint                 DEFAULT NULL   COMMENT '当前正文版本编号（clm_document_version.id）',
  `current_binding_id`          bigint                 DEFAULT NULL   COMMENT '最近一次流程绑定编号（clm_workflow_binding.id）',
  `creator`                     varchar(64)            DEFAULT ''     COMMENT '创建者',
  `create_time`                 datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                     varchar(64)            DEFAULT ''     COMMENT '更新者',
  `update_time`                 datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                     bit(1)        NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`                   bigint        NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant_owner_approval` (`tenant_id`, `owner_user_id`, `approval_status`),
  KEY `idx_tenant_dept_lifecycle` (`tenant_id`, `owner_dept_id`, `lifecycle_status`),
  KEY `idx_tenant_contract_no` (`tenant_id`, `contract_no`),
  KEY `idx_type_id` (`type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同';

-- ---------------------------------------------------------------------
-- 合同签约方关联（保存签约时快照）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_contract_party` (
  `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id`    bigint      NOT NULL                COMMENT '合同编号',
  `party_id`       bigint      NOT NULL                COMMENT '签约方编号',
  `role_code`      varchar(32) NOT NULL                COMMENT '角色：OUR_SIDE 我方 / COUNTERPARTY 相对方 / OTHER 其他',
  `sort`           int         NOT NULL DEFAULT 0      COMMENT '排序',
  `party_snapshot` text                                COMMENT '签约方快照 JSON（name、unifiedCreditCode、partyType）',
  `creator`        varchar(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint      NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_contract_id` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同签约方';

-- ---------------------------------------------------------------------
-- 合同文档（一份合同至少一份 MAIN 正文；附件复用同一模型）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_document` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id`        bigint       NOT NULL                COMMENT '合同编号',
  `role_code`          varchar(32)  NOT NULL DEFAULT 'MAIN' COMMENT '文档角色：MAIN 正文 / ATTACHMENT 附件',
  `name`               varchar(255) NOT NULL                COMMENT '文档名称',
  `current_version_id` bigint                DEFAULT NULL   COMMENT '当前版本编号',
  `status`             tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0 正常',
  `creator`            varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`            varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`          bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_contract_id` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同文档';

-- ---------------------------------------------------------------------
-- 文档版本（不可覆盖；审批绑定准确版本与校验和）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_document_version` (
  `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `document_id`       bigint       NOT NULL                COMMENT '文档编号',
  `contract_id`       bigint       NOT NULL                COMMENT '合同编号（冗余，便于权限校验）',
  `version_no`        int          NOT NULL                COMMENT '版本号，从 1 开始递增',
  `parent_version_id` bigint                DEFAULT NULL   COMMENT '父版本编号',
  `file_key`          varchar(255) NOT NULL                COMMENT '存储键（ClmDocumentStorage 返回，默认为 clm_document_blob.id）',
  `file_name`         varchar(255) NOT NULL                COMMENT '原始文件名',
  `mime_type`         varchar(128)          DEFAULT ''     COMMENT 'MIME 类型',
  `file_size`         bigint       NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
  `checksum_sha256`   char(64)     NOT NULL                COMMENT 'SHA-256 校验和（hex）',
  `source_type`       varchar(32)  NOT NULL DEFAULT 'UPLOAD' COMMENT '来源：UPLOAD 上传 / ONLINE_EDIT 在线编辑 / MANUAL_FINAL 线下定稿回传',
  `frozen`            bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否冻结（提交审批即冻结，永不解冻）',
  `remark`            varchar(255)          DEFAULT ''     COMMENT '备注',
  `creator`           varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`           varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`         bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_version` (`document_id`, `version_no`),
  KEY `idx_contract_id` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 文档版本';

-- ---------------------------------------------------------------------
-- 文档内容（CLM 私有存储：无任何 @PermitAll 读取路由，只能经 CLM 授权接口读取）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_document_blob` (
  `id`          bigint    NOT NULL AUTO_INCREMENT COMMENT '编号（即 file_key）',
  `sha256`      char(64)  NOT NULL                COMMENT 'SHA-256',
  `size`        bigint    NOT NULL                COMMENT '大小（字节）',
  `content`     longblob  NOT NULL                COMMENT '内容',
  `creator`     varchar(64)        DEFAULT ''     COMMENT '创建者',
  `create_time` datetime  NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)        DEFAULT ''     COMMENT '更新者',
  `update_time` datetime  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)    NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`   bigint    NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 文档内容（私有）';

-- ---------------------------------------------------------------------
-- 流程绑定（一次流程一个 binding；businessKey = binding.id）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_workflow_binding` (
  `id`                       bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id`              bigint       NOT NULL                COMMENT '合同编号',
  `purpose`                  varchar(32)  NOT NULL DEFAULT 'APPROVAL' COMMENT '用途：APPROVAL 合同审批',
  `process_definition_key`   varchar(64)  NOT NULL                COMMENT '流程定义 KEY',
  `process_definition_id`    varchar(64)           DEFAULT ''     COMMENT '流程定义编号',
  `process_instance_id`      varchar(64)           DEFAULT NULL   COMMENT '流程实例编号',
  `document_version_id`      bigint       NOT NULL                COMMENT '绑定的正文版本编号',
  `contract_type_version_id` bigint       NOT NULL                COMMENT '绑定的合同类型版本编号',
  `form_snapshot`            text                                 COMMENT '提交时的表单快照 JSON（核心字段 + customData + 签约方 + 版本信息）',
  `checksum_sha256`          char(64)     NOT NULL                COMMENT '绑定版本的 SHA-256',
  `status`                   tinyint      NOT NULL DEFAULT 0      COMMENT '状态：0 准备中 1 审批中 2 通过 3 驳回 4 取消（ClmWorkflowBindingStatusEnum）',
  `result_reason`            varchar(512)          DEFAULT ''     COMMENT '审批结果说明',
  `finished_time`            datetime              DEFAULT NULL   COMMENT '结束时间',
  `creator`                  varchar(64)           DEFAULT ''     COMMENT '创建者（提交人）',
  `create_time`              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                  varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`              datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                  bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`                bigint       NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_instance_id` (`process_instance_id`),
  KEY `idx_contract_id` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 流程绑定';

-- ---------------------------------------------------------------------
-- 合同参与人（对象级权限；负责人也以 OWNER 角色落一行，便于统一查询）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_contract_participant` (
  `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id`    bigint      NOT NULL                COMMENT '合同编号',
  `principal_type` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '主体类型：USER（POC 仅支持用户）',
  `principal_id`   bigint      NOT NULL                COMMENT '主体编号',
  `role_code`      varchar(32) NOT NULL                COMMENT '角色：OWNER 负责人 / COLLABORATOR 协作人 / VIEWER 查看人',
  `can_view`       bit(1)      NOT NULL DEFAULT b'1'   COMMENT '可查看',
  `can_edit`       bit(1)      NOT NULL DEFAULT b'0'   COMMENT '可编辑',
  `can_download`   bit(1)      NOT NULL DEFAULT b'0'   COMMENT '可下载',
  `can_manage`     bit(1)      NOT NULL DEFAULT b'0'   COMMENT '可管理参与人',
  `creator`        varchar(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint      NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_contract_principal` (`contract_id`, `principal_type`, `principal_id`),
  KEY `idx_principal` (`tenant_id`, `principal_type`, `principal_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 合同参与人';

-- ---------------------------------------------------------------------
-- 领域审计事件（追加式，不覆盖）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `clm_audit_event` (
  `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '编号',
  `aggregate_type` varchar(32) NOT NULL                COMMENT '聚合类型：CONTRACT / DOCUMENT / CONTRACT_TYPE',
  `aggregate_id`   bigint      NOT NULL                COMMENT '聚合编号',
  `contract_id`    bigint               DEFAULT NULL   COMMENT '关联合同编号（便于按合同查询）',
  `action`         varchar(64) NOT NULL                COMMENT '动作：CONTRACT_CREATE / CONTRACT_UPDATE / CONTRACT_DELETE / CONTRACT_SUBMIT / APPROVAL_RESULT / DOCUMENT_UPLOAD / DOCUMENT_DOWNLOAD / PARTICIPANT_UPDATE / TYPE_PUBLISH / ONLINE_EDIT_OPEN / ONLINE_EDIT_SAVE',
  `actor_user_id`  bigint               DEFAULT NULL   COMMENT '操作人用户编号（系统动作为空）',
  `actor_name`     varchar(64)          DEFAULT ''     COMMENT '操作人昵称',
  `detail_json`    text                                COMMENT '明细 JSON',
  `occurred_at`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `creator`        varchar(64)          DEFAULT ''     COMMENT '创建者',
  `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`        varchar(64)          DEFAULT ''     COMMENT '更新者',
  `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        bit(1)      NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`      bigint      NOT NULL DEFAULT 0      COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_aggregate` (`aggregate_type`, `aggregate_id`, `occurred_at`),
  KEY `idx_contract_time` (`contract_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CLM 审计事件';
