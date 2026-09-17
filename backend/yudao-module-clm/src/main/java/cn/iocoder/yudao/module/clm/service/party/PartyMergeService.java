package cn.iocoder.yudao.module.clm.service.party;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyDuplicateGroupRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyMergeReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyMergeRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmApprovalStatusEnum;
import cn.iocoder.yudao.module.clm.enums.contract.ClmLifecycleStatusEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PARTY_NOT_EXISTS;
import static cn.iocoder.yudao.module.clm.service.party.PartyMergeErrors.*;

/**
 * 参与方查重与安全合并。
 *
 * 只改写草稿/协作中合同的 {@code clm_contract_party} 活引用；
 * {@code clm_contract_revision.party_snapshot_json} 为不可变历史，本服务不依赖也不写入修订表。
 */
@Service
public class PartyMergeService {

    public static final String MATCH_CREDIT_CODE = "UNIFIED_CREDIT_CODE";
    public static final String MATCH_NORMALIZED_NAME = "NORMALIZED_NAME";
    private static final String STAGE_DRAFT = "DRAFT";
    private static final String STAGE_COLLABORATING = "COLLABORATING";
    private static final Pattern MERGED_TO_PATTERN = Pattern.compile("(?:^|\\s)mergedToPartyId=(\\d+)(?:$|\\s)");
    private static final int REMARK_MAX_LENGTH = 255;

    @Resource private PartyMapper partyMapper;
    @Resource private ContractPartyMapper contractPartyMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ClmAuditService auditService;

