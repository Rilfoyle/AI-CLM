package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_template")
@KeySequence("clm_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TemplateDO extends TenantBaseDO {
    @TableId private Long id;
    private String code;
    private String name;
    private Long contractTypeId;
    private Long currentVersionId;
    private Integer status;
    private String description;
}
