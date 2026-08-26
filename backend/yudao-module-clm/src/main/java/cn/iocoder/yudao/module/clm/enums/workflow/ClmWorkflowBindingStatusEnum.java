package cn.iocoder.yudao.module.clm.enums.workflow;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 流程绑定状态（与 BPM 流程实例状态同值）
 */
@Getter
@AllArgsConstructor
public enum ClmWorkflowBindingStatusEnum implements ArrayValuable<Integer> {

    PREPARING(0, "准备中"),
    RUNNING(1, "审批中"),
    APPROVED(2, "通过"),
    REJECTED(3, "驳回"),
    CANCELED(4, "取消");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ClmWorkflowBindingStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static ClmWorkflowBindingStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
