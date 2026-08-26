package cn.iocoder.yudao.module.clm.service.document;

import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentVersionRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * CLM 合同文档 Service 接口
 */
public interface DocumentService {

    /**
     * 上传文档，生成新版本（版本不可覆盖）
     *
     * @param contractId  合同编号
     * @param roleCode    文档角色，空则 MAIN
     * @param remark      备注
     * @param fileName    原始文件名
     * @param contentType MIME 类型，可空
     * @param content     文件内容
     * @return 版本编号
     */
    Long uploadDocument(Long contractId, String roleCode, String remark,
                        String fileName, String contentType, byte[] content);

    /**
     * 由受信任的系统回调（如 ONLYOFFICE 保存回调）直接创建新版本
     *
     * 不做编辑 ACL 校验（调用方已通过令牌鉴权），不做状态锁定校验（回调是对已开启会话的收尾，不能丢数据）；
     * 会更新 document.currentVersionId（MAIN 时同时更新 contract.currentDocumentVersionId），
     * 并以 actorUserId 作为 creator 写入 ONLINE_EDIT_SAVE（sourceType=ONLINE_EDIT）或 DOCUMENT_UPLOAD 审计。
     *
     * @param contractId       合同编号
     * @param documentId       文档编号
     * @param parentVersionId  父版本编号，可空
     * @param fileName         文件名
     * @param mimeType         MIME 类型，可空
     * @param content          文件内容
     * @param sourceType       来源 {@link cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum}
     * @param remark           备注
     * @param actorUserId      操作人用户编号（写入 creator / 审计 actorUserId），可空
     * @param extraAuditDetail 追加到审计明细的字段，可空
     * @return 版本编号
     */
    Long createVersionFromBytes(Long contractId, Long documentId, Long parentVersionId,
                                String fileName, String mimeType, byte[] content,
                                ClmDocumentSourceTypeEnum sourceType, String remark, Long actorUserId,
                                Map<String, Object> extraAuditDetail);

    /**
     * 获得合同的文档列表（含版本列表，按版本号降序）；要求合同可见
     */
    List<DocumentRespVO> getDocumentList(Long contractId);

    DocumentVersionDO getDocumentVersion(Long id);

    /**
     * 获得文档版本；不存在抛 DOCUMENT_VERSION_NOT_EXISTS
     */
    DocumentVersionDO getRequiredDocumentVersion(Long id);

    /**
     * 获得文档版本详情；要求合同可见
     */
    DocumentVersionRespVO getDocumentVersionDetail(Long id);

    /**
     * 下载文档版本：校验可下载、读取内容、记录 DOCUMENT_DOWNLOAD 审计
     */
    DocumentDownloadResult downloadDocumentVersion(Long id);

    /**
     * 冻结版本（幂等）
     */
    void freezeDocumentVersion(Long id);

    /**
     * 版本 DO 转 VO（补充 creatorName）
     */
    DocumentVersionRespVO buildDocumentVersionRespVO(DocumentVersionDO version);

}
