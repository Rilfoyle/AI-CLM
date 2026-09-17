package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import lombok.Data;

import java.util.List;
import java.time.LocalDateTime;

@Data
public class ApprovalHistoryCaseRespVO {
    private WorkflowBindingDO approvalCase;
    private List<ApprovalTaskRevisionBindingDO> decisions;
    private String displayStatus;
    private Long id;
    private String status;
    private String cancelReason;
    private Long submittedRevisionId;
    private Long currentRevisionId;
    private Long approvedRevisionId;
    private Long supersedesCaseId;
    private String processInstanceId;
    private List<ApprovalOpinionRespVO> opinions;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
