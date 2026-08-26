# BPM Business Table DDL — derived from MyBatis-Plus entities

## 1. Entity inventory

Base classes (exact inherited columns):

`yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/core/dataobject/BaseDO.java`
| Java field | Java type | column |
|---|---|---|
| `createTime` | `LocalDateTime` | `create_time` |
| `updateTime` | `LocalDateTime` | `update_time` |
| `creator` | `String` (jdbcType VARCHAR) | `creator` |
| `updater` | `String` (jdbcType VARCHAR) | `updater` |
| `deleted` | `Boolean` (`@TableLogic`) | `deleted` |

`yudao-framework/yudao-spring-boot-starter-biz-tenant/src/main/java/cn/iocoder/yudao/framework/tenant/core/db/TenantBaseDO.java` adds only `tenantId` → `tenant_id` (`Long`).

**All 8 BPM DOs extend `BaseDO`, NOT `TenantBaseDO`.** Confirmed against `TenantDatabaseInterceptor.computeIgnoreTable()` (`.../tenant/core/db/TenantDatabaseInterceptor.java`), which only injects `tenant_id` when `TenantBaseDO.class.isAssignableFrom(entityType)`. `yudao.tenant.ignore-tables` is empty in `yudao-server/src/main/resources/application.yaml:318`. **Therefore no `tenant_id` column is emitted for any BPM table** — adding one would be dead weight and the interceptor would never filter on it.

| # | File (relative to `backend/`) | `@TableName` | `autoResultMap` | extends |
|---|---|---|---|---|
| 1 | `yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/dal/dataobject/definition/BpmCategoryDO.java` | `bpm_category` | no | BaseDO |
| 2 | `.../definition/BpmFormDO.java` | `bpm_form` | **yes** | BaseDO |
| 3 | `.../definition/BpmProcessDefinitionInfoDO.java` | `bpm_process_definition_info` | **yes** | BaseDO |
| 4 | `.../definition/BpmProcessExpressionDO.java` | `bpm_process_expression` | no | BaseDO |
| 5 | `.../definition/BpmProcessListenerDO.java` | `bpm_process_listener` | no | BaseDO |
| 6 | `.../definition/BpmUserGroupDO.java` | `bpm_user_group` | **yes** | BaseDO |
| 7 | `.../oa/BpmOALeaveDO.java` | `bpm_oa_leave` | no | BaseDO |
| 8 | `.../task/BpmProcessInstanceCopyDO.java` | `bpm_process_instance_copy` | **yes** | BaseDO |

All 8 carry `@KeySequence("<table>_seq")` (Oracle/PG only; irrelevant for MySQL) and `@TableId` on `Long id`. `mybatis-plus.global-config.db-config.id-type: NONE` → auto-adapted to `AUTO` for MySQL, so `AUTO_INCREMENT` is correct.

TypeHandler fields:

| DO | field | Java type | handler | storage |
|---|---|---|---|---|
| BpmFormDO | `fields` | `List<String>` | `JacksonTypeHandler` | JSON text |
| BpmUserGroupDO | `userIds` | `Set<Long>` | `JacksonTypeHandler` | JSON text |
| BpmProcessDefinitionInfoDO | `formFields` | `List<String>` | `JacksonTypeHandler` | JSON text |
| BpmProcessDefinitionInfoDO | `startUserIds`, `startDeptIds`, `managerUserIds` | `List<Long>` | `LongListTypeHandler` | **comma-joined varchar** (see below) |
| BpmProcessDefinitionInfoDO | `processIdRule`, `titleSetting`, `summarySetting`, `processBeforeTriggerSetting`, `processAfterTriggerSetting`, `taskBeforeTriggerSetting`, `taskAfterTriggerSetting`, `printTemplateSetting` | `BpmModelMetaInfoVO.*` | `JacksonTypeHandler` | JSON text |

