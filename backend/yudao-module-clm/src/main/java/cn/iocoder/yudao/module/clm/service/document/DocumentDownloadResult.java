package cn.iocoder.yudao.module.clm.service.document;

import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文档版本下载结果：版本元数据 + 内容
 */
@Data
@AllArgsConstructor
public class DocumentDownloadResult {

    /**
     * 版本
     */
    private DocumentVersionDO version;
    /**
     * 内容
     */
    private byte[] content;

}
