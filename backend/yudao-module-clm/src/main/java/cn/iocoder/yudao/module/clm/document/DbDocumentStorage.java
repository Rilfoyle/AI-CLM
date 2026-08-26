package cn.iocoder.yudao.module.clm.document;

import cn.hutool.core.util.NumberUtil;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentBlobDO;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentBlobMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.DOCUMENT_BLOB_NOT_EXISTS;

/**
 * 基于数据库 clm_document_blob 的文档内容存储（默认实现）
 *
 * fileKey = clm_document_blob.id 的字符串
 */
@Component
public class DbDocumentStorage implements ClmDocumentStorage {

    @Resource
    private DocumentBlobMapper documentBlobMapper;

    @Override
    public String store(byte[] content, String sha256) {
        DocumentBlobDO blob = new DocumentBlobDO()
                .setSha256(sha256)
                .setSize((long) content.length)
                .setContent(content);
        documentBlobMapper.insert(blob);
        return String.valueOf(blob.getId());
    }

    @Override
    public byte[] load(String fileKey) {
        if (!NumberUtil.isLong(fileKey)) {
            throw exception(DOCUMENT_BLOB_NOT_EXISTS);
        }
        DocumentBlobDO blob = documentBlobMapper.selectById(Long.parseLong(fileKey));
        if (blob == null || blob.getContent() == null) {
            throw exception(DOCUMENT_BLOB_NOT_EXISTS);
        }
        return blob.getContent();
    }

}
