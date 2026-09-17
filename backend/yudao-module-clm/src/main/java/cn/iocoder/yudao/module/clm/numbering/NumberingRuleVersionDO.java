package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_numbering_rule_version")
@KeySequence("clm_numbering_rule_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NumberingRuleVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private String ruleCode;
    private Long contractTypeId;
    private Integer versionNo;
    private String status;
    private String prefix;
    private String datePattern;
    @TableField("`separator`")
    private String separator;
    private Integer sequenceLength;
    private String resetPeriod;
    /** 是否包含合同我方主体简称号段；一期发布规则必须开启。 */
    private Boolean includePartyShortName;
    private LocalDateTime effectiveTime;
}
