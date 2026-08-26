package cn.iocoder.yudao.module.clm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 合同生命周期状态
 */
@Getter
@AllArgsConstructor
public enum ClmLifecycleStatusEnum implements ArrayValuable<Integer> {

    DRAFT(1, "草稿"),
    APPROVED(2, "已批准"),
    EFFECTIVE(3, "已生效"),
    EXPIRED(4, "已到期"),
    TERMINATED(5, "已终止"),
    VOID(6, "作废");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ClmLifecycleStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static ClmLifecycleStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
