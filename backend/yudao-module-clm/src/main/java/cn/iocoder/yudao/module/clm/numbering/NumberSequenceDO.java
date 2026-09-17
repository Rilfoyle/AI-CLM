package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_number_sequence")
@KeySequence("clm_number_sequence_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NumberSequenceDO extends TenantBaseDO {
    @TableId private Long id;
    private Long ruleVersionId;
    private String periodKey;
    private Long currentValue;
}
