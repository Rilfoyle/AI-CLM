package cn.iocoder.yudao.module.clm.partyimport;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PartyImportItemRespVO {
    private Long id;
    private Long jobId;
    private Integer rowNo;
    private String name;
    private String unifiedCreditCode;
    private String contactName;
    private String contactPhone;
    private Long duplicatePartyId;
    private String status;
    private String errorMessage;
    private Long partyId;
    private LocalDateTime updateTime;
}
