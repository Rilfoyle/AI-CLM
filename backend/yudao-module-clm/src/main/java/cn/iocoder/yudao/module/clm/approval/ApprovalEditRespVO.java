package cn.iocoder.yudao.module.clm.approval;

import lombok.Data;

@Data
public class ApprovalEditRespVO {
    private Long revisionId;
    private Integer revisionNo;
    private Boolean majorChange;
    private Long approvalCaseId;
    private String processInstanceId;
}
