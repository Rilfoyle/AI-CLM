package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoutingRulePageReqVO extends PageParam {
    private String ruleCode;
    private String name;
    private Long contractTypeId;
    private String status;
}
