package cn.iocoder.yudao.module.clm.routing;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class RoutingRuleRespVO {
    private Long id;
    private String ruleCode;
    private String name;
    private Long contractTypeId;
    private Integer versionNo;
    private Integer priority;
    private Map<String, Object> condition;
    private String processDefinitionKey;
    private String status;
    private LocalDateTime effectiveTime;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
