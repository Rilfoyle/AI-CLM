package cn.iocoder.yudao.module.clm.approval;

import lombok.Data;

import java.util.List;

@Data
public class ApprovalHistoryRespVO {
    private Long contractId;
    private List<ApprovalHistoryCaseRespVO> cases;
}
