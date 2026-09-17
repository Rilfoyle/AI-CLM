package cn.iocoder.yudao.module.clm.commitment;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CommitmentRespVO {
    private Long id;
    private Long contractId;
    private String category;
    private String content;
    private Long ownerUserId;
    private LocalDate dueDate;
    private String riskLevel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
