package cn.iocoder.yudao.module.clm.dal.dataobject.party;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 签约方 DO
 */
@TableName("clm_party")
@KeySequence("clm_party_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PartyDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 主体类型：1 企业 2 个人
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.party.ClmPartyTypeEnum}
     */
    private Integer partyType;
    /**
     * 名称
     */
    private String name;
    /**
     * 统一社会信用代码 / 证件号
     */
    private String unifiedCreditCode;
    /**
     * 是否我方主体
     */
    private Boolean internalFlag;
    /**
     * 联系人
     */
    private String contactName;
    /**
     * 联系电话
     */
    private String contactPhone;
    /**
     * 地址
     */
    private String address;
    /**
     * 状态：0 开启 1 关闭
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;

}
