package cn.iocoder.yudao.module.clm.template;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentRoleEnum;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import cn.iocoder.yudao.module.clm.service.contract.ContractParticipantService;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.service.document.DocumentService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;

@Service
public class ContractDraftService {
    @Resource private TemplateService templateService;
    @Resource private ContractTypeService contractTypeService;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractParticipantService participantService;
    @Resource private DocumentService documentService;
    @Resource private ClmDocumentStorage documentStorage;
    @Resource private ContractRevisionService revisionService;

    @Transactional(rollbackFor = Exception.class)
    public Long createFromTemplate(CreateFromTemplateReqVO reqVO) {
        TemplateVersionDO version = templateService.getRequiredPublishedVersion(reqVO.getTemplateVersionId());
        TemplateDO template = templateService.getRequiredTemplate(version.getTemplateId());
        ContractDO contract = createContract(template.getContractTypeId(), reqVO.getName(), reqVO.getOwnerUserId(), "TEMPLATE");
        byte[] bytes = documentStorage.load(version.getFileKey());
        documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.MAIN.getCode(),
                "由独立范本 v" + version.getVersionNo() + " 创建", version.getFileName(), version.getMimeType(), bytes);
        revisionService.createInitialRevision(contract.getId(), version.getId(), "TEMPLATE_CREATE");
        return contract.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createFromUpload(String name, Long contractTypeId, Long ownerUserId,
                                 String fileName, String contentType, byte[] bytes) {
        ContractDO contract = createContract(contractTypeId, name, ownerUserId, "UPLOAD");
        documentService.uploadDocument(contract.getId(), ClmDocumentRoleEnum.MAIN.getCode(), "上传起草",
                fileName, contentType, bytes);
        revisionService.createInitialRevision(contract.getId(), null, "UPLOAD_CREATE");
        return contract.getId();
    }

    private ContractDO createContract(Long typeId, String title, Long requestedOwnerUserId, String sourceMode) {
        ContractTypeDO type = contractTypeService.getRequiredContractType(typeId);
        ContractTypeVersionDO typeVersion = contractTypeService.getRequiredContractTypeVersion(type.getCurrentVersionId());
        Long ownerUserId = ObjUtil.defaultIfNull(requestedOwnerUserId, SecurityFrameworkUtils.getLoginUserId());
        ContractDO contract = new ContractDO().setContractNo(null).setTitle(title).setTypeId(typeId)
                .setTypeVersionId(typeVersion.getId()).setOwnerUserId(ownerUserId)
                .setOwnerDeptId(SecurityFrameworkUtils.getLoginUserDeptId()).setCurrency("CNY")
                .setCustomData(new LinkedHashMap<>()).setLifecycleStatus(ClmLifecycleStatusEnum.DRAFT.getStatus())
                .setApprovalStatus(ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus()).setSourceMode(sourceMode)
                .setStageCode("DRAFT").setNoCommitmentConfirmed(Boolean.FALSE);
        contractMapper.insert(contract);
        participantService.createOwnerParticipant(contract.getId(), ownerUserId);
        return contract;
    }
}
