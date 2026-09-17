package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_user_scope")
@KeySequence("clm_user_scope_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserScopeDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String roleCode;
    private String orgScopeJson;
    private String typeScopeJson;
    private Long policyVersionId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String status;
}