`yudao-framework/yudao-spring-boot-starter-mybatis/src/main/java/cn/iocoder/yudao/framework/mybatis/core/type/LongListTypeHandler.java` — `@MappedJdbcTypes(JdbcType.VARCHAR)`, `ps.setString(i, CollUtil.join(strings, ","))`. The DO comment says *"为了可以使用 find_in_set 进行过滤"*, so these three MUST be `varchar`, **not** JSON.

No javadoc in any DO states an explicit length. The only length hints in the repo are the H2 test schema (see §3).

Nested VO shapes (for JSON column sizing), `yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/controller/admin/definition/vo/model/BpmModelMetaInfoVO.java`:
- `ProcessIdRule{Boolean enable; String prefix; String infix; String postfix; Integer length;}` (L110)
- `TitleSetting{Boolean enable; String title;}` (L134)
- `SummarySetting{Boolean enable; List<String> summary;}` (L148)
- `HttpRequestSetting{String url; List<HttpRequestParam> header; List<HttpRequestParam> body; ...}` (L161) — unbounded → `text`
- `PrintTemplateSetting{Boolean enable; String template;}` (L189) — `template` is HTML → `text`

---

## 2. MySQL 8 DDL

```sql
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
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_process_instance_id` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BPM 流程抄送';
```

Notes on type choices:
- `text` (not `json`) for all `JacksonTypeHandler` columns — the handler writes a plain JSON *string* via `setString`, and MySQL `json` would reject empty-string writes; upstream yudao uses `varchar`/`text` for these.
- `start_user_ids` / `start_dept_ids` / `manager_user_ids` are **varchar, not json** (required for `find_in_set`).
- `Boolean visible` / `allowCancelRunningProcess` / `allowWithdrawTask` → `bit(1)`, matching the `deleted` convention.
- `sort` is `Long` in `BpmProcessDefinitionInfoDO` (→ `bigint`) but `Integer` in `BpmCategoryDO` (→ `int`).
- `day` is `Long` in `BpmOALeaveDO` (→ `bigint`), despite the javadoc mentioning 0.5-day半天.

---

## 3. Seed data in `sql/mysql/ruoyi-vue-pro.sql`

**No `CREATE TABLE` for any `bpm_*` table exists in `sql/mysql/ruoyi-vue-pro.sql`.** `grep -c "INSERT INTO \`bpm_"` → **0** across the whole `backend/sql/` tree and the whole repo. No `bpm_category` / `bpm_form` seed rows exist anywhere.

The only BPM table DDL in the repo is the H2 test schema `yudao-module-bpm/src/test/resources/sql/create_tables.sql` (3 tables only: `bpm_user_group`, `bpm_category`, `bpm_form`) plus `.../sql/clean.sql`. It uses `varchar(63)` for `name`/`code`, `varchar(255)` for `description`/`fields`/`conf`/`remark`, `tinyint` for `status`, `int` for `sort`, `varchar(64)` for `creator`/`updater` — consistent with the DDL above.

### 3a. `system_menu` rows (component starts with `bpm/` or path contains `bpm`) — all `status = 0`

| id | parent_id | name | path | component | permission |
|---|---|---|---|---|---|
| 1185 | 0 | 工作流程 | `/bpm` | NULL | `''` |
| 1186 | 1185 | 流程管理 | `manager` | NULL | `''` |
| 1200 | 1185 | 审批中心 | `task` | NULL | `''` |
| 1193 | 1186 | 流程模型 | `model` | `bpm/model/index` | `''` |
| 1194 | 1193 | 模型查询 | | | `bpm:model:query` |
| 1195 | 1193 | 模型创建 | | | `bpm:model:create` |
| 1197 | 1193 | 模型更新 | | | `bpm:model:update` |
| 1198 | 1193 | 模型删除 | | | `bpm:model:delete` |
| 1199 | 1193 | 模型发布 | | | `bpm:model:deploy` |
| 1215 | 1193 | 流程定义查询 | | | `bpm:process-definition:query` |
| 1216 | 1193 | 流程任务分配规则查询 | | | `bpm:task-assign-rule:query` |
| 1217 | 1193 | 流程任务分配规则创建 | | | `bpm:task-assign-rule:create` |
| 1218 | 1193 | 流程任务分配规则更新 | | | `bpm:task-assign-rule:update` |
| 2913 | 1193 | 流程清理 | | | `bpm:model:clean` |
| 1187 | 1186 | 流程表单 | `form` | `bpm/form/index` | `''` |
| 1188–1192 | 1187 | 表单 查询/创建/更新/删除/导出 | | | `bpm:form:query` / `:create` / `:update` / `:delete` / `:export` |
| 2714 | 1186 | 流程分类 | `category` | `bpm/category/index` | `''` |
| 2715–2718 | 2714 | 分类 查询/创建/更新/删除 | | | `bpm:category:query` / `:create` / `:update` / `:delete` |
| 1209 | 1186 | 用户分组 | `user-group` | `bpm/group/index` | `''` |
| 1210–1213 | 1209 | 用户组 查询/创建/更新/删除 | | | `bpm:user-group:query` / `:create` / `:update` / `:delete` |
| 2726 | 1186 | 流程监听器 | `process-listener` | `bpm/processListener/index` | `''` |
| 2727–2730 | 2726 | 流程监听器 查询/创建/更新/删除 | | | `bpm:process-listener:query` / `:create` / `:update` / `:delete` |
| 2731 | 1186 | 流程表达式 | `process-expression` | `bpm/processExpression/index` | `''` |
| 2732–2735 | 2731 | 流程表达式 查询/创建/更新/删除 | | | `bpm:process-expression:query` / `:create` / `:update` / `:delete` |
| 2721 | 1186 | 流程实例 | `process-instance/manager` | `bpm/processInstance/manager/index` | `''` |
| 2722 | 2721 | 流程实例的查询（管理员） | | | `bpm:process-instance:manager-query` |
| 2723 | 2721 | 流程实例的取消（管理员） | | | `bpm:process-instance:cancel-by-admin` |
| 2724 | 1186 | 流程任务 | `process-tasnk` *(typo in source)* | `bpm/task/manager/index` | `''` |
| 2725 | 2724 | 流程任务的查询（管理员） | | | `bpm:task:manager-query` |
| 2720 | 1200 | 发起流程 | `create` | `bpm/processInstance/create/index` | `''` |
| 1201 | 1200 | 我的流程 | `my` | `bpm/processInstance/index` | `''` |
| 1202 | 1201 | 流程实例的查询 | | | `bpm:process-instance:query` |
| 1219 | 1201 | 流程实例的创建 | | | `bpm:process-instance:create` |
| 1220 | 1201 | 流程实例的取消 | | | `bpm:process-instance:cancel` |
| 1207 | 1200 | 待办任务 | `todo` | `bpm/task/todo/index` | `''` |
| 1221 | 1207 | 流程任务的查询 | | | `bpm:task:query` |
| 1222 | 1207 | 流程任务的更新 | | | `bpm:task:update` |
| 1208 | 1200 | 已办任务 | `done` | `bpm/task/done/index` | `''` |
| 2713 | 1200 | 抄送我的 | `copy` | `bpm/task/copy/index` | `bpm:process-instance-cc:query` |
| 1118 | **5** (OA 办公) | 请假查询 | `leave` | `bpm/oa/leave/index` | `''` |
| 1119 | 1118 | 请假申请查询 | | | `bpm:oa-leave:query` |
| 1120 | 1118 | 请假申请创建 | | | `bpm:oa-leave:create` |

### 3b. `system_dict_type` rows with `type` starting `bpm_` (8 total, all `status = 0`)

| id | type | name | `system_dict_data` count |
|---|---|---|---|
| 117 | `bpm_oa_leave_type` | OA 请假类型 | 3 |
| 139 | `bpm_process_instance_status` | 流程实例的状态 | 4 |
| 140 | `bpm_task_status` | 流程实例的结果 | 8 |
| 141 | `bpm_model_form_type` | 流程的表单类型 | 2 |
| 142 | `bpm_task_candidate_strategy` | 任务分配规则的类型 | 8 |
| 613 | `bpm_process_listener_type` | BPM 监听器类型 | 2 |
| 615 | `bpm_process_listener_value_type` | BPM 监听器值类型 | 3 |
| 629 | `bpm_model_type` | BPM 流程模型类型 | 2 |

Other `bpm_*` literals in the file (`bpm_task_timeout`, `bpm_task_assigned`, `bpm_process_instance_reject`, `bpm_process_instance_approve`) appear once each and are **notify/sms template codes, not dict types**.

---

## 4. Flowable configuration

There is **no `yudao-spring-boot-starter-flowable` module** — `yudao-framework/` contains no flowable starter. Flowable is configured entirely by (a) the official `flowable-spring-boot-starter-process` autoconfiguration reading `flowable.*` properties, and (b) one in-module config class.

`yudao-module-bpm/src/main/resources/` **does not exist** — the module has no resources dir at all, so no module-level `application*.yaml` and no `flowable.*` properties there.

All Flowable properties live in `yudao-server/src/main/resources/application.yaml:55-64`:

```yaml
flowable:
  database-schema-update: true   # 启动时会对数据库中所有表进行更新操作，如果表存在，不做处理，反之，自动创建表
  db-history-used: true
  check-process-definitions: false
  history-level: audit
