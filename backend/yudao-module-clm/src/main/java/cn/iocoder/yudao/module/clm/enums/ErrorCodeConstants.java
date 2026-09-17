package cn.iocoder.yudao.module.clm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * CLM 错误码枚举类
 *
 * clm 系统，使用 1-070-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 合同类型 1-070-000-000 ==========
    ErrorCode CONTRACT_TYPE_NOT_EXISTS = new ErrorCode(1_070_000_000, "合同分类不存在");
    ErrorCode CONTRACT_TYPE_CODE_DUPLICATE = new ErrorCode(1_070_000_001, "合同分类编码【{}】已存在");
    ErrorCode CONTRACT_TYPE_IN_USE = new ErrorCode(1_070_000_002, "合同分类已被合同引用，无法删除");
    ErrorCode CONTRACT_TYPE_NOT_PUBLISHED = new ErrorCode(1_070_000_003, "合同分类尚未发布版本，无法使用");
    ErrorCode CONTRACT_TYPE_TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_000_010, "合同分类未配置范本文件");

    // ========== 合同类型版本 1-070-001-000 ==========
    ErrorCode CONTRACT_TYPE_VERSION_NOT_EXISTS = new ErrorCode(1_070_001_000, "合同分类版本不存在");
    ErrorCode CONTRACT_TYPE_VERSION_NOT_DRAFT = new ErrorCode(1_070_001_001, "合同分类版本不是草稿状态，不允许修改");
    ErrorCode CONTRACT_TYPE_DRAFT_EXISTS = new ErrorCode(1_070_001_002, "合同分类已存在草稿版本");

    // ========== 签约方 1-070-002-000 ==========
    ErrorCode PARTY_NOT_EXISTS = new ErrorCode(1_070_002_000, "签约方不存在");
    ErrorCode PARTY_IN_USE = new ErrorCode(1_070_002_001, "签约方已被合同引用，无法删除");

    // ========== 合同 1-070-003-000 ==========
    ErrorCode CONTRACT_NOT_EXISTS = new ErrorCode(1_070_003_000, "合同不存在");
    ErrorCode CONTRACT_PARTY_REQUIRED = new ErrorCode(1_070_003_001, "合同至少需要一个我方主体和一个相对方");
    ErrorCode CONTRACT_DELETE_NOT_ALLOWED = new ErrorCode(1_070_003_002, "合同当前状态不允许删除");
    ErrorCode CONTRACT_APPROVAL_RUNNING = new ErrorCode(1_070_003_003, "合同正在审批中");
    ErrorCode CONTRACT_STATUS_NOT_ALLOW_SUBMIT = new ErrorCode(1_070_003_004, "合同当前生命周期状态不允许提交审批");
    ErrorCode CONTRACT_MAIN_DOCUMENT_REQUIRED = new ErrorCode(1_070_003_005, "合同尚未上传正文，无法提交审批");
    ErrorCode CONTRACT_NOTHING_TO_APPROVE = new ErrorCode(1_070_003_006, "当前正文版本已审批通过，无需重复提交");
    ErrorCode CONTRACT_CUSTOM_FIELD_UNKNOWN = new ErrorCode(1_070_003_007, "扩展字段【{}】不在类型定义中");
    ErrorCode CONTRACT_CUSTOM_FIELD_REQUIRED = new ErrorCode(1_070_003_008, "扩展字段【{}】为必填");
    ErrorCode CONTRACT_LOCKED_BY_APPROVAL = new ErrorCode(1_070_003_009, "合同审批中，已锁定，不允许修改");
    ErrorCode CONTRACT_TITLE_REQUIRED = new ErrorCode(1_070_003_010, "合同标题不能为空");
    // 注意：1_070_003_010 已被 CONTRACT_TITLE_REQUIRED 占用，归档错误码顺延为 011
    ErrorCode CONTRACT_ARCHIVE_NOT_ALLOWED = new ErrorCode(1_070_003_011, "仅审批通过的合同可归档定稿");
    ErrorCode CONTRACT_COMMITMENT_CONFIRM_REQUIRED = new ErrorCode(1_070_003_012, "请维护重大承诺，或明确确认无重大承诺");
    ErrorCode CONTRACT_RESTORE_NOT_ALLOWED = new ErrorCode(1_070_003_013, "仅允许恢复未提交或已取消的草稿合同");
    ErrorCode CONTRACT_LEGAL_CONCLUSION_REQUIRED = new ErrorCode(1_070_003_014, "非标准、正文已修改或高风险合同必须先完成当前修订的法务协同");

    // ========== 文档 1-070-004-000 ==========
    ErrorCode DOCUMENT_NOT_EXISTS = new ErrorCode(1_070_004_000, "合同文档不存在");
    ErrorCode DOCUMENT_VERSION_NOT_EXISTS = new ErrorCode(1_070_004_001, "文档版本不存在");
    ErrorCode DOCUMENT_FILE_TOO_LARGE = new ErrorCode(1_070_004_002, "文件大小超过限制（最大 {} MB）");
    ErrorCode DOCUMENT_FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_070_004_003, "不支持的文件类型【{}】");
    ErrorCode DOCUMENT_CHECKSUM_MISMATCH = new ErrorCode(1_070_004_004, "文档内容校验和不一致，请重新上传");
    ErrorCode DOCUMENT_BLOB_NOT_EXISTS = new ErrorCode(1_070_004_005, "文档内容不存在");
    ErrorCode DOCUMENT_FILE_EMPTY = new ErrorCode(1_070_004_006, "上传文件不能为空");

    // ========== 流程绑定 1-070-005-000 ==========
    ErrorCode WORKFLOW_BINDING_NOT_EXISTS = new ErrorCode(1_070_005_000, "流程绑定不存在");

    // ========== 参与人 1-070-006-000 ==========
    ErrorCode PARTICIPANT_ROLE_INVALID = new ErrorCode(1_070_006_000, "参与人角色【{}】不合法");
    ErrorCode PARTICIPANT_USER_NOT_EXISTS = new ErrorCode(1_070_006_001, "参与人用户【{}】不存在");

    // ========== 权限 1-070-007-000 ==========
    ErrorCode CONTRACT_ACCESS_DENIED = new ErrorCode(1_070_007_000, "无权访问该合同");

    // ========== 在线编辑 1-070-008-000 ==========
    ErrorCode ONLINE_EDIT_NOT_SUPPORTED = new ErrorCode(1_070_008_000, "暂不支持在线编辑");
    ErrorCode ONLINE_EDIT_TOKEN_INVALID = new ErrorCode(1_070_008_001, "在线编辑访问令牌无效或已过期");
    ErrorCode ONLINE_EDIT_DISABLED = new ErrorCode(1_070_008_002, "未启用 ONLYOFFICE 在线编辑");
    ErrorCode ONLINE_EDIT_NOT_CURRENT_VERSION = new ErrorCode(1_070_008_003, "只能在线编辑文档的当前版本");
    ErrorCode ONLINE_EDIT_UNSUPPORTED_TYPE = new ErrorCode(1_070_008_004, "该文件类型【{}】不支持在线编辑");
    ErrorCode ONLINE_EDIT_VERSION_FROZEN = new ErrorCode(1_070_008_005, "该版本已冻结，不允许在线编辑");
    ErrorCode ONLINE_EDIT_CALLBACK_FETCH_FAILED = new ErrorCode(1_070_008_006, "从 Document Server 拉取文件失败");

    // ========== 合同修订 1-070-009-000 ==========
    ErrorCode CONTRACT_REVISION_NOT_EXISTS = new ErrorCode(1_070_009_000, "合同修订不存在");
    ErrorCode CONTRACT_REVISION_CONFLICT = new ErrorCode(1_070_009_001, "合同已产生新修订，请刷新后重试；当前修订为【{}】");
    ErrorCode CONTRACT_REVISION_CONTRACT_MISMATCH = new ErrorCode(1_070_009_002, "修订不属于当前合同");
    ErrorCode COLLABORATION_NOT_EXISTS = new ErrorCode(1_070_009_100, "法务协同不存在");
    ErrorCode COLLABORATION_ACTIVE_EXISTS = new ErrorCode(1_070_009_101, "当前合同已有进行中的法务协同");
    ErrorCode COLLABORATION_ACTION_DENIED = new ErrorCode(1_070_009_102, "无权处理该法务协同");
    ErrorCode COLLABORATION_NOT_ACTIVE = new ErrorCode(1_070_009_103, "法务协同已失效或完成，不能继续操作");
    ErrorCode COLLABORATION_REVISION_REQUIRED = new ErrorCode(1_070_009_104, "协同动作必须绑定合同修订");
    ErrorCode COLLABORATION_CANCEL_HANDLED = new ErrorCode(1_070_009_105, "法务已开始处理，不能取消邀请");

    // ========== 独立范本 1-070-010-000 ==========
    ErrorCode TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_010_000, "合同范本不存在");
    ErrorCode TEMPLATE_VERSION_NOT_PUBLISHED = new ErrorCode(1_070_010_001, "合同范本版本不存在或尚未发布");

    // ========== 重大承诺 1-070-011-000 ==========
    ErrorCode COMMITMENT_NOT_EXISTS = new ErrorCode(1_070_011_000, "重大承诺不存在");
    ErrorCode COMMITMENT_CONTRACT_MISMATCH = new ErrorCode(1_070_011_001, "重大承诺不属于当前合同");
    ErrorCode COMMITMENT_NONE_CONFLICT = new ErrorCode(1_070_011_002, "当前仍有重大承诺，不能确认无重大承诺");

    // ========== 编号与路由 1-070-012-000 ==========
    ErrorCode ROUTING_RULE_NO_MATCH = new ErrorCode(1_070_012_000, "没有命中可用的业务单据流程配置");
    ErrorCode ROUTING_RULE_AMBIGUOUS = new ErrorCode(1_070_012_001, "命中多条业务单据流程配置，请管理员修正规则");

    // ========== CLM 审批 façade 1-070-013-000 ==========
    ErrorCode APPROVAL_TASK_ALREADY_DECIDED = new ErrorCode(1_070_013_000, "审批任务已经决定，不能绑定另一修订");
    ErrorCode APPROVAL_RETURN_TARGET_REQUIRED = new ErrorCode(1_070_013_001, "退回目标节点不能为空");
    ErrorCode APPROVAL_CASE_NOT_RUNNING = new ErrorCode(1_070_013_002, "审批业务单已不在运行中");
    ErrorCode APPROVAL_NODE_EDIT_DENIED = new ErrorCode(1_070_013_003, "当前审批节点未授权编辑合同");
    ErrorCode APPROVAL_EDIT_FIELD_DENIED = new ErrorCode(1_070_013_004, "当前审批节点不允许修改字段【{}】");
    ErrorCode APPROVAL_EDIT_REQUEST_CONFLICT = new ErrorCode(1_070_013_005, "审批编辑请求幂等键已用于其他内容");
    ErrorCode APPROVAL_CASE_NOT_CURRENT = new ErrorCode(1_070_013_006, "审批业务单不是合同当前运行中的审批");
    ErrorCode APPROVAL_STARTER_WITHDRAW_DENIED = new ErrorCode(1_070_013_007, "只有审批发起人可以撤回当前审批");
    ErrorCode APPROVAL_TASK_NOT_CLM = new ErrorCode(1_070_013_008, "审批任务不存在或不属于合同审批");

    // ========== 治理与对账 1-070-014-000 ==========
    ErrorCode GOVERNANCE_ISSUE_NOT_EXISTS = new ErrorCode(1_070_014_000, "配置异常记录不存在");

    // ========== 个人查询 1-070-015-000 ==========
    ErrorCode SAVED_FILTER_NOT_EXISTS = new ErrorCode(1_070_015_000, "保存筛选不存在");
    ErrorCode SAVED_FILTER_ACCESS_DENIED = new ErrorCode(1_070_015_001, "无权修改该保存筛选");
    ErrorCode SAVED_FILTER_INVALID = new ErrorCode(1_070_015_002, "保存筛选包含不支持的条件");

    // ========== AI 修订审查 1-070-016-000 ==========
    ErrorCode AI_FINDING_NOT_EXISTS = new ErrorCode(1_070_016_000, "AI 审查发现不存在");
    ErrorCode AI_FINDING_RESOLUTION_INVALID = new ErrorCode(1_070_016_001, "AI 审查处理结果不合法");

    // ========== 外部集成 1-070-017-000 ==========
    ErrorCode INTEGRATION_DELIVERY_NOT_EXISTS = new ErrorCode(1_070_017_000, "集成消息投递记录不存在");

    // ========== 合同域权限 1-070-018-000 ==========
    ErrorCode PERMISSION_POLICY_NOT_EXISTS = new ErrorCode(1_070_018_000, "数据权限规则版本不存在");
    ErrorCode PERMISSION_POLICY_DRAFT_EXISTS = new ErrorCode(1_070_018_001, "已有数据权限规则草稿");
    ErrorCode PERMISSION_POLICY_NOT_DRAFT = new ErrorCode(1_070_018_002, "已发布的数据权限规则不可修改");
    ErrorCode PERMISSION_POLICY_NOT_PUBLISHED = new ErrorCode(1_070_018_003, "只能按已发布的数据权限规则授权");
    ErrorCode PERMISSION_POLICY_INVALID = new ErrorCode(1_070_018_004, "数据权限规则格式不合法");
    ErrorCode USER_SCOPE_NOT_EXISTS = new ErrorCode(1_070_018_100, "用户合同域授权不存在");
    ErrorCode USER_SCOPE_ROLE_INVALID = new ErrorCode(1_070_018_101, "合同域产品角色不合法");
    ErrorCode USER_SCOPE_ROLE_NOT_INSTALLED = new ErrorCode(1_070_018_102, "合同域产品角色【{}】尚未安装");
    ErrorCode USER_SCOPE_EXCEEDS_POLICY = new ErrorCode(1_070_018_103, "用户授权范围超过已发布数据权限规则上限：{}");

}
