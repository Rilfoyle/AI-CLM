package cn.iocoder.yudao.module.clm.approval;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalOpinionRespVO {
    private String taskId;
    private String nodeName;
    private String userName;
    private String action;
    private String reason;
    private Long revisionId;
    private LocalDateTime createTime;
}
