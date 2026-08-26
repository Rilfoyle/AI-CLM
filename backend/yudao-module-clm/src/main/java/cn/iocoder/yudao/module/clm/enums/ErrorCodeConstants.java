package cn.iocoder.yudao.module.clm.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * CLM 错误码枚举类
 *
 * clm 系统，使用 1-070-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 合同类型 1-070-000-000 ==========
    ErrorCode CONTRACT_TYPE_NOT_EXISTS = new ErrorCode(1_070_000_000, "合同类型不存在");
    ErrorCode CONTRACT_TYPE_CODE_DUPLICATE = new ErrorCode(1_070_000_001, "合同类型编码【{}】已存在");
    ErrorCode CONTRACT_TYPE_IN_USE = new ErrorCode(1_070_000_002, "合同类型已被合同引用，无法删除");
    ErrorCode CONTRACT_TYPE_NOT_PUBLISHED = new ErrorCode(1_070_000_003, "合同类型尚未发布版本，无法使用");
    ErrorCode CONTRACT_TYPE_TEMPLATE_NOT_EXISTS = new ErrorCode(1_070_000_010, "合同类型未配置范本文件");

    // ========== 合同类型版本 1-070-001-000 ==========
    ErrorCode CONTRACT_TYPE_VERSION_NOT_EXISTS = new ErrorCode(1_070_001_000, "合同类型版本不存在");
    ErrorCode CONTRACT_TYPE_VERSION_NOT_DRAFT = new ErrorCode(1_070_001_001, "合同类型版本不是草稿状态，不允许修改");
    ErrorCode CONTRACT_TYPE_DRAFT_EXISTS = new ErrorCode(1_070_001_002, "合同类型已存在草稿版本");

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

}
