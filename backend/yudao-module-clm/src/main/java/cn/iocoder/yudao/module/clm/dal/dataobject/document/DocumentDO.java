package cn.iocoder.yudao.module.clm.dal.dataobject.document;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 合同文档 DO
 */
@TableName("clm_document")
@KeySequence("clm_document_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DocumentDO extends TenantBaseDO {

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
     * 文档角色：MAIN 正文 / ATTACHMENT 附件
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.document.ClmDocumentRoleEnum}
     */
    private String roleCode;
    /**
     * 文档名称
     */
    private String name;
    /**
     * 当前版本编号
     */
    private Long currentVersionId;
    /**
     * 状态：0 正常
     */
    private Integer status;

}
