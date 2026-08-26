package cn.iocoder.yudao.module.clm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 合同签约方角色
 */
@Getter
@AllArgsConstructor
public enum ClmContractPartyRoleEnum {

    OUR_SIDE("OUR_SIDE", "我方"),
    COUNTERPARTY("COUNTERPARTY", "相对方"),
    OTHER("OTHER", "其他");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmContractPartyRoleEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
