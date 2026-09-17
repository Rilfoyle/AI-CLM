package cn.iocoder.yudao.module.clm.service.document;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentVersionRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentVersionMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentRoleEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMultiMap;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

/**
 * CLM 合同文档 Service 实现类
 */
@Service
@Validated
public class DocumentServiceImpl implements DocumentService {

    /**
     * 允许上传的扩展名白名单
     */
    public static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "docx", "doc", "pdf", "xlsx", "xls", "pptx", "ppt", "txt", "zip", "png", "jpg", "jpeg"));
    /**
     * 最大文件大小（MB）
     */
    public static final int MAX_FILE_SIZE_MB = 50;
    public static final long MAX_FILE_SIZE = MAX_FILE_SIZE_MB * 1024L * 1024L;

    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    @Resource
    private DocumentMapper documentMapper;
    @Resource
    private DocumentVersionMapper documentVersionMapper;
    @Resource
    private ContractMapper contractMapper;

    @Resource
    private ClmDocumentStorage documentStorage;
    @Resource
    private ContractAccessService contractAccessService;
    @Resource
    private ClmAuditService clmAuditService;

    @Autowired(required = false)
    private ContractRevisionService contractRevisionService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadDocument(Long contractId, String roleCode, String remark,
                               String fileName, String contentType, byte[] content) {
        // 1. 合同与权限校验
        ContractDO contract = getRequiredContract(contractId);
        if (ClmApprovalStatusEnum.RUNNING.getStatus().equals(contract.getApprovalStatus())) {
            throw exception(CONTRACT_LOCKED_BY_APPROVAL);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        contractAccessService.assertCanEdit(contract, userId);
        // 2. 文件校验
        if (ArrayUtil.isEmpty(content)) {
            throw exception(DOCUMENT_FILE_EMPTY);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw exception(DOCUMENT_FILE_TOO_LARGE, MAX_FILE_SIZE_MB);
        }
        fileName = StrUtil.blankToDefault(fileName, "unknown");
        String extension = StrUtil.emptyToDefault(FileUtil.extName(fileName), "").toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw exception(DOCUMENT_FILE_TYPE_NOT_ALLOWED, extension);
        }
        ClmDocumentRoleEnum role = ClmDocumentRoleEnum.of(StrUtil.blankToDefault(roleCode,
                ClmDocumentRoleEnum.MAIN.getCode()));
        if (role == null) {
            role = ClmDocumentRoleEnum.MAIN;
        }
        // 3. 存储内容
        String sha256 = DigestUtil.sha256Hex(content);
        String fileKey = documentStorage.store(content, sha256);
        // 4. 取/建 document
        DocumentDO document = null;
        if (role == ClmDocumentRoleEnum.MAIN) {
            document = documentMapper.selectByContractIdAndRoleCode(contractId, role.getCode());
        }
        if (document == null) {
            document = new DocumentDO()
                    .setContractId(contractId)
                    .setRoleCode(role.getCode())
                    .setName(role == ClmDocumentRoleEnum.MAIN ? "合同正文" : fileName)
                    .setStatus(0);
            documentMapper.insert(document);
        }
        // 5. 新版本：versionNo = max + 1，parentVersionId = document.currentVersionId
        DocumentVersionDO latest = documentVersionMapper.selectLatestByDocumentId(document.getId());
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        DocumentVersionDO version = new DocumentVersionDO()
                .setDocumentId(document.getId())
                .setContractId(contractId)
                .setVersionNo(versionNo)
                .setParentVersionId(document.getCurrentVersionId())
                .setFileKey(fileKey)
                .setFileName(fileName)
                .setMimeType(StrUtil.blankToDefault(contentType, DEFAULT_MIME_TYPE))
                .setFileSize((long) content.length)
                .setChecksumSha256(sha256)
                .setSourceType(ClmDocumentSourceTypeEnum.UPLOAD.getCode())
                .setFrozen(false)
                .setRemark(StrUtil.nullToEmpty(remark));
        documentVersionMapper.insert(version);
        // 6. 更新 document.currentVersionId；MAIN 时更新 contract.currentDocumentVersionId
        documentMapper.updateById(new DocumentDO().setId(document.getId()).setCurrentVersionId(version.getId()));
        if (role == ClmDocumentRoleEnum.MAIN) {
            contractMapper.updateById(new ContractDO().setId(contractId).setCurrentDocumentVersionId(version.getId()));
        }
        // 7. 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("versionNo", versionNo);
        detail.put("fileName", fileName);
        detail.put("sha256", sha256);
        detail.put("size", content.length);
        detail.put("roleCode", role.getCode());
        clmAuditService.record(ClmAuditAggregateTypeEnum.DOCUMENT, document.getId(), contractId,
                ClmAuditActionEnum.DOCUMENT_UPLOAD, detail);
        if (role == ClmDocumentRoleEnum.MAIN && contractRevisionService != null) {
            ContractDO latestContract = contractMapper.selectById(contractId);
            // 新建草稿由起草服务在文件落库后创建带来源信息的初始修订；已有草稿则追加修订。
            if (latestContract.getCurrentRevisionId() != null) {
                contractRevisionService.createSnapshot(contractId, latestContract.getCurrentRevisionId(),
                        "DOCUMENT_UPLOAD", StrUtil.nullToEmpty(remark));
            }
        }
        return version.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersionFromBytes(Long contractId, Long documentId, Long parentVersionId,
                                       String fileName, String mimeType, byte[] content,
                                       ClmDocumentSourceTypeEnum sourceType, String remark, Long actorUserId,
                                       Map<String, Object> extraAuditDetail) {
        return createVersionFromBytes(contractId, documentId, parentVersionId, fileName, mimeType, content,
                sourceType, remark, actorUserId, extraAuditDetail, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersionFromBytes(Long contractId, Long documentId, Long parentVersionId,
                                       String fileName, String mimeType, byte[] content,
                                       ClmDocumentSourceTypeEnum sourceType, String remark, Long actorUserId,
                                       Map<String, Object> extraAuditDetail, boolean advanceCurrent) {
        // 1. 存在性校验（不做 ACL / 状态锁定校验：调用方已通过令牌鉴权）
        getRequiredContract(contractId);
        DocumentDO document = documentMapper.selectById(documentId);
        if (document == null || !Objects.equals(document.getContractId(), contractId)) {
            throw exception(DOCUMENT_NOT_EXISTS);
        }
        if (ArrayUtil.isEmpty(content)) {
            throw exception(DOCUMENT_FILE_EMPTY);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw exception(DOCUMENT_FILE_TOO_LARGE, MAX_FILE_SIZE_MB);
        }
        fileName = StrUtil.blankToDefault(fileName, "unknown");
        if (sourceType == null) {
            sourceType = ClmDocumentSourceTypeEnum.UPLOAD;
        }
        // 2. 存储内容
        String sha256 = DigestUtil.sha256Hex(content);
        String fileKey = documentStorage.store(content, sha256);
        // 3. 新版本：versionNo = max + 1
        DocumentVersionDO latest = documentVersionMapper.selectLatestByDocumentId(document.getId());
        int versionNo = latest == null ? 1 : latest.getVersionNo() + 1;
        String actor = actorUserId == null ? null : String.valueOf(actorUserId);
        DocumentVersionDO version = new DocumentVersionDO()
                .setDocumentId(document.getId())
                .setContractId(contractId)
                .setVersionNo(versionNo)
                .setParentVersionId(parentVersionId)
                .setFileKey(fileKey)
                .setFileName(fileName)
                .setMimeType(StrUtil.blankToDefault(mimeType, DEFAULT_MIME_TYPE))
                .setFileSize((long) content.length)
                .setChecksumSha256(sha256)
                .setSourceType(sourceType.getCode())
                .setFrozen(false)
                .setRemark(StrUtil.nullToEmpty(remark));
        // 回调上下文无登录用户，显式写入 creator / updater（自动填充只在为空时生效）
        version.setCreator(actor);
        version.setUpdater(actor);
        documentVersionMapper.insert(version);
        // 4. 更新 document.currentVersionId；MAIN 时更新 contract.currentDocumentVersionId
        if (advanceCurrent) {
            DocumentDO documentUpdate = new DocumentDO().setId(document.getId()).setCurrentVersionId(version.getId());
            documentUpdate.setUpdater(actor);
            documentMapper.updateById(documentUpdate);
            if (ClmDocumentRoleEnum.MAIN.getCode().equals(document.getRoleCode())) {
                ContractDO contractUpdate = new ContractDO().setId(contractId).setCurrentDocumentVersionId(version.getId());
                contractUpdate.setUpdater(actor);
                contractMapper.updateById(contractUpdate);
            }
        }
        // 5. 审计（指定操作人）
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("versionNo", versionNo);
        detail.put("parentVersionId", parentVersionId);
        detail.put("fileName", fileName);
        detail.put("sha256", sha256);
        detail.put("size", content.length);
        detail.put("roleCode", document.getRoleCode());
        detail.put("sourceType", sourceType.getCode());
        detail.put("advanceCurrent", advanceCurrent);
        if (extraAuditDetail != null) {
            detail.putAll(extraAuditDetail);
        }
        ClmAuditActionEnum action = sourceType == ClmDocumentSourceTypeEnum.ONLINE_EDIT
                ? ClmAuditActionEnum.ONLINE_EDIT_SAVE : ClmAuditActionEnum.DOCUMENT_UPLOAD;
        clmAuditService.record(ClmAuditAggregateTypeEnum.DOCUMENT, document.getId(), contractId, action, detail,
                actorUserId, resolveActorName(actorUserId));
        if (advanceCurrent && ClmDocumentRoleEnum.MAIN.getCode().equals(document.getRoleCode())
                && contractRevisionService != null) {
            ContractDO latestContract = contractMapper.selectById(contractId);
            if (latestContract.getCurrentRevisionId() != null) {
                contractRevisionService.createSnapshot(contractId, latestContract.getCurrentRevisionId(),
                        sourceType.getCode(), StrUtil.nullToEmpty(remark), actorUserId);
            }
        }
        return version.getId();
    }

    @Override
    public List<DocumentRespVO> getDocumentList(Long contractId) {
        ContractDO contract = getRequiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        List<DocumentDO> documents = documentMapper.selectListByContractId(contractId);
        if (CollUtil.isEmpty(documents)) {
            return new ArrayList<>();
        }
        List<DocumentVersionDO> versions = documentVersionMapper.selectListByContractId(contractId);
        Map<Long, List<DocumentVersionDO>> versionMap = convertMultiMap(versions, DocumentVersionDO::getDocumentId);
        Map<Long, AdminUserRespDTO> userMap = getUserMap(versions);
        return convertList(documents, document -> {
            DocumentRespVO vo = BeanUtils.toBean(document, DocumentRespVO.class);
            List<DocumentVersionDO> list = versionMap.getOrDefault(document.getId(), Collections.emptyList());
            vo.setVersions(convertList(list, version -> buildDocumentVersionRespVO(version, userMap)));
            return vo;
        });
    }

    @Override
    public DocumentVersionDO getDocumentVersion(Long id) {
        return documentVersionMapper.selectById(id);
    }

    @Override
    public DocumentVersionDO getRequiredDocumentVersion(Long id) {
        DocumentVersionDO version = documentVersionMapper.selectById(id);
        if (version == null) {
            throw exception(DOCUMENT_VERSION_NOT_EXISTS);
        }
        return version;
    }

    @Override
    public DocumentVersionRespVO getDocumentVersionDetail(Long id) {
        DocumentVersionDO version = getRequiredDocumentVersion(id);
        ContractDO contract = getRequiredContract(version.getContractId());
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        return buildDocumentVersionRespVO(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDownloadResult downloadDocumentVersion(Long id) {
        DocumentVersionDO version = getRequiredDocumentVersion(id);
        ContractDO contract = getRequiredContract(version.getContractId());
        contractAccessService.assertCanDownload(contract, SecurityFrameworkUtils.getLoginUserId());
        byte[] content = documentStorage.load(version.getFileKey());
        // 审计
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("versionId", version.getId());
        detail.put("versionNo", version.getVersionNo());
        detail.put("fileName", version.getFileName());
        detail.put("sha256", version.getChecksumSha256());
        detail.put("size", version.getFileSize());
        clmAuditService.record(ClmAuditAggregateTypeEnum.DOCUMENT, version.getDocumentId(), version.getContractId(),
                ClmAuditActionEnum.DOCUMENT_DOWNLOAD, detail);
        return new DocumentDownloadResult(version, content);
    }

    @Override
    public void freezeDocumentVersion(Long id) {
        DocumentVersionDO version = getRequiredDocumentVersion(id);
        if (BooleanUtil.isTrue(version.getFrozen())) {
            return;
        }
        documentVersionMapper.updateById(new DocumentVersionDO().setId(id).setFrozen(true));
    }

    @Override
    public DocumentVersionRespVO buildDocumentVersionRespVO(DocumentVersionDO version) {
        if (version == null) {
            return null;
        }
        return buildDocumentVersionRespVO(version, getUserMap(Collections.singletonList(version)));
    }

    // ========== 私有方法 ==========

    private DocumentVersionRespVO buildDocumentVersionRespVO(DocumentVersionDO version,
                                                             Map<Long, AdminUserRespDTO> userMap) {
        DocumentVersionRespVO vo = BeanUtils.toBean(version, DocumentVersionRespVO.class);
        AdminUserRespDTO user = findUser(userMap, version.getCreator());
        vo.setCreatorName(user == null ? null : user.getNickname());
        return vo;
    }

    private Map<Long, AdminUserRespDTO> getUserMap(List<DocumentVersionDO> versions) {
        Set<Long> userIds = new HashSet<>();
        for (DocumentVersionDO version : versions) {
            if (NumberUtil.isLong(version.getCreator())) {
                userIds.add(Long.parseLong(version.getCreator()));
            }
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, AdminUserRespDTO> map = adminUserApi.getUserMap(userIds);
        return map == null ? Collections.emptyMap() : map;
    }

    private AdminUserRespDTO findUser(Map<Long, AdminUserRespDTO> userMap, String creator) {
        if (userMap == null || !NumberUtil.isLong(creator)) {
            return null;
        }
        return userMap.get(Long.parseLong(creator));
    }

    private String resolveActorName(Long actorUserId) {
        if (actorUserId == null) {
            return null;
        }
        try {
            AdminUserRespDTO user = adminUserApi.getUser(actorUserId);
            return user == null ? null : user.getNickname();
        } catch (Exception ex) {
            return null;
        }
    }

    private ContractDO getRequiredContract(Long contractId) {
        ContractDO contract = contractMapper.selectById(contractId);
        if (contract == null) {
            throw exception(CONTRACT_NOT_EXISTS);
        }
        return contract;
    }

}
