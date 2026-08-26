package cn.iocoder.yudao.module.clm.dal.dataobject.document;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 文档内容 DO（CLM 私有存储，只能经 CLM 授权接口读取）
 */
@TableName("clm_document_blob")
@KeySequence("clm_document_blob_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "content")
public class DocumentBlobDO extends TenantBaseDO {

    /**
     * 编号（即 file_key）
     */
    @TableId
    private Long id;
    /**
     * SHA-256
     */
    private String sha256;
    /**
     * 大小（字节）
     */
    private Long size;
    /**
     * 内容
     */
    private byte[] content;

}
