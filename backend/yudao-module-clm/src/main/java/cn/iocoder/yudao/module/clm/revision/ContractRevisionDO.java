package cn.iocoder.yudao.module.clm.revision;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 一次不可变的合同业务快照。 */
@TableName("clm_contract_revision")
@KeySequence("clm_contract_revision_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContractRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long contractId;
    private Integer revisionNo;
    private Long baseRevisionId;
    private Long contractTypeVersionId;
    private Long templateVersionId;
    private Long mainDocumentVersionId;
    private String documentVersionIdsJson;
    private String fieldSnapshotJson;
    private String partySnapshotJson;
    private String commitmentSnapshotJson;
    private Boolean noCommitment;
    private String changeSource;
    private String changeReason;
}
