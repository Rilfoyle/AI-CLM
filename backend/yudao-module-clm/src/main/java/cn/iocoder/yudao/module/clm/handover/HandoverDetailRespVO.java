package cn.iocoder.yudao.module.clm.handover;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HandoverDetailRespVO {
    private Long id;
    private Long sourceUserId;
    private Long targetUserId;
    private String status;
    private String reason;
    private Long handledBy;
    private LocalDateTime finishedTime;
    private List<HandoverItemRespVO> items;
}
