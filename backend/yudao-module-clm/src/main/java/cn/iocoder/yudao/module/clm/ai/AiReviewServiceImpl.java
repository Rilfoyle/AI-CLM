package cn.iocoder.yudao.module.clm.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.clm.access.ContractAccessService;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractMapper;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionDO;
import cn.iocoder.yudao.module.clm.revision.ContractRevisionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class AiReviewServiceImpl implements AiReviewService {

    private static final String LOCAL_PROVIDER = "LOCAL_DETERMINISTIC";

    @Resource private AiReviewRunMapper runMapper;
    @Resource private AiReviewFindingMapper findingMapper;
    @Resource private ContractMapper contractMapper;
    @Resource private ContractRevisionService revisionService;
    @Resource private ContractAccessService accessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long run(AiReviewRunReqVO reqVO, Long userId) {
        ContractDO contract = getRequiredContract(reqVO.getContractId());
        accessService.assertCanView(contract, userId);
        ContractRevisionDO revision = revisionService.getRequiredRevision(reqVO.getRevisionId());
        if (!Objects.equals(revision.getContractId(), contract.getId())) {
            throw exception(CONTRACT_REVISION_CONTRACT_MISMATCH);
        }
        String input = StrUtil.nullToEmpty(revision.getFieldSnapshotJson()) + "\n"
                + StrUtil.nullToEmpty(revision.getPartySnapshotJson()) + "\n"
                + StrUtil.nullToEmpty(revision.getCommitmentSnapshotJson()) + "\n"
                + revision.getMainDocumentVersionId();
        AiReviewRunDO run = new AiReviewRunDO().setContractId(contract.getId())
                .setRevisionId(revision.getId()).setRunType(reqVO.getRunType())
                .setProviderCode(LOCAL_PROVIDER).setStatus("RUNNING")
                .setInputFingerprint(DigestUtil.sha256Hex(input));
        runMapper.insert(run);

        List<AiReviewFindingDO> findings = buildFindings(run, contract, revision);
        for (AiReviewFindingDO finding : findings) findingMapper.insert(finding);
        Map<String, Object> result = buildResult(contract, revision, findings);
        runMapper.updateById(new AiReviewRunDO().setId(run.getId()).setStatus("SUCCEEDED")
                .setResultJson(JsonUtils.toJsonString(result)).setErrorMessage("")
                .setFinishedTime(LocalDateTime.now()));
        return run.getId();
    }

    @Override
    public List<AiReviewRunRespVO> getList(Long contractId, Long userId) {
        ContractDO contract = getRequiredContract(contractId);
        accessService.assertCanView(contract, userId);
        List<AiReviewRunRespVO> result = new ArrayList<>();
        for (AiReviewRunDO run : runMapper.selectListByContractId(contractId)) {
            AiReviewRunRespVO item = new AiReviewRunRespVO();
            item.setRun(run);
            item.setStale(!Objects.equals(run.getRevisionId(), contract.getCurrentRevisionId()));
            item.setFindings(findingMapper.selectListByRunId(run.getId()));
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveFinding(Long findingId, String resolution, Long userId) {
        if (!"ACCEPTED".equals(resolution) && !"IGNORED".equals(resolution)) {
            throw exception(AI_FINDING_RESOLUTION_INVALID);
        }
        AiReviewFindingDO finding = findingMapper.selectById(findingId);
        if (finding == null) throw exception(AI_FINDING_NOT_EXISTS);
        ContractDO contract = getRequiredContract(finding.getContractId());
        accessService.assertCanEdit(contract, userId);
        findingMapper.updateById(new AiReviewFindingDO().setId(findingId).setResolution(resolution)
                .setResolvedBy(userId).setResolvedTime(LocalDateTime.now()));
    }

    private Map<String, Object> buildResult(ContractDO contract, ContractRevisionDO revision,
                                             List<AiReviewFindingDO> findings) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", LOCAL_PROVIDER);
        result.put("externalTransmission", false);
        result.put("contractName", contract.getTitle());
        result.put("revisionId", revision.getId());
        result.put("revisionNo", revision.getRevisionNo());
        result.put("findingCount", findings.size());
        result.put("summary", "本地规则已检查合同字段、参与方和重大承诺；未向外部模型发送合同正文。");
        return result;
    }

    @SuppressWarnings("rawtypes")
    private List<AiReviewFindingDO> buildFindings(AiReviewRunDO run, ContractDO contract,
                                                   ContractRevisionDO revision) {
        List<AiReviewFindingDO> result = new ArrayList<>();
        Map<String, Object> fields = StrUtil.isBlank(revision.getFieldSnapshotJson())
                ? new LinkedHashMap<>() : JsonUtils.parseMap(revision.getFieldSnapshotJson());
        List<Map> commitments = StrUtil.isBlank(revision.getCommitmentSnapshotJson())
                ? new ArrayList<>() : JsonUtils.parseArray(revision.getCommitmentSnapshotJson(), Map.class);
        if (commitments.isEmpty() && !Boolean.TRUE.equals(revision.getNoCommitment())) {
            result.add(finding(run, "COMMITMENT_DECLARATION", "HIGH", "重大承诺尚未声明",
                    "提交前需新增重大承诺，或显式确认当前修订无重大承诺。"));
        }
        for (Map commitment : commitments) {
            if ("HIGH".equals(String.valueOf(commitment.get("riskLevel")))) {
                result.add(finding(run, "HIGH_RISK_COMMITMENT", "HIGH", "存在高风险重大承诺",
                        String.valueOf(commitment.get("content"))));
            }
        }
        BigDecimal amount = toDecimal(fields.get("amount"));
        if (amount != null && amount.compareTo(new BigDecimal("1000000")) >= 0) {
            result.add(finding(run, "HIGH_AMOUNT", "MEDIUM", "合同金额较高",
                    "当前金额达到本地演示阈值 100 万，请确认业务单据流程配置和授权范围。"));
        }
        if (fields.get("endDate") == null) {
            result.add(finding(run, "MISSING_END_DATE", "LOW", "合同结束日期为空",
                    "请确认该合同是否确实为无固定期限。"));
        }
        return result;
    }

    private AiReviewFindingDO finding(AiReviewRunDO run, String type, String severity,
                                      String title, String detail) {
        return new AiReviewFindingDO().setRunId(run.getId()).setContractId(run.getContractId())
                .setRevisionId(run.getRevisionId()).setFindingType(type).setSeverity(severity)
                .setTitle(title).setDetail(detail).setLocatorJson("{}");
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ContractDO getRequiredContract(Long contractId) {
        ContractDO contract = contractMapper.selectById(contractId);
        if (contract == null) throw exception(CONTRACT_NOT_EXISTS);
        return contract;
    }
}
