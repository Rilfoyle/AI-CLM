package cn.iocoder.yudao.module.clm.handover;

import lombok.Data;

@Data
public class HandoverItemRespVO {
    private Long id;
    private String itemType;
    private Long contractId;
    private String contractNo;
    private String contractName;
    private String taskId;
    private Long originalAssignee;
    private Long targetUserId;
    private String status;
    private String resultMessage;
}
