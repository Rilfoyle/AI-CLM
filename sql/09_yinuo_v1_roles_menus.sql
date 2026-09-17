SET NAMES utf8mb4;

-- =====================================================================
-- 一诺复刻第一版：产品角色、W01-W06 菜单与 G/S 能力授权
--
-- 约束：
--   1. 可重复执行；四个产品角色的菜单集由本迁移权威维护。
--   2. W02/W04 是隐藏上下文路由；W06 是合同审批唯一业务入口。
--   3. 只为已有 Vue 实现的 G01-G10/S01-S03 建入口。
--   4. 系统管理员没有任何 clm:contract/revision/document/commitment 权限。
--      它仅可在本人确有 Flowable 任务关系时通过 W06 审批门面查看必要上下文；
--      服务端仍必须校验当前/历史任务关系，菜单权限不能替代对象鉴权。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 现有菜单收敛：保留真实 W/G 页面，隐藏上下文路由和技术底座入口
-- ---------------------------------------------------------------------
UPDATE `system_menu`
SET `name` = '合同管理', `sort` = 4, `status` = 0, `visible` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7000;

UPDATE `system_menu`
SET `name` = '起草中心', `sort` = 0, `parent_id` = 7000,
    `path` = 'draft-center', `icon` = 'ep:edit-pen',
    `component` = 'clm/contract/draftCenter/index', `component_name` = 'ClmContractDraftCenter',
    `status` = 0, `visible` = b'0', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7040;

UPDATE `system_menu`
SET `name` = '合同台账', `sort` = 1, `parent_id` = 7000,
    `path` = 'contract', `icon` = 'ep:tickets',
    `component` = 'clm/contract/index', `component_name` = 'ClmContract',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7030;

UPDATE `system_menu`
SET `name` = '合同类型', `sort` = 1, `parent_id` = 7101,
    `path` = 'contract-type', `icon` = 'ep:collection-tag',
    `component` = 'clm/contractType/index', `component_name` = 'ClmContractType',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7010;

UPDATE `system_menu`
SET `name` = '参与方目录', `sort` = 1, `parent_id` = 7102,
    `path` = 'directory', `icon` = 'ep:office-building',
    `component` = 'clm/party/index', `component_name` = 'ClmParty',
    `status` = 0, `visible` = b'1', `keep_alive` = b'1', `always_show` = b'1',
    `updater` = '1', `update_time` = NOW(), `deleted` = b'0'
WHERE `id` = 7020;

-- W06 取代旧通用 BPM 合同审批外壳；流程设计和运行监控不进入业务导航。
UPDATE `system_menu`
SET `visible` = b'0', `updater` = '1', `update_time` = NOW()
WHERE `id` IN (1185, 1193, 1200, 1201, 1207, 1208, 1209, 2713, 2714, 2720, 2721, 2724, 7050);

