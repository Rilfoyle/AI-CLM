package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_user_scope_item")
@KeySequence("clm_user_scope_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserScopeItemDO extends TenantBaseDO {
    public static final String TYPE_ORG = "ORG";
    public static final String TYPE_CONTRACT_TYPE = "CONTRACT_TYPE";

    @TableId private Long id;
    private Long userScopeId;
    private String scopeType;
    private Long scopeId;
}
