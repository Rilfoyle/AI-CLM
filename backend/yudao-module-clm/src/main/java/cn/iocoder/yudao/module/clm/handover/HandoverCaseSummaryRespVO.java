package cn.iocoder.yudao.module.clm.handover;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HandoverCaseSummaryRespVO {
    private Long id;
    private Long sourceUserId;
    private Long targetUserId;
    private String status;
    private String reason;
    private Long handledBy;
    private Long itemCount;
    private Long pendingCount;
    private LocalDateTime finishedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
