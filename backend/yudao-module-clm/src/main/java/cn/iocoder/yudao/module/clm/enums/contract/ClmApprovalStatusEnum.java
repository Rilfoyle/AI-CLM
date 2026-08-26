package cn.iocoder.yudao.module.clm.enums.contract;

import cn.hutool.core.util.ArrayUtil;
import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 合同审批状态（与 BPM 流程实例状态同值）
 */
@Getter
@AllArgsConstructor
public enum ClmApprovalStatusEnum implements ArrayValuable<Integer> {

    NOT_SUBMITTED(0, "未提交"),
    RUNNING(1, "审批中"),
    APPROVED(2, "审批通过"),
    REJECTED(3, "审批驳回"),
    CANCELED(4, "已取消");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ClmApprovalStatusEnum::getStatus).toArray(Integer[]::new);

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

    public static ClmApprovalStatusEnum valueOf(Integer status) {
        return ArrayUtil.firstMatch(item -> item.getStatus().equals(status), values());
    }

}