    public List<PartyDuplicateGroupRespVO> getDuplicateCandidates(Boolean internalFlag, Integer partyType) {
        List<PartyDO> candidates = partyMapper.selectDuplicateCandidates(
                CommonStatusEnum.ENABLE.getStatus(), internalFlag, partyType);
        if (candidates.size() < 2) {
            return List.of();
        }
        List<PartyDuplicateGroupRespVO> result = new ArrayList<>();
        Set<Long> claimedByCreditCode = new HashSet<>();
        appendGroups(result, candidates, MATCH_CREDIT_CODE, this::normalizeCreditCode, claimedByCreditCode);
        List<PartyDO> nameCandidates = candidates.stream()
                .filter(party -> !claimedByCreditCode.contains(party.getId()))
                .toList();
        appendGroups(result, nameCandidates, MATCH_NORMALIZED_NAME, this::normalizeName, new HashSet<>());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PartyMergeRespVO merge(PartyMergeReqVO reqVO) {
        Long sourceId = reqVO.getSourcePartyId();
        Long targetId = reqVO.getTargetPartyId();
        if (Objects.equals(sourceId, targetId)) {
            throw exception(PARTY_MERGE_SAME_PARTY);
        }
        LockedParties locked = lockParties(sourceId, targetId);
        PartyDO source = locked.source();
        PartyDO target = locked.target();
        Long mergedTo = parseMergedToPartyId(source.getRemark());
        if (Objects.equals(mergedTo, targetId)) {
            return buildResponse(sourceId, targetId, 0, 0, 0, 0, true);
        }
        if (mergedTo != null) {
            throw exception(PARTY_MERGE_SOURCE_ALREADY_MERGED, mergedTo, targetId);
        }
        if (!Objects.equals(source.getInternalFlag(), target.getInternalFlag())) {
            throw exception(PARTY_MERGE_INTERNAL_FLAG_MISMATCH);
        }
        if (!Objects.equals(source.getPartyType(), target.getPartyType())) {
            throw exception(PARTY_MERGE_PARTY_TYPE_MISMATCH);
        }
        if (!CommonStatusEnum.isEnable(target.getStatus())) {
            throw exception(PARTY_MERGE_TARGET_DISABLED);
        }

        List<ContractPartyDO> sourceLinks = contractPartyMapper.selectList(ContractPartyDO::getPartyId, sourceId);
        List<ContractPartyDO> targetLinks = contractPartyMapper.selectList(ContractPartyDO::getPartyId, targetId);
        Set<Long> contractIds = new LinkedHashSet<>();
        sourceLinks.forEach(link -> contractIds.add(link.getContractId()));
        Map<Long, ContractDO> contracts = new HashMap<>();
        if (!contractIds.isEmpty()) {
            for (ContractDO contract : contractMapper.selectByIds(contractIds)) {
                contracts.put(contract.getId(), contract);
            }
        }

        Set<String> targetRoleKeys = new HashSet<>();
        targetLinks.forEach(link -> targetRoleKeys.add(roleKey(link.getContractId(), link.getRoleCode())));
        Set<Long> rewrittenContracts = new LinkedHashSet<>();
        Set<Long> protectedContracts = new LinkedHashSet<>();
        int rewrittenLinks = 0;
        int deduplicatedLinks = 0;
        String targetSnapshot = buildPartySnapshot(target);
        for (ContractPartyDO sourceLink : sourceLinks) {
            ContractDO contract = contracts.get(sourceLink.getContractId());
            if (!canRewriteLiveReference(contract)) {
                protectedContracts.add(sourceLink.getContractId());
                continue;
            }
            String roleKey = roleKey(sourceLink.getContractId(), sourceLink.getRoleCode());
            if (targetRoleKeys.contains(roleKey)) {
                contractPartyMapper.deleteById(sourceLink.getId());
                deduplicatedLinks++;
            } else {
                contractPartyMapper.updateById(new ContractPartyDO().setId(sourceLink.getId())
                        .setPartyId(targetId).setPartySnapshot(targetSnapshot));
                targetRoleKeys.add(roleKey);
                rewrittenLinks++;
            }
            rewrittenContracts.add(sourceLink.getContractId());
        }

        partyMapper.updateById(new PartyDO().setId(sourceId)
                .setStatus(CommonStatusEnum.DISABLE.getStatus())
                .setRemark(buildMergedRemark(source.getRemark(), targetId)));
        PartyMergeRespVO response = buildResponse(sourceId, targetId, rewrittenContracts.size(), rewrittenLinks,
                deduplicatedLinks, protectedContracts.size(), false);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourcePartyId", sourceId);
        detail.put("sourcePartyName", source.getName());
        detail.put("targetPartyId", targetId);
        detail.put("targetPartyName", target.getName());
        detail.put("rewrittenContractIds", rewrittenContracts);
        detail.put("protectedContractIds", protectedContracts);
        detail.put("rewrittenLinkCount", rewrittenLinks);
        detail.put("deduplicatedLinkCount", deduplicatedLinks);
        auditService.record(ClmAuditAggregateTypeEnum.PARTY, targetId, null,
                ClmAuditActionEnum.PARTY_MERGE, detail);
        return response;
    }

    private void appendGroups(List<PartyDuplicateGroupRespVO> result, List<PartyDO> parties, String matchType,
                              Function<PartyDO, String> valueExtractor, Set<Long> claimed) {
        Map<GroupKey, List<PartyDO>> groups = new LinkedHashMap<>();
        for (PartyDO party : parties) {
            String value = valueExtractor.apply(party);
            if (StrUtil.isBlank(value)) {
                continue;
            }
            GroupKey key = new GroupKey(party.getPartyType(), party.getInternalFlag(), value);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(party);
        }
        for (Map.Entry<GroupKey, List<PartyDO>> entry : groups.entrySet()) {
            List<PartyDO> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            group.sort(Comparator.comparing(PartyDO::getId));
            PartyDuplicateGroupRespVO respVO = new PartyDuplicateGroupRespVO();
            respVO.setMatchType(matchType);
            respVO.setMatchValue(entry.getKey().value());
            respVO.setRecommendedTargetPartyId(group.get(0).getId());
            respVO.setParties(group.stream().map(party -> BeanUtils.toBean(party, PartyRespVO.class)).toList());
            result.add(respVO);
            group.forEach(party -> claimed.add(party.getId()));
        }
    }

    private LockedParties lockParties(Long sourceId, Long targetId) {
        Long firstId = Math.min(sourceId, targetId);
        Long secondId = Math.max(sourceId, targetId);
        PartyDO first = partyMapper.selectByIdForUpdate(firstId);
        PartyDO second = partyMapper.selectByIdForUpdate(secondId);
        if (first == null || second == null) {
            throw exception(PARTY_NOT_EXISTS);
        }
        PartyDO source = Objects.equals(first.getId(), sourceId) ? first : second;
        PartyDO target = Objects.equals(first.getId(), targetId) ? first : second;
        return new LockedParties(source, target);
    }

    private boolean canRewriteLiveReference(ContractDO contract) {
        if (contract == null || !ClmLifecycleStatusEnum.DRAFT.getStatus().equals(contract.getLifecycleStatus())) {
            return false;
        }
        Integer approvalStatus = contract.getApprovalStatus();
        boolean editableApproval = ClmApprovalStatusEnum.NOT_SUBMITTED.getStatus().equals(approvalStatus)
                || ClmApprovalStatusEnum.REJECTED.getStatus().equals(approvalStatus)
                || ClmApprovalStatusEnum.CANCELED.getStatus().equals(approvalStatus);
        if (!editableApproval) {
            return false;
        }
        return StrUtil.isBlank(contract.getStageCode()) || STAGE_DRAFT.equals(contract.getStageCode())
                || STAGE_COLLABORATING.equals(contract.getStageCode());
    }

    private String normalizeCreditCode(PartyDO party) {
        return StrUtil.nullToEmpty(party.getUnifiedCreditCode()).replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeName(PartyDO party) {
        String normalized = Normalizer.normalize(StrUtil.nullToEmpty(party.getName()), Normalizer.Form.NFKC);
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String buildPartySnapshot(PartyDO party) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", party.getName());
        snapshot.put("shortName", party.getShortName());
        snapshot.put("unifiedCreditCode", party.getUnifiedCreditCode());
        snapshot.put("partyType", party.getPartyType());
        return JsonUtils.toJsonString(snapshot);
    }

    private Long parseMergedToPartyId(String remark) {
        if (StrUtil.isBlank(remark)) {
            return null;
        }
        Matcher matcher = MERGED_TO_PATTERN.matcher(remark);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String buildMergedRemark(String oldRemark, Long targetId) {
        String marker = "mergedToPartyId=" + targetId;
        String old = StrUtil.trim(oldRemark);
        if (StrUtil.isBlank(old)) {
            return marker;
        }
        int oldLimit = Math.max(0, REMARK_MAX_LENGTH - marker.length() - 1);
        return StrUtil.sub(old, 0, oldLimit) + "\n" + marker;
    }

    private String roleKey(Long contractId, String roleCode) {
        return contractId + ":" + roleCode;
    }

    private PartyMergeRespVO buildResponse(Long sourceId, Long targetId, int rewrittenContracts,
                                           int rewrittenLinks, int deduplicatedLinks, int protectedContracts,
                                           boolean idempotent) {
        PartyMergeRespVO respVO = new PartyMergeRespVO();
        respVO.setSourcePartyId(sourceId);
        respVO.setTargetPartyId(targetId);
        respVO.setRewrittenContractCount(rewrittenContracts);
        respVO.setRewrittenLinkCount(rewrittenLinks);
        respVO.setDeduplicatedLinkCount(deduplicatedLinks);
        respVO.setProtectedContractCount(protectedContracts);
        respVO.setIdempotent(idempotent);
        return respVO;
    }

    private record GroupKey(Integer partyType, Boolean internalFlag, String value) {}

    private record LockedParties(PartyDO source, PartyDO target) {}
}
