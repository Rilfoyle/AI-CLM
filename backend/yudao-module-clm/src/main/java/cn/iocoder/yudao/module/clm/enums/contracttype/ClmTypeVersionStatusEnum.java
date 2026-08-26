package cn.iocoder.yudao.module.clm.enums.contracttype;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 合同类型版本状态
 */
@Getter
@AllArgsConstructor
public enum ClmTypeVersionStatusEnum implements ArrayValuable<Integer> {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ClmTypeVersionStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static ClmTypeVersionStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
