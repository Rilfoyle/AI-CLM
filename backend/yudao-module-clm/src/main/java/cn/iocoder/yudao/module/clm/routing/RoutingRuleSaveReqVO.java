package cn.iocoder.yudao.module.clm.routing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class RoutingRuleSaveReqVO {
    private Long id;
    @NotBlank(message = "路由编码不能为空")
    @Size(max = 64, message = "路由编码长度不能超过 64")
    private String ruleCode;
    @NotBlank(message = "路由名称不能为空")
    @Size(max = 128, message = "路由名称长度不能超过 128")
    private String name;
    private Long contractTypeId;
    @NotNull(message = "路由优先级不能为空")
    private Integer priority;
    private Map<String, Object> condition;
    @NotBlank(message = "审批流程定义标识不能为空")
    @Size(max = 64, message = "审批流程定义标识长度不能超过 64")
    private String processDefinitionKey;
}
