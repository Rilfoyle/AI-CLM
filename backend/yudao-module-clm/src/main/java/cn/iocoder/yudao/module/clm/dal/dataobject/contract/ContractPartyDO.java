package cn.iocoder.yudao.module.clm.dal.dataobject.contract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 合同签约方 DO
 */
@TableName("clm_contract_party")
@KeySequence("clm_contract_party_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContractPartyDO extends TenantBaseDO {

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
     * 签约方编号
     */
    private Long partyId;
    /**
     * 角色：OUR_SIDE 我方 / COUNTERPARTY 相对方 / OTHER 其他
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.contract.ClmContractPartyRoleEnum}
     */
    private String roleCode;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 签约方快照 JSON（name、unifiedCreditCode、partyType）
     */
    private String partySnapshot;

}
