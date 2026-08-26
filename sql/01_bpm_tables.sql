SET NAMES utf8mb4;

-- =====================================================================
-- BPM 流程分类 (BpmCategoryDO extends BaseDO)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_category` (
  `id`          bigint       NOT NULL AUTO_INCREMENT           COMMENT '分类编号',
  `name`        varchar(64)  NOT NULL DEFAULT ''               COMMENT '分类名',
  `code`        varchar(64)  NOT NULL DEFAULT ''               COMMENT '分类标志',
  `description` varchar(255)          DEFAULT ''               COMMENT '分类描述',
  `status`      tinyint      NOT NULL DEFAULT 0                COMMENT '分类状态，枚举 CommonStatusEnum：0 开启、1 关闭',
  `sort`        int          NOT NULL DEFAULT 0                COMMENT '分类排序',
  `creator`     varchar(64)           DEFAULT ''               COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)           DEFAULT ''               COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程分类';

-- =====================================================================
-- BPM 工作流的表单定义 (BpmFormDO extends BaseDO, autoResultMap = true)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_form` (
  `id`          bigint       NOT NULL AUTO_INCREMENT           COMMENT '编号',
  `name`        varchar(64)  NOT NULL DEFAULT ''               COMMENT '表单名',
  `status`      tinyint      NOT NULL DEFAULT 0                COMMENT '状态，枚举 CommonStatusEnum：0 开启、1 关闭',
  `conf`        text                                           COMMENT '表单的配置',
  `fields`      text                                           COMMENT '表单项的数组（JacksonTypeHandler，List<String> 的 JSON 串，form-generator 格式）',
  `remark`      varchar(255)          DEFAULT ''               COMMENT '备注',
  `creator`     varchar(64)           DEFAULT ''               COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)           DEFAULT ''               COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 工作流的表单定义';

-- =====================================================================
-- BPM 流程定义的拓展信息 (BpmProcessDefinitionInfoDO extends BaseDO, autoResultMap = true)
-- 补充 Flowable ProcessDefinition 不支持的拓展字段
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_process_definition_info` (
  `id`                             bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
  `process_definition_id`          varchar(64)  NOT NULL DEFAULT ''     COMMENT '流程定义的编号，关联 ProcessDefinition#getId()',
  `model_id`                       varchar(64)  NOT NULL DEFAULT ''     COMMENT '流程模型的编号，关联 Model#getId()',
  `model_type`                     tinyint      NOT NULL DEFAULT 0      COMMENT '流程模型的类型，枚举 BpmModelTypeEnum',
  `category`                       varchar(64)           DEFAULT ''     COMMENT '流程分类的编码，关联 bpm_category.code',
  `icon`                           varchar(512)          DEFAULT ''     COMMENT '图标',
  `description`                    varchar(1024)         DEFAULT ''     COMMENT '描述',
  `form_type`                      tinyint      NOT NULL DEFAULT 0      COMMENT '表单类型，枚举 BpmModelFormTypeEnum',
  `form_id`                        bigint                DEFAULT NULL   COMMENT '动态表单编号，关联 bpm_form.id（form_type = NORMAL 时）',
  `form_conf`                      text                                 COMMENT '表单的配置，冗余 bpm_form.conf（form_type = NORMAL 时）',
  `form_fields`                    text                                 COMMENT '表单项的数组，冗余 bpm_form.fields（JacksonTypeHandler，List<String> JSON）',
  `form_custom_create_path`        varchar(255)          DEFAULT ''     COMMENT '自定义表单的提交路径，Vue 路由地址（form_type = CUSTOM 时）',
  `form_custom_view_path`          varchar(255)          DEFAULT ''     COMMENT '自定义表单的查看路径，Vue 路由地址（form_type = CUSTOM 时）',
  `simple_model`                   longtext                             COMMENT 'SIMPLE 设计器模型数据 json 格式（仿钉钉设计器发布时的快照）',
  `visible`                        bit(1)       NOT NULL DEFAULT b'1'   COMMENT '是否可见；false 时不展示在“发起流程”列表',
  `sort`                           bigint       NOT NULL DEFAULT 0      COMMENT '排序值',
  `start_user_ids`                 varchar(1024)         DEFAULT NULL   COMMENT '可发起用户编号数组（LongListTypeHandler，英文逗号分隔，供 find_in_set 使用）；为空表示全部可发起',
  `start_dept_ids`                 varchar(1024)         DEFAULT NULL   COMMENT '可发起部门编号数组（LongListTypeHandler，英文逗号分隔）',
  `manager_user_ids`               varchar(1024)         DEFAULT NULL   COMMENT '可管理用户编号数组（LongListTypeHandler，英文逗号分隔，供 find_in_set 使用）',
  `allow_cancel_running_process`   bit(1)       NOT NULL DEFAULT b'1'   COMMENT '是否允许撤销审批中的申请',
  `allow_withdraw_task`            bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否允许审批人撤回任务',
  `process_id_rule`                text                                 COMMENT '流程 ID 规则（JacksonTypeHandler，BpmModelMetaInfoVO.ProcessIdRule）',
  `auto_approval_type`             tinyint               DEFAULT NULL   COMMENT '自动去重类型，枚举 BpmAutoApproveTypeEnum',
  `title_setting`                  text                                 COMMENT '标题设置（JacksonTypeHandler，BpmModelMetaInfoVO.TitleSetting）',
  `summary_setting`                text                                 COMMENT '摘要设置（JacksonTypeHandler，BpmModelMetaInfoVO.SummarySetting）',
  `process_before_trigger_setting` text                                 COMMENT '流程前置通知设置（JacksonTypeHandler，HttpRequestSetting）',
  `process_after_trigger_setting`  text                                 COMMENT '流程后置通知设置（JacksonTypeHandler，HttpRequestSetting）',
  `task_before_trigger_setting`    text                                 COMMENT '任务前置通知设置（JacksonTypeHandler，HttpRequestSetting）',
  `task_after_trigger_setting`     text                                 COMMENT '任务后置通知设置（JacksonTypeHandler，HttpRequestSetting）',
  `print_template_setting`         text                                 COMMENT '自定义打印模板设置（JacksonTypeHandler，PrintTemplateSetting）',
  `creator`                        varchar(64)           DEFAULT ''     COMMENT '创建者',
  `create_time`                    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`                        varchar(64)           DEFAULT ''     COMMENT '更新者',
  `update_time`                    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                        bit(1)       NOT NULL DEFAULT b'0'   COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_definition_id` (`process_definition_id`),
  KEY `idx_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程定义的拓展信息';

