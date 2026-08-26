package cn.iocoder.yudao.module.clm.enums.audit;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计聚合类型
 */
@Getter
@AllArgsConstructor
public enum ClmAuditAggregateTypeEnum {

    CONTRACT("CONTRACT", "合同"),
    DOCUMENT("DOCUMENT", "文档"),
    CONTRACT_TYPE("CONTRACT_TYPE", "合同类型");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmAuditAggregateTypeEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
