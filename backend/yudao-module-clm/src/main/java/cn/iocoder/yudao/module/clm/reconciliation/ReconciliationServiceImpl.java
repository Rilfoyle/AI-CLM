package cn.iocoder.yudao.module.clm.reconciliation;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum;
import cn.iocoder.yudao.module.clm.governance.GovernanceIssueService;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import jakarta.annotation.Resource;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Set<String> CONFIRMABLE_RESULTS = Set.of(
            "ISSUE_OPENED", "PROJECTION_REPAIRED", "CHECK_FAILED");

    @Resource private ReconciliationRunMapper runMapper;
    @Resource private WorkflowBindingMapper bindingMapper;
    @Resource private BpmProcessInstanceService processInstanceService;
    @Resource private ContractWorkflowService contractWorkflowService;
    @Resource private GovernanceIssueService governanceIssueService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long run(ReconciliationRunReqVO reqVO) {
        ReconciliationRunDO existing = runMapper.selectByRunKey(reqVO.getRunKey());
        if (existing != null) return existing.getId();
        ReconciliationRunDO run = new ReconciliationRunDO().setRunKey(reqVO.getRunKey())
                .setTriggerType(reqVO.getTriggerType()).setStatus("RUNNING")
                .setScannedCount(0).setIssueCount(0).setRepairedCount(0);
        runMapper.insert(run);

        List<Map<String, Object>> report = new ArrayList<>();
        int issues = 0;
        int repaired = 0;
        List<WorkflowBindingDO> bindings = bindingMapper.selectList();
        for (WorkflowBindingDO binding : bindings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bindingId", binding.getId());
            row.put("contractId", binding.getContractId());
            row.put("processInstanceId", binding.getProcessInstanceId());
            try {
                if (binding.getProcessInstanceId() == null) {
                    if (ClmWorkflowBindingStatusEnum.PREPARING.getStatus().equals(binding.getStatus())) {
                        issues++;
                        governanceIssueService.open("PROCESS_START_INCOMPLETE", "binding:" + binding.getId(),
                                binding.getContractId(), binding.getId(), null,
                                "审批业务单已建立，但未绑定 Flowable 实例", row);
                        row.put("result", "ISSUE_OPENED");
                    }
                    report.add(row);
                    continue;
                }
                HistoricProcessInstance instance = processInstanceService
                        .getHistoricProcessInstance(binding.getProcessInstanceId());
                if (instance == null) {
                    issues++;
                    governanceIssueService.open("PROCESS_INSTANCE_MISSING", "binding:" + binding.getId(),
                            binding.getContractId(), binding.getId(), null,
                            "审批业务单引用的 Flowable 实例不存在", row);
                    row.put("result", "ISSUE_OPENED");
                    report.add(row);
                    continue;
                }
                Integer authoritativeStatus = FlowableUtils.getProcessInstanceStatus(instance);
                row.put("authoritativeStatus", authoritativeStatus);
                row.put("projectionStatus", binding.getStatus());
                if (!Objects.equals(authoritativeStatus, binding.getStatus())) {
                    contractWorkflowService.handleProcessResult(binding, authoritativeStatus,
                            "流程对账依据 Flowable 权威终态幂等恢复");
                    repaired++;
                    row.put("result", "PROJECTION_REPAIRED");
                } else {
                    row.put("result", "CONSISTENT");
                }
            } catch (RuntimeException ex) {
                issues++;
                row.put("result", "CHECK_FAILED");
                row.put("error", ex.getClass().getSimpleName());
                governanceIssueService.open("RECONCILIATION_FAILED", "binding:" + binding.getId(),
                        binding.getContractId(), binding.getId(), null,
                        "审批一致性检查失败，可安全重试", row);
            }
            report.add(row);
        }
        String status = issues == 0 ? "SUCCEEDED" : (repaired > 0 ? "PARTIAL" : "FAILED");
        runMapper.updateById(new ReconciliationRunDO().setId(run.getId()).setStatus(status)
                .setScannedCount(bindings.size()).setIssueCount(issues).setRepairedCount(repaired)
                .setReportJson(JsonUtils.toJsonString(report)).setFinishedTime(LocalDateTime.now()));
        return run.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirm(ReconciliationConfirmReqVO reqVO, Long userId) {
        if (userId == null) {
            throw invalidParamException("业务确认人不能为空");
        }
        ReconciliationRunDO run = runMapper.selectByIdForUpdate(reqVO.getRunId());
        if (run == null) {
            throw invalidParamException("对账运行不存在");
        }
        List<Map<String, Object>> report = parseReport(run.getReportJson());
        Map<String, Object> target = report.stream()
                .filter(row -> sameId(row.get("bindingId"), reqVO.getBindingId()))
                .findFirst()
                .orElseThrow(() -> invalidParamException("对账报告中不存在该审批业务单"));
        String result = Objects.toString(target.get("result"), "");
        if (!CONFIRMABLE_RESULTS.contains(result)) {
            throw invalidParamException("只有差异或异常项可以进行业务确认");
        }
        if ("CONFIRMED".equals(target.get("confirmationStatus"))) {
            return true;
        }
        target.put("confirmationStatus", "CONFIRMED");
        target.put("confirmedBy", userId);
        target.put("confirmedTime", LocalDateTime.now());
        target.put("confirmationOpinion", reqVO.getOpinion().trim());
        runMapper.updateById(new ReconciliationRunDO().setId(run.getId())
                .setReportJson(JsonUtils.toJsonString(report)));
        return true;
    }

    @Override
    public PageResult<ReconciliationRunDO> getPage(PageParam pageParam) {
        return runMapper.selectPage(pageParam);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> parseReport(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            throw invalidParamException("对账报告为空，无法进行业务确认");
        }
        try {
            List<Map<String, Object>> report = new ArrayList<>();
            for (Map row : JsonUtils.parseArray(reportJson, Map.class)) {
                report.add(new LinkedHashMap<>(row));
            }
            return report;
        } catch (RuntimeException ex) {
            throw invalidParamException("对账报告格式错误，无法进行业务确认");
        }
    }

    private boolean sameId(Object value, Long expected) {
        return value != null && Objects.equals(String.valueOf(value), String.valueOf(expected));
    }
}
