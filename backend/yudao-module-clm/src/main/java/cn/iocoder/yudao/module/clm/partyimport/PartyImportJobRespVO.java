package cn.iocoder.yudao.module.clm.partyimport;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PartyImportJobRespVO {
    private Long id;
    private String jobKey;
    private String fileName;
    private String status;
    private Integer totalCount;
    private Integer validCount;
    private Integer invalidCount;
    private Integer skippedCount;
    private Integer successCount;
    private Integer failedCount;
    private LocalDateTime finishedTime;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<PartyImportItemRespVO> items;
}
