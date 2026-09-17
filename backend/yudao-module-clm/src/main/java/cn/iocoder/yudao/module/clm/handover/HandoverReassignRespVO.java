package cn.iocoder.yudao.module.clm.handover;

import lombok.Data;

import java.util.List;

@Data
public class HandoverReassignRespVO {
    private Long caseId;
    private String status;
    private Long pendingCount;
    private List<ItemResult> items;

    @Data
    public static class ItemResult {
        private Long itemId;
        private String itemType;
        private String status;
        private String resultMessage;
    }
}
