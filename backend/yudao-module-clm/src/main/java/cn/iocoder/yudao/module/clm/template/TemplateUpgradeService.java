package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionMapper;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.CONTRACT_REVISION_CONFLICT;
import static cn.iocoder.yudao.module.clm.template.TemplateGovernanceErrors.*;

@Service
public class TemplateUpgradeService {
    @Resource private ContractMapper contractMapper;
    @Resource private ContractAccessService contractAccessService;
    @Resource private ContractRevisionMapper revisionMapper;
    @Resource private ContractRevisionService revisionService;
    @Resource private TemplateMapper templateMapper;
    @Resource private TemplateVersionMapper versionMapper;
    @Resource private DocumentService documentService;
    @Resource private DocumentMapper documentMapper;
    @Resource private ClmDocumentStorage documentStorage;
    @Resource private ClmAuditService auditService;

    public TemplateUpgradePreviewRespVO preview(Long contractId) {
        ContractDO contract = contractMapper.selectById(contractId);
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        ContractRevisionDO sourceRevision = resolveSourceRevision(contract);
        TemplateVersionDO oldVersion = versionMapper.selectById(sourceRevision.getTemplateVersionId());
        if (oldVersion == null) throw exception(UPGRADE_SOURCE_NOT_EXISTS);
        TemplateDO template = templateMapper.selectById(oldVersion.getTemplateId());
        if (template == null) throw exception(UPGRADE_SOURCE_NOT_EXISTS);
        TemplateVersionDO current = template.getCurrentVersionId() == null ? null
                : versionMapper.selectById(template.getCurrentVersionId());
        TemplateUpgradePreviewRespVO resp = new TemplateUpgradePreviewRespVO();
        resp.setContractId(contractId);
        resp.setOldTemplateVersionId(oldVersion.getId());
        resp.setOldTemplateVersionNo(oldVersion.getVersionNo());
        resp.setOldChecksumSha256(oldVersion.getChecksumSha256());
        boolean available = "TEMPLATE".equals(contract.getSourceMode()) && Integer.valueOf(1).equals(template.getStatus())
                && current != null && "PUBLISHED".equals(current.getStatus())
                && current.getVersionNo() > oldVersion.getVersionNo();
        resp.setUpgradeAvailable(available);
        if (available) {
            resp.setNewTemplateVersionId(current.getId());
            resp.setNewTemplateVersionNo(current.getVersionNo());
            resp.setNewChecksumSha256(current.getChecksumSha256());
        }
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    public TemplateUpgradeRespVO upgrade(TemplateUpgradeReqVO reqVO) {
        ContractDO contract = contractMapper.selectByIdForUpdate(reqVO.getContractId());
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        if (!"TEMPLATE".equals(contract.getSourceMode())) throw exception(UPGRADE_NOT_TEMPLATE_DRAFT);
        contractAccessService.assertCanEdit(contract, SecurityFrameworkUtils.getLoginUserId());
        if (!Objects.equals(contract.getCurrentRevisionId(), reqVO.getBaseRevisionId())) {
            throw exception(CONTRACT_REVISION_CONFLICT, contract.getCurrentRevisionId());
        }
        ContractRevisionDO sourceRevision = resolveSourceRevision(contract);
        TemplateVersionDO oldVersion = versionMapper.selectById(sourceRevision.getTemplateVersionId());
        TemplateVersionDO target = versionMapper.selectById(reqVO.getTargetTemplateVersionId());
        if (oldVersion == null || target == null || !"PUBLISHED".equals(target.getStatus())
                || !Objects.equals(oldVersion.getTemplateId(), target.getTemplateId())
                || target.getVersionNo() <= oldVersion.getVersionNo()) {
            throw exception(UPGRADE_TARGET_INVALID);
        }
        TemplateDO template = templateMapper.selectById(target.getTemplateId());
        if (template == null || !Integer.valueOf(1).equals(template.getStatus())
                || !Objects.equals(template.getCurrentVersionId(), target.getId())) {
            throw exception(UPGRADE_TARGET_INVALID);
        }
        DocumentVersionDO parent = documentService.getRequiredDocumentVersion(contract.getCurrentDocumentVersionId());
        if (!Objects.equals(parent.getContractId(), contract.getId())) throw exception(UPGRADE_TARGET_INVALID);
        byte[] content = documentStorage.load(target.getFileKey());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("templateUpgrade", true);
        audit.put("fromTemplateVersionId", oldVersion.getId());
        audit.put("targetTemplateVersionId", target.getId());
        Long documentVersionId = documentService.createVersionFromBytes(contract.getId(), parent.getDocumentId(), parent.getId(),
                target.getFileName(), target.getMimeType(), content, ClmDocumentSourceTypeEnum.UPLOAD,
                "升级至范本 v" + target.getVersionNo(), SecurityFrameworkUtils.getLoginUserId(), audit, false);
        documentMapper.updateById(new DocumentDO().setId(parent.getDocumentId()).setCurrentVersionId(documentVersionId));
        contractMapper.updateById(new ContractDO().setId(contract.getId()).setCurrentDocumentVersionId(documentVersionId));
        Long revisionId = revisionService.createSnapshot(contract.getId(), reqVO.getBaseRevisionId(),
                "TEMPLATE_UPGRADE", "升级至范本 v" + target.getVersionNo());
        // createSnapshot 与本次升级在同一事务中；在提交前补齐来源范本，发布后不再修改该修订。
        revisionMapper.updateById(new ContractRevisionDO().setId(revisionId).setTemplateVersionId(target.getId()));
        auditService.record(ClmAuditAggregateTypeEnum.CONTRACT, contract.getId(), contract.getId(),
                ClmAuditActionEnum.TEMPLATE_UPGRADE, Map.of(
                        "fromTemplateVersionId", oldVersion.getId(), "targetTemplateVersionId", target.getId(),
                        "baseRevisionId", reqVO.getBaseRevisionId(), "resultRevisionId", revisionId,
                        "documentVersionId", documentVersionId));
        return new TemplateUpgradeRespVO(contract.getId(), revisionId, documentVersionId, target.getId());
    }

    private ContractRevisionDO resolveSourceRevision(ContractDO contract) {
        ContractRevisionDO current = contract.getCurrentRevisionId() == null ? null
                : revisionMapper.selectById(contract.getCurrentRevisionId());
        if (current != null && current.getTemplateVersionId() != null) return current;
        return revisionMapper.selectListByContractId(contract.getId()).stream()
                .filter(revision -> revision.getTemplateVersionId() != null).findFirst()
                .orElseThrow(() -> exception(UPGRADE_SOURCE_NOT_EXISTS));
    }
}
