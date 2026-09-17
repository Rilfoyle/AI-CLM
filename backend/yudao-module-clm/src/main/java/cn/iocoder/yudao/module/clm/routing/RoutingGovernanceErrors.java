package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface RoutingGovernanceErrors {
    ErrorCode RULE_NOT_EXISTS = new ErrorCode(1_070_012_020, "业务单据流程配置版本不存在");
    ErrorCode RULE_NOT_DRAFT = new ErrorCode(1_070_012_021, "只有草稿业务单据流程配置可以修改或发布");
    ErrorCode DRAFT_EXISTS = new ErrorCode(1_070_012_022, "业务单据流程配置【{}】已有草稿版本");
    ErrorCode CODE_IMMUTABLE = new ErrorCode(1_070_012_023, "业务单据流程配置编码创建后不可修改");
    ErrorCode SCOPE_IMMUTABLE = new ErrorCode(1_070_012_024, "同一业务单据流程配置的适用合同分类不可修改");
    ErrorCode CONDITION_INVALID = new ErrorCode(1_070_012_025, "业务单据流程配置条件不合法：{}");
    ErrorCode PROCESS_DEFINITION_INVALID = new ErrorCode(1_070_012_026, "审批流程定义【{}】不存在、未启用或审批人配置不完整");
    ErrorCode PRECHECK_SCOPE_MISMATCH = new ErrorCode(1_070_012_027, "候选流程配置不适用于预检的合同分类");
}