```

Exact property keys: `flowable.database-schema-update`, `flowable.db-history-used`, `flowable.check-process-definitions`, `flowable.history-level`.

**`flowable.database-schema-update: true` → the `ACT_*` tables are auto-created at startup.** You do NOT need to ship `ACT_*` DDL. Only the 8 `bpm_*` business tables above must be created manually.

Config class: `yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/framework/flowable/config/BpmFlowableConfiguration.java`
- `@Bean("applicationTaskExecutor") AsyncTaskExecutor taskExecutor()` — required or startup fails
- `@Bean EngineConfigurationConfigurer<SpringProcessEngineConfiguration> bpmProcessEngineConfigurationConfigurer(ObjectProvider<FlowableEventListener>, ObjectProvider<FlowableFunctionDelegate>, BpmActivityBehaviorFactory)`
- `@Bean BpmActivityBehaviorFactory bpmActivityBehaviorFactory(BpmTaskCandidateInvoker)`
- `@Bean BpmTaskCandidateInvoker bpmTaskCandidateInvoker(List<BpmTaskCandidateStrategy>, AdminUserApi)`
- `@Bean BpmProcessInstanceEventPublisher processInstanceEventPublisher(ApplicationEventPublisher)`

No schema-related property is set programmatically anywhere.

---

## 5. `yudao-module-bpm/pom.xml` dependencies (artifactIds)

`cn.iocoder.boot`: `yudao-module-system`, `yudao-spring-boot-starter-biz-data-permission`, `yudao-spring-boot-starter-biz-tenant`, `yudao-spring-boot-starter-web`, `yudao-spring-boot-starter-security`, `yudao-spring-boot-starter-mybatis`, `yudao-spring-boot-starter-test`, `yudao-spring-boot-starter-excel`.

`org.flowable`: `flowable-spring-boot-starter-process`, `flowable-spring-boot-starter-actuator`.

Version: `<flowable.version>8.0.0</flowable.version>` at `yudao-dependencies/pom.xml:49`.

`yudao-server/src/main/resources/application.yaml` references to bpm/flowable: only the `flowable:` block at lines 55–64. There is no other `application-*.yaml` in `yudao-server/src/main/resources/` referencing bpm or flowable (the only other hit repo-wide is the build copy `yudao-server/target/classes/application.yaml`). No `yudao.bpm.*` properties exist.