-- =====================================================================
-- BPM 流程表达式 (BpmProcessExpressionDO extends BaseDO)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_process_expression` (
  `id`          bigint        NOT NULL AUTO_INCREMENT           COMMENT '编号',
  `name`        varchar(64)   NOT NULL DEFAULT ''               COMMENT '表达式名字',
  `status`      tinyint       NOT NULL DEFAULT 0                COMMENT '表达式状态，枚举 CommonStatusEnum：0 开启、1 关闭',
  `expression`  varchar(1024) NOT NULL DEFAULT ''               COMMENT '表达式',
  `creator`     varchar(64)            DEFAULT ''               COMMENT '创建者',
  `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)            DEFAULT ''               COMMENT '更新者',
  `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)        NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程表达式';

-- =====================================================================
-- BPM 流程监听器 (BpmProcessListenerDO extends BaseDO)
-- 本质是流程监听器的模版，供 BPMN 设计时选择
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_process_listener` (
  `id`          bigint        NOT NULL AUTO_INCREMENT           COMMENT '主键 ID，自增',
  `name`        varchar(64)   NOT NULL DEFAULT ''               COMMENT '监听器名字',
  `status`      tinyint       NOT NULL DEFAULT 0                COMMENT '状态，枚举 CommonStatusEnum：0 开启、1 关闭',
  `type`        varchar(32)   NOT NULL DEFAULT ''               COMMENT '监听类型，枚举 BpmProcessListenerTypeEnum：execution 执行监听器、task 任务监听器',
  `event`       varchar(32)   NOT NULL DEFAULT ''               COMMENT '监听事件；execution 时 start/end，task 时 create/assignment/complete/delete/update/timeout',
  `value_type`  varchar(32)   NOT NULL DEFAULT ''               COMMENT '值类型：class / delegateExpression / expression',
  `value`       varchar(255)  NOT NULL DEFAULT ''               COMMENT '值',
  `creator`     varchar(64)            DEFAULT ''               COMMENT '创建者',
  `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)            DEFAULT ''               COMMENT '更新者',
  `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)        NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程监听器';

