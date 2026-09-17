package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NumberingRulePageReqVO extends PageParam {
    private String ruleCode;
    private Long contractTypeId;
    private String status;
}
