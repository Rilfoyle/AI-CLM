package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentVersionRespVO;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ApprovalTaskDetailRespVO {
    private BpmTaskRespVO task;
    private String taskId;
    private String taskName;
    private String taskStatus;
    private ContractRespVO contract;
    private Long contractId;
    private Long approvalCaseId;
    private Long submittedRevisionId;
    private Long currentRevisionId;
    private DocumentVersionRespVO mainDocument;
    private List<Map<String, Object>> revisions;
    private List<Map<String, Object>> partySnapshot;
    private List<Map<String, Object>> commitmentSnapshot;
    private List<ApprovalTaskRevisionBindingDO> historyOpinions;
    /** 前端稳定字段别名。 */
    private List<Map<String, Object>> parties;
    private List<Map<String, Object>> commitments;
    private List<ApprovalOpinionRespVO> opinions;
    private List<String> availableActions;
    private Map<String, Object> editPolicy;
    private List<ApprovalReturnTargetRespVO> returnTargets;
    private Map<String, Object> nodeEditPolicy;
}
