package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface HandoverErrors {
    ErrorCode CASE_NOT_EXISTS = new ErrorCode(1_070_020_000, "经办人变更事项不存在");
    ErrorCode SOURCE_USER_NOT_EXISTS = new ErrorCode(1_070_020_001, "离职用户【{}】不存在");
    ErrorCode TARGET_USER_INVALID = new ErrorCode(1_070_020_002, "接手用户不存在、已停用或与离职用户相同");
    ErrorCode TARGET_SCOPE_DENIED = new ErrorCode(1_070_020_003,
            "接手用户没有覆盖合同组织和类型的有效合同域授权");
    ErrorCode ITEM_NOT_EXISTS = new ErrorCode(1_070_020_004, "交接项【{}】不存在或不属于当前经办人变更事项");
    ErrorCode ITEM_TYPE_INVALID = new ErrorCode(1_070_020_005, "交接项类型【{}】不支持");
    ErrorCode CONTRACT_NOT_EXISTS = new ErrorCode(1_070_020_006, "交接合同【{}】不存在");
    ErrorCode REASON_REQUIRED = new ErrorCode(1_070_020_007, "交接原因不能为空");
}
