package cn.iocoder.yudao.module.clm.dal.dataobject.document;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 文档版本 DO（不可覆盖；审批绑定准确版本与校验和）
 */
@TableName("clm_document_version")
@KeySequence("clm_document_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DocumentVersionDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 文档编号
     */
    private Long documentId;
    /**
     * 合同编号（冗余，便于权限校验）
     */
    private Long contractId;
    /**
     * 版本号，从 1 开始递增
     */
    private Integer versionNo;
    /**
     * 父版本编号
     */
    private Long parentVersionId;
    /**
     * 存储键（ClmDocumentStorage 返回，默认为 clm_document_blob.id）
     */
    private String fileKey;
    /**
     * 原始文件名
     */
    private String fileName;
    /**
     * MIME 类型
     */
    private String mimeType;
    /**
     * 文件大小（字节）
     */
    private Long fileSize;
    /**
     * SHA-256 校验和（hex）
     */
    private String checksumSha256;
    /**
     * 来源：UPLOAD 上传 / ONLINE_EDIT 在线编辑 / MANUAL_FINAL 线下定稿回传
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum}
     */
    private String sourceType;
    /**
     * 是否冻结（提交审批即冻结，永不解冻）
     */
    private Boolean frozen;
    /**
     * 备注
     */
    private String remark;

}
