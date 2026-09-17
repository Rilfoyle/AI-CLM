package cn.iocoder.yudao.module.clm.commitment;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;

@TableName("clm_commitment")
@KeySequence("clm_commitment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CommitmentDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contractId;
    private String category;
    private String content;
    private Long ownerUserId;
    private LocalDate dueDate;
    private String riskLevel;
}
