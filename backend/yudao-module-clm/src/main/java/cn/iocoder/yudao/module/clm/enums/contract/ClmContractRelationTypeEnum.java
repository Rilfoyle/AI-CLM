package cn.iocoder.yudao.module.clm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合同与来源合同的关联类型
 */
@Getter
@AllArgsConstructor
public enum ClmContractRelationTypeEnum {

    COPY("COPY", "复制"),
    RENEWAL("RENEWAL", "续签");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmContractRelationTypeEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