-- =====================================================================
-- BPM 用户组 (BpmUserGroupDO extends BaseDO, autoResultMap = true)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_user_group` (
  `id`          bigint       NOT NULL AUTO_INCREMENT           COMMENT '编号，自增',
  `name`        varchar(64)  NOT NULL DEFAULT ''               COMMENT '组名',
  `description` varchar(255)          DEFAULT ''               COMMENT '描述',
  `status`      tinyint      NOT NULL DEFAULT 0                COMMENT '状态，枚举 CommonStatusEnum：0 开启、1 关闭',
  `user_ids`    varchar(1024)         DEFAULT NULL             COMMENT '成员用户编号数组（JacksonTypeHandler，Set<Long> 的 JSON 串）',
  `creator`     varchar(64)           DEFAULT ''               COMMENT '创建者',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`     varchar(64)           DEFAULT ''               COMMENT '更新者',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     bit(1)       NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 用户组';

-- =====================================================================
-- OA 请假申请 (BpmOALeaveDO extends BaseDO)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_oa_leave` (
  `id`                  bigint       NOT NULL AUTO_INCREMENT           COMMENT '请假表单主键',
  `user_id`             bigint       NOT NULL                          COMMENT '申请人的用户编号，关联 system_users.id',
  `type`                tinyint      NOT NULL DEFAULT 0                COMMENT '请假类型，字典 bpm_oa_leave_type',
  `reason`              varchar(255) NOT NULL DEFAULT ''               COMMENT '原因',
  `start_time`          datetime     NOT NULL                          COMMENT '开始时间',
  `end_time`            datetime     NOT NULL                          COMMENT '结束时间',
  `day`                 bigint       NOT NULL DEFAULT 0                COMMENT '请假天数',
  `status`              tinyint      NOT NULL DEFAULT 0                COMMENT '审批结果，枚举 BpmTaskStatusEnum',
  `process_instance_id` varchar(64)           DEFAULT ''               COMMENT '对应的流程编号，关联 ProcessInstance#getId()',
  `creator`             varchar(64)           DEFAULT ''               COMMENT '创建者',
  `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`             varchar(64)           DEFAULT ''               COMMENT '更新者',
  `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             bit(1)       NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_process_instance_id` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OA 请假申请';

-- =====================================================================
-- BPM 流程抄送 (BpmProcessInstanceCopyDO extends BaseDO, autoResultMap = true)
-- =====================================================================
CREATE TABLE IF NOT EXISTS `bpm_process_instance_copy` (
  `id`                    bigint       NOT NULL AUTO_INCREMENT           COMMENT '编号',
  `start_user_id`         bigint                DEFAULT NULL             COMMENT '发起人 Id，冗余 ProcessInstance 的 startUserId',
  `process_instance_name` varchar(255)          DEFAULT ''               COMMENT '流程名，冗余 ProcessInstance 的 name',
  `process_instance_id`   varchar(64)  NOT NULL DEFAULT ''               COMMENT '流程实例的编号，关联 ProcessInstance#getId()',
  `process_definition_id` varchar(64)           DEFAULT ''               COMMENT '流程实例的流程定义编号，关联 ProcessInstance 的 processDefinitionId',
  `category`              varchar(64)           DEFAULT ''               COMMENT '流程分类，冗余 ProcessInstance 的 category',
  `activity_id`           varchar(64)           DEFAULT ''               COMMENT '流程活动的编号，冗余 FlowNode#getId()，对应 BPMN XML 节点编号',
  `activity_name`         varchar(255)          DEFAULT ''               COMMENT '流程活动的名字，冗余 FlowNode#getName()',
  `task_id`               varchar(64)           DEFAULT ''               COMMENT '流程任务的编号，关联 HistoricTaskInstance#getId()',
  `user_id`               bigint       NOT NULL                          COMMENT '被抄送的用户编号，关联 system_users.id',
  `reason`                varchar(255)          DEFAULT ''               COMMENT '抄送意见',
  `creator`               varchar(64)           DEFAULT ''               COMMENT '创建者',
  `create_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`               varchar(64)           DEFAULT ''               COMMENT '更新者',
  `update_time`           datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)       NOT NULL DEFAULT b'0'             COMMENT '是否删除',
  `tenant_id`   bigint       NOT NULL DEFAULT 0                COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_process_instance_id` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程抄送';

-- =====================================================================
-- CLM POC seed: a process category for contract flows
-- =====================================================================
INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `updater`, `tenant_id`)
SELECT '合同管理', 'contract', '合同审批相关流程', 0, 1, '1', '1', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `code` = 'contract');
INSERT INTO `bpm_category` (`name`, `code`, `description`, `status`, `sort`, `creator`, `updater`, `tenant_id`)
SELECT 'OA 办公', 'oa', 'OA 办公相关流程（示例）', 0, 2, '1', '1', 1
WHERE NOT EXISTS (SELECT 1 FROM `bpm_category` WHERE `code` = 'oa');
