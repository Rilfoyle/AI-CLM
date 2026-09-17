package cn.iocoder.yudao.module.clm.handover;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandoverRefreshRespVO {
    private Long caseId;
    private String status;
    private Long itemCount;
    private Long pendingCount;
}
