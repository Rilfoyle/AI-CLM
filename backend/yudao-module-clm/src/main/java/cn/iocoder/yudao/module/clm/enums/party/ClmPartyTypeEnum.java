package cn.iocoder.yudao.module.clm.enums.party;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 签约方主体类型
 */
@Getter
@AllArgsConstructor
public enum ClmPartyTypeEnum implements ArrayValuable<Integer> {

    COMPANY(1, "企业"),
    INDIVIDUAL(2, "个人");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ClmPartyTypeEnum::getStatus).toArray(Integer[]::new);

    /**
     * 状态值
     */
    private final Integer status;
    /**
     * 描述
     */
    private final String desc;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static ClmPartyTypeEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
