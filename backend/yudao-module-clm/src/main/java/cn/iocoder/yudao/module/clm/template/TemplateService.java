package cn.iocoder.yudao.module.clm.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.document.ClmDocumentStorage;
import cn.iocoder.yudao.module.clm.service.contracttype.ContractTypeService;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.clm.template.TemplateGovernanceErrors.*;

@Service
public class TemplateService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf");

    @Resource private TemplateMapper templateMapper;
    @Resource private TemplateVersionMapper versionMapper;
    @Resource private ContractTypeService contractTypeService;
    @Resource private ClmDocumentStorage documentStorage;
    @Resource private ClmAuditService auditService;

    public PageResult<PublishedTemplateRespVO> getPublishedPage(TemplatePageReqVO reqVO) {
        PageResult<TemplateDO> page = templateMapper.selectPublishedPage(reqVO);
        List<PublishedTemplateRespVO> result = new ArrayList<>();
        for (TemplateDO template : page.getList()) {
            PublishedTemplateRespVO vo = BeanUtils.toBean(template, PublishedTemplateRespVO.class);
            TemplateVersionDO version = versionMapper.selectById(template.getCurrentVersionId());
            if (version != null && "PUBLISHED".equals(version.getStatus())) {
                vo.setCurrentVersionNo(version.getVersionNo());
                vo.setFileName(version.getFileName());
                result.add(vo);
            }
        }
        return new PageResult<>(result, page.getTotal());
    }

    public TemplateVersionDO getRequiredPublishedVersion(Long id) {
        TemplateVersionDO version = versionMapper.selectById(id);
        if (version == null || !"PUBLISHED".equals(version.getStatus())) throw exception(TEMPLATE_VERSION_NOT_PUBLISHED);
        TemplateDO template = templateMapper.selectById(version.getTemplateId());
        if (template == null || !Integer.valueOf(1).equals(template.getStatus())) throw exception(TEMPLATE_NOT_EXISTS);
        return version;
    }

    public TemplateDO getRequiredTemplate(Long id) {
        TemplateDO template = templateMapper.selectById(id);
        if (template == null) throw exception(TEMPLATE_NOT_EXISTS);
        return template;
    }

    public PageResult<TemplateGovernanceRespVO> getGovernancePage(TemplateGovernancePageReqVO reqVO) {
        PageResult<TemplateDO> page = templateMapper.selectGovernancePage(reqVO);
        List<TemplateGovernanceRespVO> rows = new ArrayList<>(page.getList().size());
        for (TemplateDO template : page.getList()) {
            rows.add(buildGovernanceResp(template, false));
        }
        return new PageResult<>(rows, page.getTotal());
    }

    public TemplateGovernanceRespVO getGovernanceDetail(Long id) {
        return buildGovernanceResp(getRequiredTemplate(id), true);
    }

    /**
     * 保存唯一草稿。未显式传 versionId 时优先复用现有草稿；没有草稿时从当前发布版本派生。
     * 发布版本的文件记录从不原地更新。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveDraft(TemplateDraftSaveReqVO reqVO) throws IOException {
        String code = StrUtil.trim(reqVO.getCode());
        TemplateDO template;
        if (reqVO.getTemplateId() == null) {
            TemplateDO duplicate = templateMapper.selectByCode(code);
            if (duplicate != null) throw exception(CODE_DUPLICATE, code);
            contractTypeService.getRequiredContractType(reqVO.getContractTypeId());
            template = new TemplateDO().setCode(code).setName(StrUtil.trim(reqVO.getName()))
                    .setContractTypeId(reqVO.getContractTypeId()).setStatus(1)
                    .setDescription(StrUtil.nullToEmpty(reqVO.getDescription()));
            templateMapper.insert(template);
        } else {
            template = templateMapper.selectByIdForUpdate(reqVO.getTemplateId());
            if (template == null) throw exception(TEMPLATE_NOT_EXISTS);
            if (!Objects.equals(template.getCode(), code)) throw exception(CODE_IMMUTABLE);
            if (!Objects.equals(template.getContractTypeId(), reqVO.getContractTypeId())) {
                if (template.getCurrentVersionId() != null) throw exception(SCOPE_IMMUTABLE);
                contractTypeService.getRequiredContractType(reqVO.getContractTypeId());
            }
            templateMapper.updateById(new TemplateDO().setId(template.getId())
                    .setName(StrUtil.trim(reqVO.getName()))
                    .setContractTypeId(reqVO.getContractTypeId())
                    .setDescription(StrUtil.nullToEmpty(reqVO.getDescription())));
            template.setName(StrUtil.trim(reqVO.getName()))
                    .setContractTypeId(reqVO.getContractTypeId())
                    .setDescription(StrUtil.nullToEmpty(reqVO.getDescription()));
        }

        TemplateVersionDO version;
        if (reqVO.getVersionId() != null) {
            version = versionMapper.selectByIdForUpdate(reqVO.getVersionId());
            validateDraftBelongsToTemplate(version, template.getId());
        } else {
            version = versionMapper.selectDraftByTemplateId(template.getId());
        }
        if (version == null) {
            version = new TemplateVersionDO().setTemplateId(template.getId())
                    .setVersionNo(versionMapper.selectMaxVersionNo(template.getId()) + 1)
                    .setStatus("DRAFT");
            if (reqVO.getFile() == null || reqVO.getFile().isEmpty()) {
                copyCurrentFile(template, version);
            }
            applyFileIfPresent(version, reqVO.getFile());
            version.setRemark(StrUtil.nullToEmpty(reqVO.getRemark()));
            versionMapper.insert(version);
        } else {
            validateDraftBelongsToTemplate(version, template.getId());
            applyFileIfPresent(version, reqVO.getFile());
            version.setRemark(StrUtil.nullToEmpty(reqVO.getRemark()));
            versionMapper.updateById(version);
        }
        auditService.record(ClmAuditAggregateTypeEnum.TEMPLATE, template.getId(), null,
                ClmAuditActionEnum.TEMPLATE_DRAFT_SAVE,
                Map.of("templateId", template.getId(), "versionId", version.getId(),
                        "versionNo", version.getVersionNo()));
        return version.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long versionId) {
        TemplateVersionDO version = versionMapper.selectByIdForUpdate(versionId);
        if (version == null) throw exception(VERSION_NOT_EXISTS);
        TemplateDO template = templateMapper.selectByIdForUpdate(version.getTemplateId());
        if (template == null) throw exception(TEMPLATE_NOT_EXISTS);
        if ("PUBLISHED".equals(version.getStatus()) && Objects.equals(template.getCurrentVersionId(), versionId)
                && Integer.valueOf(1).equals(template.getStatus())) {
            return;
        }
        if (!"DRAFT".equals(version.getStatus())) throw exception(VERSION_NOT_DRAFT);
        if (StrUtil.isBlank(version.getFileKey())) throw exception(FILE_REQUIRED);
        for (TemplateVersionDO old : versionMapper.selectListByTemplateId(template.getId())) {
            if ("PUBLISHED".equals(old.getStatus()) && !Objects.equals(old.getId(), versionId)) {
                versionMapper.updateById(new TemplateVersionDO().setId(old.getId()).setStatus("INACTIVE"));
            }
        }
        versionMapper.updateById(new TemplateVersionDO().setId(versionId).setStatus("PUBLISHED")
                .setPublishedTime(java.time.LocalDateTime.now()));
        templateMapper.updateById(new TemplateDO().setId(template.getId()).setCurrentVersionId(versionId).setStatus(1));
        auditService.record(ClmAuditAggregateTypeEnum.TEMPLATE, template.getId(), null,
                ClmAuditActionEnum.TEMPLATE_PUBLISH,
                Map.of("versionId", versionId, "versionNo", version.getVersionNo()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long templateId) {
        TemplateDO template = templateMapper.selectByIdForUpdate(templateId);
        if (template == null) throw exception(TEMPLATE_NOT_EXISTS);
        for (TemplateVersionDO version : versionMapper.selectListByTemplateId(templateId)) {
            if ("PUBLISHED".equals(version.getStatus())) {
                versionMapper.updateById(new TemplateVersionDO().setId(version.getId()).setStatus("INACTIVE"));
            }
        }
        templateMapper.updateById(new TemplateDO().setId(templateId).setStatus(0));
        auditService.record(ClmAuditAggregateTypeEnum.TEMPLATE, templateId, null,
                ClmAuditActionEnum.TEMPLATE_DISABLE, Map.of("templateId", templateId));
    }

    public TemplateFileResult getFile(Long versionId) {
        TemplateVersionDO version = versionMapper.selectById(versionId);
        if (version == null) throw exception(VERSION_NOT_EXISTS);
        return new TemplateFileResult(version.getFileName(), version.getMimeType(), documentStorage.load(version.getFileKey()));
    }

    private TemplateGovernanceRespVO buildGovernanceResp(TemplateDO template, boolean includeVersions) {
        TemplateGovernanceRespVO vo = BeanUtils.toBean(template, TemplateGovernanceRespVO.class);
        List<TemplateVersionDO> versions = versionMapper.selectListByTemplateId(template.getId());
        List<TemplateVersionRespVO> versionVOs = BeanUtils.toBean(versions, TemplateVersionRespVO.class);
        TemplateVersionDO draft = versions.stream().filter(v -> "DRAFT".equals(v.getStatus())).findFirst().orElse(null);
        TemplateVersionDO current = template.getCurrentVersionId() == null ? null : versions.stream()
                .filter(v -> Objects.equals(v.getId(), template.getCurrentVersionId())).findFirst().orElse(null);
        vo.setDraftVersionId(draft == null ? null : draft.getId());
        vo.setCurrentVersionNo(current == null ? null : current.getVersionNo());
        vo.setCurrentFileName(current == null ? null : current.getFileName());
        if (includeVersions) vo.setVersions(versionVOs);
        return vo;
    }

    private void validateDraftBelongsToTemplate(TemplateVersionDO version, Long templateId) {
        if (version == null) throw exception(VERSION_NOT_EXISTS);
        if (!Objects.equals(version.getTemplateId(), templateId)) throw exception(VERSION_MISMATCH);
        if (!"DRAFT".equals(version.getStatus())) throw exception(VERSION_NOT_DRAFT);
    }

    private void copyCurrentFile(TemplateDO template, TemplateVersionDO draft) {
        if (template.getCurrentVersionId() == null) throw exception(FILE_REQUIRED);
        TemplateVersionDO current = versionMapper.selectById(template.getCurrentVersionId());
        if (current == null || StrUtil.isBlank(current.getFileKey())) throw exception(FILE_REQUIRED);
        draft.setFileKey(current.getFileKey()).setFileName(current.getFileName()).setMimeType(current.getMimeType())
                .setFileSize(current.getFileSize()).setChecksumSha256(current.getChecksumSha256());
    }

    private void applyFileIfPresent(TemplateVersionDO version, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;
        byte[] content = file.getBytes();
        if (ArrayUtil.isEmpty(content)) throw exception(DOCUMENT_FILE_EMPTY);
        if (content.length > MAX_FILE_SIZE) throw exception(DOCUMENT_FILE_TOO_LARGE, 20);
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "template.docx");
        String extension = StrUtil.emptyToDefault(FileUtil.extName(fileName), "").toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) throw exception(DOCUMENT_FILE_TYPE_NOT_ALLOWED, extension);
        String sha256 = DigestUtil.sha256Hex(content);
        version.setFileKey(documentStorage.store(content, sha256)).setFileName(fileName)
                .setMimeType(resolveMimeType(extension, file.getContentType())).setFileSize((long) content.length)
                .setChecksumSha256(sha256);
    }

    private String resolveMimeType(String extension, String supplied) {
        if ("pdf".equals(extension)) return "application/pdf";
        if ("doc".equals(extension)) return "application/msword";
        if ("docx".equals(extension)) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return StrUtil.blankToDefault(supplied, "application/octet-stream");
    }
}
