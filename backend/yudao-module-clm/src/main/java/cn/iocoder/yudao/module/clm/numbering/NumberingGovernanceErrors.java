package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface NumberingGovernanceErrors {
    ErrorCode RULE_NOT_EXISTS = new ErrorCode(1_070_012_010, "编码规则版本不存在");
    ErrorCode RULE_NOT_DRAFT = new ErrorCode(1_070_012_011, "只有草稿编码规则可以修改或发布");
    ErrorCode DRAFT_EXISTS = new ErrorCode(1_070_012_012, "编码规则【{}】已有草稿版本");
    ErrorCode CODE_IMMUTABLE = new ErrorCode(1_070_012_013, "编码规则编码创建后不可修改");
    ErrorCode SCOPE_IMMUTABLE = new ErrorCode(1_070_012_014, "同一编码规则的适用合同分类不可修改");
    ErrorCode PARAM_INVALID = new ErrorCode(1_070_012_015, "编码规则参数不合法：{}");
    ErrorCode OUR_PARTY_SHORT_NAME_REQUIRED = new ErrorCode(1_070_012_016, "我方主体简称缺失或不合法，无法分配合同编号");
}
