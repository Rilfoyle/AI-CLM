package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_party_import_item")
@KeySequence("clm_party_import_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PartyImportItemDO extends TenantBaseDO {
    @TableId private Long id;
    private Long jobId;
    private Integer rowNo;
    private String sourceJson;
    private Long duplicatePartyId;
    private String status;
    private String errorMessage;
    private Long partyId;
}
