SET NAMES utf8mb4;

-- =====================================================================
-- CLM 菜单（id 7000-7999 预留段）、角色授权、字典
-- 可重复执行：先删除本段再插入
-- =====================================================================
DELETE FROM `system_role_menu` WHERE `menu_id` BETWEEN 7000 AND 7999;
DELETE FROM `system_menu` WHERE `id` BETWEEN 7000 AND 7999;

INSERT INTO `system_menu`
  (`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,
   `status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`)
VALUES
  -- 顶级目录
  (7000, '合同管理', '', 1, 45, 0, '/clm', 'ep:document', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- 合同台账
  (7030, '合同台账', '', 2, 1, 7000, 'contract', 'ep:tickets', 'clm/contract/index', 'ClmContract', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7031, '合同查询',     'clm:contract:query',         3, 1, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7032, '合同创建',     'clm:contract:create',        3, 2, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7033, '合同更新',     'clm:contract:update',        3, 3, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7034, '合同删除',     'clm:contract:delete',        3, 4, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7035, '合同提交审批', 'clm:contract:submit',        3, 5, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7036, '合同文档下载', 'clm:contract:download',      3, 6, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7037, '合同成员管理', 'clm:contract:manage-member', 3, 7, 7030, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- 合同类型
  (7010, '合同类型', '', 2, 2, 7000, 'contract-type', 'ep:collection-tag', 'clm/contractType/index', 'ClmContractType', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7011, '合同类型查询', 'clm:contract-type:query',   3, 1, 7010, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7012, '合同类型创建', 'clm:contract-type:create',  3, 2, 7010, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7013, '合同类型更新', 'clm:contract-type:update',  3, 3, 7010, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7014, '合同类型删除', 'clm:contract-type:delete',  3, 4, 7010, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7015, '合同类型发布', 'clm:contract-type:publish', 3, 5, 7010, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),

  -- 签约方
  (7020, '签约方', '', 2, 3, 7000, 'party', 'ep:office-building', 'clm/party/index', 'ClmParty', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7021, '签约方查询', 'clm:party:query',  3, 1, 7020, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7022, '签约方创建', 'clm:party:create', 3, 2, 7020, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7023, '签约方更新', 'clm:party:update', 3, 3, 7020, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
  (7024, '签约方删除', 'clm:party:delete', 3, 4, 7020, '', '', '', '', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 普通角色（id=2，common）也授予全部 CLM 功能权限：功能权限只是第一层，合同对象级权限在服务端另行校验
INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 2, m.id, '1', NOW(), '1', NOW(), b'0', 1
FROM `system_menu` m WHERE m.id BETWEEN 7000 AND 7999 AND m.deleted = b'0';

-- =====================================================================
-- 字典
-- =====================================================================
DELETE FROM `system_dict_data` WHERE `dict_type` LIKE 'clm_%';
DELETE FROM `system_dict_type` WHERE `type` LIKE 'clm_%';

INSERT INTO `system_dict_type` (`id`,`name`,`type`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`deleted_time`) VALUES
  (1070001, 'CLM 合同审批状态',   'clm_approval_status',         0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070002, 'CLM 合同生命周期',   'clm_lifecycle_status',        0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070003, 'CLM 签约方类型',     'clm_party_type',              0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070004, 'CLM 合同签约方角色', 'clm_contract_party_role',     0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070005, 'CLM 文档版本来源',   'clm_document_source_type',    0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070006, 'CLM 类型版本状态',   'clm_type_version_status',     0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070007, 'CLM 流程绑定状态',   'clm_workflow_binding_status', 0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070008, 'CLM 参与人角色',     'clm_participant_role',        0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070009, 'CLM 文档角色',       'clm_document_role',           0, NULL, '1', NOW(), '1', NOW(), b'0', NULL),
  (1070010, 'CLM 审计动作',       'clm_audit_action',            0, NULL, '1', NOW(), '1', NOW(), b'0', NULL);

INSERT INTO `system_dict_data` (`id`,`sort`,`label`,`value`,`dict_type`,`status`,`color_type`,`css_class`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
  -- clm_approval_status（与 BPM 流程实例状态同值）
  (1070101, 0, '未提交',   '0', 'clm_approval_status', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070102, 1, '审批中',   '1', 'clm_approval_status', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070103, 2, '审批通过', '2', 'clm_approval_status', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070104, 3, '审批驳回', '3', 'clm_approval_status', 0, 'danger',  '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070105, 4, '已取消',   '4', 'clm_approval_status', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_lifecycle_status
  (1070111, 1, '草稿',   '1', 'clm_lifecycle_status', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070112, 2, '已批准', '2', 'clm_lifecycle_status', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070113, 3, '已生效', '3', 'clm_lifecycle_status', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070114, 4, '已到期', '4', 'clm_lifecycle_status', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070115, 5, '已终止', '5', 'clm_lifecycle_status', 0, 'danger',  '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070116, 6, '作废',   '6', 'clm_lifecycle_status', 0, 'danger',  '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_party_type
  (1070121, 1, '企业', '1', 'clm_party_type', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070122, 2, '个人', '2', 'clm_party_type', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_contract_party_role
  (1070131, 1, '我方',   'OUR_SIDE',     'clm_contract_party_role', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070132, 2, '相对方', 'COUNTERPARTY', 'clm_contract_party_role', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070133, 3, '其他',   'OTHER',        'clm_contract_party_role', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_document_source_type
  (1070141, 1, '上传',         'UPLOAD',       'clm_document_source_type', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070142, 2, '在线编辑',     'ONLINE_EDIT',  'clm_document_source_type', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070143, 3, '线下定稿回传', 'MANUAL_FINAL', 'clm_document_source_type', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_type_version_status
  (1070151, 0, '草稿',   '0', 'clm_type_version_status', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070152, 1, '已发布', '1', 'clm_type_version_status', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_workflow_binding_status
  (1070161, 0, '准备中', '0', 'clm_workflow_binding_status', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070162, 1, '审批中', '1', 'clm_workflow_binding_status', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070163, 2, '通过',   '2', 'clm_workflow_binding_status', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070164, 3, '驳回',   '3', 'clm_workflow_binding_status', 0, 'danger',  '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070165, 4, '取消',   '4', 'clm_workflow_binding_status', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_participant_role
  (1070171, 1, '负责人', 'OWNER',        'clm_participant_role', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070172, 2, '协作人', 'COLLABORATOR', 'clm_participant_role', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070173, 3, '查看人', 'VIEWER',       'clm_participant_role', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_document_role
  (1070181, 1, '正文', 'MAIN',       'clm_document_role', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070182, 2, '附件', 'ATTACHMENT', 'clm_document_role', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  -- clm_audit_action
  (1070191,  1, '创建合同',     'CONTRACT_CREATE',    'clm_audit_action', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070192,  2, '修改合同',     'CONTRACT_UPDATE',    'clm_audit_action', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070193,  3, '删除合同',     'CONTRACT_DELETE',    'clm_audit_action', 0, 'danger',  '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070194,  4, '提交审批',     'CONTRACT_SUBMIT',    'clm_audit_action', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070195,  5, '审批结果',     'APPROVAL_RESULT',    'clm_audit_action', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070196,  6, '上传文档版本', 'DOCUMENT_UPLOAD',    'clm_audit_action', 0, 'primary', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070197,  7, '下载文档版本', 'DOCUMENT_DOWNLOAD',  'clm_audit_action', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070198,  8, '参与人变更',   'PARTICIPANT_UPDATE', 'clm_audit_action', 0, 'warning', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070199,  9, '发布类型版本', 'TYPE_PUBLISH',       'clm_audit_action', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070200, 10, '打开在线编辑', 'ONLINE_EDIT_OPEN',   'clm_audit_action', 0, 'info',    '', NULL, '1', NOW(), '1', NOW(), b'0'),
  (1070201, 11, '在线编辑保存', 'ONLINE_EDIT_SAVE',   'clm_audit_action', 0, 'success', '', NULL, '1', NOW(), '1', NOW(), b'0');
