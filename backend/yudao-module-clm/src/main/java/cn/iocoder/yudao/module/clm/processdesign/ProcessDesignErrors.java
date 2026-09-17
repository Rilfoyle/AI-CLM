package cn.iocoder.yudao.module.clm.processdesign;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * CLM 流程定义治理错误码。
 */
public interface ProcessDesignErrors {

    ErrorCode PROCESS_MODEL_NOT_CONTRACT = new ErrorCode(1_070_012_030,
            "流程定义不存在或不属于 CLM 合同流程");
    ErrorCode PROCESS_KEY_PREFIX_INVALID = new ErrorCode(1_070_012_031,
            "CLM 合同流程编码必须以 clm_contract_ 开头");
    ErrorCode PROCESS_KEY_IMMUTABLE = new ErrorCode(1_070_012_032,
            "流程编码创建后不可修改");
    ErrorCode PROCESS_MODEL_METADATA_INVALID = new ErrorCode(1_070_012_033,
            "流程定义元数据不符合 CLM 合同流程约束，请先保存流程图后再发布");
    ErrorCode PROCESS_MODEL_VERSION_CONFLICT = new ErrorCode(1_070_012_034,
            "流程定义已被其他管理员更新，请刷新后重试");
    ErrorCode PROCESS_MODEL_MANAGER_MISSING = new ErrorCode(1_070_012_035,
            "流程定义缺少原始流程管理员，无法安全保存或发布，请联系系统管理员修复数据");
    ErrorCode PROCESS_SIMPLE_MODEL_INVALID = new ErrorCode(1_070_012_036,
            "流程图不符合 CLM 安全约束：{}");

}
