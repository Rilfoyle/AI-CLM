package cn.iocoder.yudao.module.clm.document;

/**
 * CLM 文档内容私有存储
 *
 * 不暴露任何公开读取路由，只能通过 CLM 自身的授权接口读取
 */
public interface ClmDocumentStorage {

    /**
     * 存储文档内容
     *
     * @param content 内容
     * @param sha256  内容的 SHA-256（hex）
     * @return 存储键 fileKey
     */
    String store(byte[] content, String sha256);

    /**
     * 读取文档内容
     *
     * @param fileKey 存储键
     * @return 内容；不存在时抛出 DOCUMENT_BLOB_NOT_EXISTS
     */
    byte[] load(String fileKey);

}