-- ---------------------------------------------------------------------
-- 2) W05/W06 真实页面，以及 W/G/S 权限点
-- ---------------------------------------------------------------------
INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  -- 合同治理三级 IA：仅已有真实 Vue 页生成可见入口。
  (7100, '合同治理', '', 1, 10, 7000, 'governance', 'ep:setting', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7101, '规则设置', '', 1, 1, 7100, 'settings', 'ep:tools', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7102, '参与方', '', 1, 2, 7100, 'parties', 'ep:office-building', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7103, '异常处理', '', 1, 3, 7100, 'exceptions', 'ep:warning', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7302, '模板库', '', 2, 2, 7101, 'template', 'ep:files', 'clm/governance/template/index', 'ClmGovernanceTemplate', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7303, '编号规则', '', 2, 3, 7101, 'numbering', 'ep:sort', 'clm/governance/numbering/index', 'ClmGovernanceNumbering', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7304, '审批路由', '', 2, 4, 7101, 'routing', 'ep:share', 'clm/governance/routing/index', 'ClmGovernanceRouting', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7305, '合同权限', '', 2, 5, 7101, 'permission', 'ep:lock', 'clm/governance/permission/index', 'ClmGovernancePermission', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7306, '治理阻断', '', 2, 1, 7103, 'issues', 'ep:warning-filled', 'clm/governance/issues/index', 'ClmGovernanceIssues', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7308, '相对方导入任务', '', 2, 2, 7102, 'import', 'ep:upload-filled', 'clm/governance/partyImport/index', 'ClmGovernancePartyImport', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7309, '流程对账', '', 2, 2, 7103, 'reconciliation', 'ep:refresh', 'clm/governance/reconciliation/index', 'ClmGovernanceReconciliation', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7310, '离职交接', '', 2, 3, 7103, 'handover', 'ep:switch', 'clm/governance/handover/index', 'ClmGovernanceHandover', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- 系统设置：S01-S03 均有真实页面。
  (7200, '系统设置', '', 1, 11, 7000, 'system', 'ep:operation', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7401, '产品角色与数据范围', '', 2, 1, 7200, 'user-scope', 'ep:user', 'clm/system/userScope/index', 'ClmSystemUserScope', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7402, '组织与用户同步', '', 2, 2, 7200, 'sync', 'ep:refresh', 'clm/system/sync/index', 'ClmSystemSync', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7403, '钉钉集成', '', 2, 3, 7200, 'integration', 'ep:connection', 'clm/system/integration/index', 'ClmSystemIntegration', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- W05/W06：当前有真实 Vue 页面，作为侧边栏入口。
  (7060, '协同中心', '', 2, 2, 7000, 'collaboration', 'ep:chat-line-square', 'clm/collaboration/index', 'ClmCollaboration', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7070, '审批中心', '', 2, 3, 7000, 'approval', 'ep:stamp', 'clm/approval/index', 'ClmApproval', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- W01-W04：工作台、起草、草稿恢复、修订和重大承诺。
  (7080, '合同工作台查询', 'clm:workbench:query', 3, 1, 7000, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7081, '已发布模板查询', 'clm:template:query', 3, 1, 7040, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7082, '草稿恢复', 'clm:contract:restore', 3, 8, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7083, '合同修订查询', 'clm:revision:query', 3, 9, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7084, '合同修订保存', 'clm:revision:update', 3, 10, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7085, '重大承诺查询', 'clm:commitment:query', 3, 11, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7086, '重大承诺新增', 'clm:commitment:create', 3, 12, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7087, '重大承诺修改', 'clm:commitment:update', 3, 13, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7088, '重大承诺删除', 'clm:commitment:delete', 3, 14, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7089, '确认无重大承诺', 'clm:commitment:confirm', 3, 15, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7090, '运行 AI 修订审查', 'clm:ai-review:run', 3, 16, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7091, '处理 AI 风险项', 'clm:ai-review:update', 3, 17, 7030, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- W05：协同动作仍由服务端校验经办/当前协同关系和精确修订。
  (7061, '协同查询', 'clm:collaboration:query', 3, 1, 7060, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7062, '发起协同', 'clm:collaboration:start', 3, 2, 7060, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7063, '协同评论', 'clm:collaboration:update', 3, 3, 7060, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7064, '要求修改', 'clm:collaboration:update', 3, 4, 7060, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7065, '完成协同', 'clm:collaboration:update', 3, 5, 7060, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7066, '取消协同', 'clm:collaboration:start', 3, 6, 7060, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- W06：权限仅是第一层，当前 assignee/任务关系是强制第二层。
  (7071, '审批任务查询', 'clm:approval:query', 3, 1, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7072, '审批同意', 'clm:approval:approve', 3, 2, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7073, '审批拒绝', 'clm:approval:reject', 3, 3, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7074, '审批退回', 'clm:approval:return', 3, 4, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7075, '审批历史', 'clm:approval:history', 3, 5, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7076, '审批中编辑', 'clm:approval:edit', 3, 6, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7077, '审批协作', 'clm:approval:collaborate', 3, 7, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7078, '发起人撤回整单', 'clm:approval:withdraw-case', 3, 8, 7070, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- G：权限点挂在对应治理页。
  (7111, '模板治理查询', 'clm:governance:template:query', 3, 1, 7302, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7112, '模板治理维护', 'clm:governance:template:update', 3, 2, 7302, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7113, '模板治理发布', 'clm:governance:template:publish', 3, 3, 7302, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7121, '编号规则查询', 'clm:governance:numbering:query', 3, 1, 7303, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7122, '编号规则维护', 'clm:governance:numbering:update', 3, 2, 7303, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7123, '编号规则发布', 'clm:governance:numbering:publish', 3, 3, 7303, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7131, '审批路由查询', 'clm:governance:routing:query', 3, 1, 7304, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7132, '审批路由维护', 'clm:governance:routing:update', 3, 2, 7304, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7133, '审批路由发布', 'clm:governance:routing:publish', 3, 3, 7304, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7141, '合同权限政策查询', 'clm:permission-policy:query', 3, 1, 7305, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7142, '合同权限政策维护', 'clm:permission-policy:update', 3, 2, 7305, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7143, '合同权限政策发布', 'clm:permission-policy:publish', 3, 3, 7305, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7151, '治理阻断查询', 'clm:governance-issue:query', 3, 1, 7306, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7152, '治理阻断处理', 'clm:governance-issue:update', 3, 2, 7306, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7161, '参与方导入查询', 'clm:party-import:query', 3, 1, 7308, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7162, '参与方导入', 'clm:party-import:create', 3, 2, 7308, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7163, '参与方导入确认', 'clm:party-import:confirm', 3, 3, 7308, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7171, '流程对账查询', 'clm:reconciliation:query', 3, 1, 7309, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7172, '流程对账业务确认', 'clm:reconciliation:confirm', 3, 2, 7309, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7173, '流程对账技术重放', 'clm:reconciliation:replay', 3, 3, 7309, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7181, '离职交接查询', 'clm:handover:query', 3, 1, 7310, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7182, '活动任务重分配', 'clm:handover:reassign-task', 3, 2, 7310, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- S：复用系统用户/角色/部门页面；以下仅为 CLM 范围和钉钉适配权限。
  (7211, '产品角色范围查询', 'clm:user-scope:query', 3, 1, 7401, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7212, '产品角色范围分配', 'clm:user-scope:update', 3, 2, 7401, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7221, '组织与钉钉集成查询', 'clm:integration:query', 3, 1, 7402, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7222, '组织与钉钉沙箱执行', 'clm:integration:run', 3, 2, 7402, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7223, '组织同步失败重试', 'clm:system:org-sync:retry', 3, 3, 7402, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7231, '钉钉集成查询', 'clm:system:dingtalk:query', 3, 3, 7403, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7232, '钉钉集成配置', 'clm:system:dingtalk:update', 3, 4, 7403, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7233, '钉钉消息重试', 'clm:system:dingtalk:retry', 3, 5, 7403, '', '', '', '', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `permission` = VALUES(`permission`), `type` = VALUES(`type`),
  `sort` = VALUES(`sort`), `parent_id` = VALUES(`parent_id`), `path` = VALUES(`path`),
  `icon` = VALUES(`icon`), `component` = VALUES(`component`), `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`), `visible` = VALUES(`visible`), `keep_alive` = VALUES(`keep_alive`),
  `always_show` = VALUES(`always_show`), `updater` = '1', `update_time` = NOW(), `deleted` = b'0';

-- 第一阶段明确裁掉的能力：若旧库或后续 01-08 兼容迁移曾创建入口，一律隐藏并撤销菜单授权。
UPDATE `system_menu`
SET `visible` = b'0', `updater` = '1', `update_time` = NOW()
WHERE `id` BETWEEN 7000 AND 7999
  AND (
    `name` REGEXP '复制|续签|归档|用章|签署|履约'
    OR LOWER(COALESCE(`permission`, '')) REGEXP '(^|:)(copy|renew|renewal|archive|seal|sign|signature|performance)(:|$)'
    OR LOWER(COALESCE(`path`, '')) REGEXP '(^|/|-)(copy|renew|renewal|archive|seal|sign|signature|performance)(/|-|$)'
    OR LOWER(COALESCE(`component`, '')) REGEXP '(^|/|-)(copy|renew|renewal|archive|seal|sign|signature|performance)(/|-|$)'
  );

DELETE rm
FROM `system_role_menu` rm
JOIN `system_menu` m ON m.`id` = rm.`menu_id`
WHERE m.`id` BETWEEN 7000 AND 7999
  AND (
    m.`name` REGEXP '复制|续签|归档|用章|签署|履约'
    OR LOWER(COALESCE(m.`permission`, '')) REGEXP '(^|:)(copy|renew|renewal|archive|seal|sign|signature|performance)(:|$)'
    OR LOWER(COALESCE(m.`path`, '')) REGEXP '(^|/|-)(copy|renew|renewal|archive|seal|sign|signature|performance)(/|-|$)'
    OR LOWER(COALESCE(m.`component`, '')) REGEXP '(^|/|-)(copy|renew|renewal|archive|seal|sign|signature|performance)(/|-|$)'
  );

-- ---------------------------------------------------------------------
-- 3) 四个固定产品角色（业务范围默认拒绝，系统管理范围为全组织）
-- ---------------------------------------------------------------------
INSERT INTO `system_role`
  (`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'CLM 业务经办人', 'clm_business', 10, 2, '', 0, 2,
       '起草、协同发起、提交及本人/参与合同；合同数据范围未显式配置时默认拒绝',
       '1', NOW(), '1', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_business' AND `deleted` = b'0'
);

INSERT INTO `system_role`
  (`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'CLM 法务', 'clm_legal', 20, 2, '', 0, 2,
       '当前协同/审批任务及显式授权合同；合同数据范围未显式配置时默认拒绝',
       '1', NOW(), '1', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_legal' AND `deleted` = b'0'
);

INSERT INTO `system_role`
  (`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'CLM 合同管理员', 'clm_contract_admin', 30, 2, '', 0, 2,
       '合同元数据与治理；无合同正文下载/编辑权限，正文另需显式业务数据授权',
       '1', NOW(), '1', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_contract_admin' AND `deleted` = b'0'
);

INSERT INTO `system_role`
  (`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,
   `creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 'CLM 系统管理员', 'clm_system_admin', 40, 1, '', 0, 2,
       '组织、账号、角色与集成；不授予合同台账、正文、修订、下载或审批历史全局权限',
       '1', NOW(), '1', NOW(), b'0', 1
WHERE NOT EXISTS (
  SELECT 1 FROM `system_role` WHERE `tenant_id` = 1 AND `code` = 'clm_system_admin' AND `deleted` = b'0'
);

UPDATE `system_role`
SET `name` = 'CLM 业务经办人', `sort` = 10, `data_scope` = 2, `data_scope_dept_ids` = '',
    `status` = 0, `type` = 2,
    `remark` = '起草、协同发起、提交及本人/参与合同；合同数据范围未显式配置时默认拒绝',
    `updater` = '1', `update_time` = NOW()
WHERE `tenant_id` = 1 AND `code` = 'clm_business' AND `deleted` = b'0';

UPDATE `system_role`
SET `name` = 'CLM 法务', `sort` = 20, `data_scope` = 2, `data_scope_dept_ids` = '',
    `status` = 0, `type` = 2,
    `remark` = '当前协同/审批任务及显式授权合同；合同数据范围未显式配置时默认拒绝',
    `updater` = '1', `update_time` = NOW()
WHERE `tenant_id` = 1 AND `code` = 'clm_legal' AND `deleted` = b'0';

UPDATE `system_role`
SET `name` = 'CLM 合同管理员', `sort` = 30, `data_scope` = 2, `data_scope_dept_ids` = '',
    `status` = 0, `type` = 2,
    `remark` = '合同元数据与治理；无合同正文下载/编辑权限，正文另需显式业务数据授权',
    `updater` = '1', `update_time` = NOW()
WHERE `tenant_id` = 1 AND `code` = 'clm_contract_admin' AND `deleted` = b'0';

UPDATE `system_role`
SET `name` = 'CLM 系统管理员', `sort` = 40, `data_scope` = 1, `data_scope_dept_ids` = '',
    `status` = 0, `type` = 2,
    `remark` = '组织、账号、角色与集成；不授予合同台账、正文、修订、下载或审批历史全局权限',
    `updater` = '1', `update_time` = NOW()
WHERE `tenant_id` = 1 AND `code` = 'clm_system_admin' AND `deleted` = b'0';

-- 旧 common 不再兜底获得全部 CLM，也不再显示通用 BPM/流程配置入口。
DELETE rm
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
WHERE r.`tenant_id` = 1 AND r.`code` = 'common'
  AND (rm.`menu_id` BETWEEN 7000 AND 7999
       OR rm.`menu_id` IN (1185, 1193, 1200, 1201, 1207, 1208, 1209, 2713, 2714, 2720, 2721, 2724));

-- 四个固定角色的菜单集以本迁移为准，先清后写保证重复执行不累积脏授权。
DELETE rm
FROM `system_role_menu` rm
JOIN `system_role` r ON r.`id` = rm.`role_id`
WHERE r.`tenant_id` = 1
  AND r.`code` IN ('clm_business', 'clm_legal', 'clm_contract_admin', 'clm_system_admin');

-- 业务经办人：W01/W02/W03/W04/W05/W06；不授予治理和系统设置。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  7000, 7040, 7030, 7060, 7070,
  7080, 7081, 7082, 7083, 7084, 7085, 7086, 7087, 7088, 7089, 7090, 7091,
  7031, 7032, 7033, 7034, 7035, 7036, 7037,
  7061, 7062, 7063, 7066,
  7071, 7072, 7073, 7074, 7075, 7077, 7078
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_business' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 法务：授权合同/修订、当前协同和本人审批任务；不能起草、提交或治理发布。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  7000, 7030, 7060, 7070,
  7080, 7083, 7084, 7085, 7086, 7087, 7088, 7089, 7090, 7091, 7031, 7033, 7036,
  7061, 7063, 7064, 7065,
  7071, 7072, 7073, 7074, 7075, 7076, 7077, 7078
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_legal' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 合同管理员：合同元数据 + G 治理；不授予合同正文下载/编辑/提交。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  7000, 7030, 7070,
  7100, 7101, 7102, 7103, 7010, 7020, 7302, 7303, 7304, 7305, 7306, 7308, 7309, 7310,
  7080, 7081, 7031, 7083,
  7071, 7072, 7073, 7074, 7075, 7077, 7078,
  7011, 7012, 7013, 7014, 7015,
  7021, 7022, 7023, 7024,
  7111, 7112, 7113, 7121, 7122, 7123, 7131, 7132, 7133,
  7141, 7142, 7143, 7151, 7152, 7161, 7162, 7163,
  7171, 7172, 7181, 7182
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_contract_admin' AND r.`deleted` = b'0' AND m.`deleted` = b'0';

-- 系统管理员：S01-S03 为 CLM 设置页；W06 仅保留本人任务查询，不授予审批动作。
-- 明确不包含 7030/7031/7033/7036/7083/7084/7085-7089 等合同正文能力。
INSERT INTO `system_role_menu`
  (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT r.`id`, m.`id`, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_role` r
JOIN `system_menu` m ON m.`id` IN (
  1, 100, 101, 103,
  1001, 1002, 1003, 1004, 1005, 1006, 1007,
  1008, 1009, 1010, 1011, 1012, 1063, 1064, 1065,
  1017, 1018, 1019, 1020,
  7000, 7070, 7080, 7071,
  7100, 7103, 7309, 7171, 7173,
  7200, 7401, 7402, 7403, 7211, 7212, 7221, 7222, 7223, 7231, 7232, 7233
)
WHERE r.`tenant_id` = 1 AND r.`code` = 'clm_system_admin' AND r.`deleted` = b'0' AND m.`deleted` = b'0';
