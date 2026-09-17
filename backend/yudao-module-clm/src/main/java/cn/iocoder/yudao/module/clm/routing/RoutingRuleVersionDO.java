package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_routing_rule_version")
@KeySequence("clm_routing_rule_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RoutingRuleVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private String ruleCode;
    private String name;
    private Long contractTypeId;
    private Integer versionNo;
    private Integer priority;
    private String conditionJson;
    private String processDefinitionKey;
    private String status;
    private LocalDateTime effectiveTime;
}
