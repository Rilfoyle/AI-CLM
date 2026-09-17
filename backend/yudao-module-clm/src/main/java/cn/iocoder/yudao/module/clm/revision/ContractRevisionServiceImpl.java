package cn.iocoder.yudao.module.clm.revision;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.NumberUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.commitment.CommitmentDO;
import cn.iocoder.yudao.module.clm.commitment.CommitmentMapper;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.document.DocumentDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.document.DocumentMapper;
import cn.iocoder.yudao.module.clm.enums.document.ClmDocumentSourceTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;

import jakarta.annotation.Resource;
import java.util.*;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class ContractRevisionServiceImpl implements ContractRevisionService {

    @Resource private ContractRevisionMapper revisionMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractPartyMapper contractPartyMapper;
    @Resource private PartyMapper partyMapper;
    @Resource private DocumentMapper documentMapper;
    @Resource private CommitmentMapper commitmentMapper;
    @Resource private ContractAccessService contractAccessService;
    @Autowired(required = false) private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractRevisionSaveRespVO save(ContractRevisionSaveReqVO reqVO) {
        return saveInternal(reqVO, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContractRevisionSaveRespVO saveForApproval(ContractRevisionSaveReqVO reqVO) {
        return saveInternal(reqVO, true);
    }

    private ContractRevisionSaveRespVO saveInternal(ContractRevisionSaveReqVO reqVO, boolean approvalEdit) {
        ContractDO contract = getRequiredContractForUpdate(reqVO.getContractId());
        if (!approvalEdit) {
            contractAccessService.assertCanEdit(contract, SecurityFrameworkUtils.getLoginUserId());
        }
        assertCurrentBase(contract, reqVO.getBaseRevisionId());
        ContractRevisionDO baseRevision = getRequiredBaseRevision(contract, reqVO.getBaseRevisionId());
        Map<Long, PartyDO> parties = loadParties(reqVO.getOurPartyId(), reqVO.getCounterpartyIds());
        ContractDO update = new ContractDO().setId(contract.getId())
                .setTitle(reqVO.getName()).setAmount(reqVO.getAmount())
                .setCurrency(StrUtil.blankToDefault(reqVO.getCurrency(), "CNY"))
                .setEffectiveDate(reqVO.getStartDate()).setExpiryDate(reqVO.getEndDate())
                .setCustomData(reqVO.getCustomData() == null ? new LinkedHashMap<>() : reqVO.getCustomData())
                .setDescription(reqVO.getDescription());
        contractMapper.updateById(update);
        replaceParties(contract.getId(), reqVO.getOurPartyId(), reqVO.getCounterpartyIds(), parties);
        Long revisionId = createSnapshotLocked(contractMapper.selectById(contract.getId()), reqVO.getBaseRevisionId(),
                approvalEdit ? "APPROVAL_EDIT" : "MANUAL_EDIT", reqVO.getChangeReason(),
                baseRevision.getTemplateVersionId(), SecurityFrameworkUtils.getLoginUserId());
        ContractRevisionDO revision = revisionMapper.selectById(revisionId);
        return new ContractRevisionSaveRespVO(revisionId, revision.getRevisionNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInitialRevision(Long contractId, Long templateVersionId, String changeSource) {
        ContractDO contract = getRequiredContractForUpdate(contractId);
        if (contract.getCurrentRevisionId() != null) {
            return contract.getCurrentRevisionId();
        }
        return createSnapshotLocked(contract, null, changeSource, "初始修订", templateVersionId,
                SecurityFrameworkUtils.getLoginUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSnapshot(Long contractId, Long baseRevisionId, String changeSource, String changeReason) {
        return createSnapshot(contractId, baseRevisionId, changeSource, changeReason,
                SecurityFrameworkUtils.getLoginUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSnapshot(Long contractId, Long baseRevisionId, String changeSource, String changeReason,
                               Long actorUserId) {
        ContractDO contract = getRequiredContractForUpdate(contractId);
        assertCurrentBase(contract, baseRevisionId);
        ContractRevisionDO baseRevision = getRequiredBaseRevision(contract, baseRevisionId);
        Long templateVersionId = changesDocumentBody(changeSource) ? null : baseRevision.getTemplateVersionId();
        return createSnapshotLocked(contract, baseRevisionId, changeSource, changeReason, templateVersionId,
                actorUserId);
    }

    @Override
    public List<ContractRevisionRespVO> getList(Long contractId) {
        ContractDO contract = getRequiredContract(contractId);
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        List<ContractRevisionRespVO> result = new ArrayList<>();
        for (ContractRevisionDO revision : revisionMapper.selectListByContractId(contractId)) {
            result.add(toResp(revision));
        }
        return result;
    }

    @Override
    public ContractRevisionCompareRespVO compare(Long fromRevisionId, Long toRevisionId) {
        ContractRevisionDO from = getRequiredRevision(fromRevisionId);
        ContractRevisionDO to = getRequiredRevision(toRevisionId);
        if (!Objects.equals(from.getContractId(), to.getContractId())) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
        ContractDO contract = getRequiredContract(from.getContractId());
        contractAccessService.assertCanView(contract, SecurityFrameworkUtils.getLoginUserId());
        Map<String, Object> fromFields = parseMap(from.getFieldSnapshotJson());
        Map<String, Object> toFields = parseMap(to.getFieldSnapshotJson());
        Set<String> keys = new LinkedHashSet<>(fromFields.keySet());
        keys.addAll(toFields.keySet());
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            if (!Objects.equals(fromFields.get(key), toFields.get(key))) changed.add(key);
        }
        ContractRevisionCompareRespVO resp = new ContractRevisionCompareRespVO();
        resp.setFromRevision(toResp(from));
        resp.setToRevision(toResp(to));
        resp.setChangedFields(changed);
        resp.setPartiesChanged(!Objects.equals(from.getPartySnapshotJson(), to.getPartySnapshotJson()));
        resp.setCommitmentsChanged(!Objects.equals(from.getCommitmentSnapshotJson(), to.getCommitmentSnapshotJson())
                || !Objects.equals(from.getNoCommitment(), to.getNoCommitment()));
        resp.setDocumentChanged(!Objects.equals(from.getDocumentVersionIdsJson(), to.getDocumentVersionIdsJson()));
        List<RevisionChangeRespVO> fieldChanges = new ArrayList<>();
        for (String key : changed) {
            fieldChanges.add(new RevisionChangeRespVO(key, fieldLabel(key), fromFields.get(key), toFields.get(key)));
        }
        resp.setFieldChanges(fieldChanges);
        resp.setPartyChanges(resp.getPartiesChanged() ? Collections.singletonList(new RevisionChangeRespVO(
                "parties", "参与方", parseListMap(from.getPartySnapshotJson()), parseListMap(to.getPartySnapshotJson())))
                : Collections.emptyList());
        resp.setCommitmentChanges(resp.getCommitmentsChanged() ? Collections.singletonList(new RevisionChangeRespVO(
                "commitments", "重大承诺", parseListMap(from.getCommitmentSnapshotJson()), parseListMap(to.getCommitmentSnapshotJson())))
                : Collections.emptyList());
        resp.setDocumentChanges(resp.getDocumentChanged() ? Collections.singletonList(new RevisionChangeRespVO(
                "documents", "合同正文与附件", parseList(from.getDocumentVersionIdsJson(), Long.class),
                parseList(to.getDocumentVersionIdsJson(), Long.class))) : Collections.emptyList());
        int total = fieldChanges.size() + resp.getPartyChanges().size() + resp.getCommitmentChanges().size()
                + resp.getDocumentChanges().size();
        resp.setSummary(total == 0 ? "两个修订无差异" : "共发现 " + total + " 组差异");
        return resp;
    }

    @Override
    public ContractRevisionDO getRequiredRevision(Long id) {
        ContractRevisionDO revision = revisionMapper.selectById(id);
        if (revision == null) throw exception(CONTRACT_REVISION_NOT_EXISTS);
        return revision;
    }

    private Long createSnapshotLocked(ContractDO contract, Long baseRevisionId, String source,
                                      String reason, Long templateVersionId, Long actorUserId) {
        ContractRevisionDO latest = revisionMapper.selectLatest(contract.getId());
        int nextNo = latest == null ? 1 : latest.getRevisionNo() + 1;
        List<ContractPartyDO> parties = contractPartyMapper.selectListByContractId(contract.getId());
        List<CommitmentDO> commitments = commitmentMapper.selectListByContractId(contract.getId());
        List<Long> documentVersions = new ArrayList<>();
        for (DocumentDO document : documentMapper.selectListByContractId(contract.getId())) {
            if (document.getCurrentVersionId() != null) documentVersions.add(document.getCurrentVersionId());
        }
        ContractRevisionDO revision = new ContractRevisionDO()
                .setContractId(contract.getId()).setRevisionNo(nextNo).setBaseRevisionId(baseRevisionId)
                .setContractTypeVersionId(contract.getTypeVersionId()).setTemplateVersionId(templateVersionId)
                .setMainDocumentVersionId(contract.getCurrentDocumentVersionId())
                .setDocumentVersionIdsJson(JsonUtils.toJsonString(documentVersions))
                .setFieldSnapshotJson(JsonUtils.toJsonString(buildFieldSnapshot(contract)))
                .setPartySnapshotJson(JsonUtils.toJsonString(buildPartySnapshot(parties)))
                .setCommitmentSnapshotJson(JsonUtils.toJsonString(buildCommitmentSnapshot(commitments)))
                .setNoCommitment(Boolean.TRUE.equals(contract.getNoCommitmentConfirmed()))
                .setChangeSource(source).setChangeReason(StrUtil.nullToEmpty(reason));
        String actor = actorUserId == null ? null : String.valueOf(actorUserId);
        revision.setCreator(actor);
        revision.setUpdater(actor);
        revisionMapper.insert(revision);
        ContractDO pointerUpdate = new ContractDO().setId(contract.getId()).setCurrentRevisionId(revision.getId());
        pointerUpdate.setUpdater(actor);
        contractMapper.updateById(pointerUpdate);
        return revision.getId();
    }

    private Map<String, Object> buildFieldSnapshot(ContractDO c) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("name", c.getTitle()); fields.put("amount", c.getAmount()); fields.put("currency", c.getCurrency());
        fields.put("startDate", c.getEffectiveDate()); fields.put("endDate", c.getExpiryDate());
        fields.put("description", c.getDescription()); fields.put("customData", c.getCustomData());
        fields.put("ownerUserId", c.getOwnerUserId()); fields.put("ownerDeptId", c.getOwnerDeptId());
        fields.put("contractTypeId", c.getTypeId()); fields.put("sourceMode", c.getSourceMode());
        return fields;
    }

    private List<Map<String, Object>> buildPartySnapshot(List<ContractPartyDO> parties) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ContractPartyDO party : parties) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("partyId", party.getPartyId()); row.put("roleCode", party.getRoleCode());
            row.put("sort", party.getSort()); row.put("party", parseMap(party.getPartySnapshot()));
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> buildCommitmentSnapshot(List<CommitmentDO> commitments) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CommitmentDO item : commitments) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId()); row.put("category", item.getCategory()); row.put("content", item.getContent());
            row.put("ownerUserId", item.getOwnerUserId()); row.put("dueDate", item.getDueDate());
            row.put("riskLevel", item.getRiskLevel()); result.add(row);
        }
        return result;
    }

    private ContractRevisionRespVO toResp(ContractRevisionDO revision) {
        ContractRevisionRespVO vo = new ContractRevisionRespVO();
        vo.setId(revision.getId()); vo.setContractId(revision.getContractId()); vo.setRevisionNo(revision.getRevisionNo());
        vo.setBaseRevisionId(revision.getBaseRevisionId()); vo.setContractTypeVersionId(revision.getContractTypeVersionId());
        vo.setTemplateVersionId(revision.getTemplateVersionId()); vo.setMainDocumentVersionId(revision.getMainDocumentVersionId());
        vo.setDocumentVersionId(revision.getMainDocumentVersionId());
        vo.setDocumentVersionIds(parseList(revision.getDocumentVersionIdsJson(), Long.class));
        Map<String, Object> fields = parseMap(revision.getFieldSnapshotJson());
        vo.setFields(fields); vo.setName(asString(fields.get("name"))); vo.setAmount(asDecimal(fields.get("amount")));
        vo.setCurrency(asString(fields.get("currency"))); vo.setStartDate(asString(fields.get("startDate")));
        vo.setEndDate(asString(fields.get("endDate"))); vo.setParties(parseListMap(revision.getPartySnapshotJson()));
        List<Long> counterparties = new ArrayList<>();
        for (Map<String, Object> party : vo.getParties()) {
            Long partyId = asLong(party.get("partyId"));
            if ("OUR_SIDE".equals(party.get("roleCode"))) vo.setOurPartyId(partyId);
            if ("COUNTERPARTY".equals(party.get("roleCode")) && partyId != null) counterparties.add(partyId);
        }
        vo.setCounterpartyIds(counterparties);
        vo.setCommitments(parseListMap(revision.getCommitmentSnapshotJson())); vo.setNoCommitment(revision.getNoCommitment());
        vo.setChangeSource(revision.getChangeSource()); vo.setSourceType(revision.getChangeSource()); vo.setChangeReason(revision.getChangeReason());
        vo.setCreator(revision.getCreator()); vo.setCreatorName(resolveCreatorName(revision.getCreator()));
        vo.setCreateTime(revision.getCreateTime()); return vo;
    }

    private ContractDO getRequiredContract(Long id) {
        ContractDO contract = contractMapper.selectById(id);
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        return contract;
    }

    private ContractDO getRequiredContractForUpdate(Long id) {
        ContractDO contract = contractMapper.selectByIdForUpdate(id);
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        return contract;
    }

    private void assertCurrentBase(ContractDO contract, Long baseRevisionId) {
        if (!Objects.equals(contract.getCurrentRevisionId(), baseRevisionId)) {
            throw exception(CONTRACT_REVISION_CONFLICT, contract.getCurrentRevisionId());
        }
    }

    private ContractRevisionDO getRequiredBaseRevision(ContractDO contract, Long baseRevisionId) {
        ContractRevisionDO baseRevision = getRequiredRevision(baseRevisionId);
        if (!Objects.equals(contract.getId(), baseRevision.getContractId())) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
        return baseRevision;
    }

    /**
     * 范本来源描述的是正文来源，不因结构化字段、参与方或重大承诺变化而失效。
     * 一旦正文内容可能改变，就清空来源；提交时还会再按 SHA-256 与已发布范本严格比对。
     */
    private boolean changesDocumentBody(String changeSource) {
        return "DOCUMENT_UPLOAD".equals(changeSource)
                || "TEMPLATE_UPGRADE".equals(changeSource)
                || ClmDocumentSourceTypeEnum.contains(changeSource);
    }

    private Map<Long, PartyDO> loadParties(Long ourPartyId, List<Long> counterpartyIds) {
        Set<Long> ids = new LinkedHashSet<>(); ids.add(ourPartyId); ids.addAll(counterpartyIds);
        List<PartyDO> rows = partyMapper.selectByIds(ids);
        if (rows.size() != ids.size()) throw exception(PARTY_NOT_EXISTS);
        Map<Long, PartyDO> result = new HashMap<>(); for (PartyDO row : rows) result.put(row.getId(), row);
        return result;
    }

    private void replaceParties(Long contractId, Long ourPartyId, List<Long> counterpartyIds, Map<Long, PartyDO> partyMap) {
        contractPartyMapper.deleteByContractId(contractId);
        insertParty(contractId, ourPartyId, "OUR_SIDE", 0, partyMap.get(ourPartyId));
        int sort = 1; for (Long id : counterpartyIds) insertParty(contractId, id, "COUNTERPARTY", sort++, partyMap.get(id));
    }

    private void insertParty(Long contractId, Long partyId, String role, int sort, PartyDO party) {
        Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("name", party.getName());
        snapshot.put("shortName", party.getShortName());
        snapshot.put("unifiedCreditCode", party.getUnifiedCreditCode()); snapshot.put("partyType", party.getPartyType());
        contractPartyMapper.insert(new ContractPartyDO().setContractId(contractId).setPartyId(partyId)
                .setRoleCode(role).setSort(sort).setPartySnapshot(JsonUtils.toJsonString(snapshot)));
    }

    private Map<String, Object> parseMap(String json) {
        return StrUtil.isBlank(json) ? new LinkedHashMap<>() : JsonUtils.parseMap(json);
    }

    private List<Map<String, Object>> parseListMap(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (StrUtil.isBlank(json)) return result;
        for (Map row : JsonUtils.parseArray(json, Map.class)) {
            result.add(new LinkedHashMap<>(row));
        }
        return result;
    }

    private <T> List<T> parseList(String json, Class<T> type) {
        return StrUtil.isBlank(json) ? new ArrayList<>() : JsonUtils.parseArray(json, type);
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "name" -> "合同名称"; case "amount" -> "合同金额"; case "currency" -> "币种";
            case "startDate" -> "开始日期"; case "endDate" -> "结束日期"; case "description" -> "合同说明";
            case "customData" -> "扩展字段"; case "ownerUserId" -> "负责人"; case "ownerDeptId" -> "所属部门";
            case "contractTypeId" -> "合同类型"; case "sourceMode" -> "起草来源"; default -> field;
        };
    }

    private String asString(Object value) { return value == null ? null : String.valueOf(value); }
    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }
    private BigDecimal asDecimal(Object value) {
        try { return value == null ? null : new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ex) { return null; }
    }
    private String resolveCreatorName(String creator) {
        if (adminUserApi == null || !NumberUtil.isLong(creator)) return creator;
        AdminUserRespDTO user = adminUserApi.getUser(Long.valueOf(creator));
        return user == null ? creator : user.getNickname();
    }
}
