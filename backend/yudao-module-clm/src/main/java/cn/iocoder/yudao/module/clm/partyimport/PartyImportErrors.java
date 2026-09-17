package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface PartyImportErrors {
    ErrorCode JOB_NOT_EXISTS = new ErrorCode(1_070_019_000, "相对方导入任务不存在");
    ErrorCode ITEM_NOT_EXISTS = new ErrorCode(1_070_019_001, "相对方导入行不存在");
    ErrorCode FILE_TYPE_INVALID = new ErrorCode(1_070_019_002, "仅支持 xls/xlsx 导入文件");
    ErrorCode FILE_EMPTY = new ErrorCode(1_070_019_003, "导入文件为空或没有数据行");
    ErrorCode FILE_TOO_LARGE = new ErrorCode(1_070_019_004, "导入文件不能超过 10 MB");
    ErrorCode ROW_LIMIT_EXCEEDED = new ErrorCode(1_070_019_005, "单次最多导入 5000 行");
    ErrorCode JOB_STATUS_INVALID = new ErrorCode(1_070_019_006, "导入任务当前状态【{}】不允许该操作");
    ErrorCode ITEM_ALREADY_IMPORTED = new ErrorCode(1_070_019_007, "已导入行不能修改");
    ErrorCode ITEM_JOB_MISMATCH = new ErrorCode(1_070_019_008, "导入行不属于指定任务");
    ErrorCode JOB_KEY_REQUIRED = new ErrorCode(1_070_019_009, "导入任务幂等键不能为空");
}
