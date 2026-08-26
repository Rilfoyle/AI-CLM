package cn.iocoder.yudao.module.clm.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 合同参与人 DO（对象级权限）
 */
@TableName("clm_contract_participant")
@KeySequence("clm_contract_participant_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContractParticipantDO extends TenantBaseDO {

    /**
     * 主体类型 - 用户
     */
    public static final String PRINCIPAL_TYPE_USER = "USER";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 合同编号
     */
    private Long contractId;
    /**
     * 主体类型：USER（POC 仅支持用户）
     */
    private String principalType;
    /**
     * 主体编号
     */
    private Long principalId;
    /**
     * 角色：OWNER 负责人 / COLLABORATOR 协作人 / VIEWER 查看人
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.contract.ClmParticipantRoleEnum}
     */
    private String roleCode;
    /**
     * 可查看
     */
    private Boolean canView;
    /**
     * 可编辑
     */
    private Boolean canEdit;
    /**
     * 可下载
     */
    private Boolean canDownload;
    /**
     * 可管理参与人
     */
    private Boolean canManage;

}
