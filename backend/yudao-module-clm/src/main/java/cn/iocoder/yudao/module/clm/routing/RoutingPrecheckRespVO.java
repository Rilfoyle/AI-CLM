package cn.iocoder.yudao.module.clm.routing;

import lombok.Data;

import java.util.List;

@Data
public class RoutingPrecheckRespVO {
    private String result;
    private Integer matchedCount;
    private List<Long> matchedRuleVersionIds;
    private List<String> processDefinitionKeys;
}
