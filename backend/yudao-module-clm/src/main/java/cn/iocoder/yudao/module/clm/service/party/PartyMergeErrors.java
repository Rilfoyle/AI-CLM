package cn.iocoder.yudao.module.clm.service.party;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

interface PartyMergeErrors {

    ErrorCode PARTY_MERGE_SAME_PARTY = new ErrorCode(1_070_002_002, "源主体和目标主体不能相同");
    ErrorCode PARTY_MERGE_INTERNAL_FLAG_MISMATCH = new ErrorCode(1_070_002_003, "我方主体与相对方不能相互合并");
    ErrorCode PARTY_MERGE_PARTY_TYPE_MISMATCH = new ErrorCode(1_070_002_004, "企业与个人主体不能相互合并");
    ErrorCode PARTY_MERGE_TARGET_DISABLED = new ErrorCode(1_070_002_005, "目标主体已停用，不能作为合并保留记录");
    ErrorCode PARTY_MERGE_SOURCE_ALREADY_MERGED = new ErrorCode(1_070_002_006,
            "源主体已合并到主体【{}】，不能再合并到【{}】");
}
