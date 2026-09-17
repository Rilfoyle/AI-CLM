package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_permission_policy_version")
@KeySequence("clm_permission_policy_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PermissionPolicyVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Integer versionNo;
    private String status;
    private String roleCapabilitiesJson;
    private String scopeLimitsJson;
    private String nodeEditPolicyJson;
    private String remark;
    private Long publishedBy;
    private LocalDateTime publishedTime;
}
