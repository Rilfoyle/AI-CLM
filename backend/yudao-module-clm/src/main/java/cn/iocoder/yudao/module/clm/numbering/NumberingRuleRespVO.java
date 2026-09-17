package cn.iocoder.yudao.module.clm.numbering;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NumberingRuleRespVO {
    private Long id;
    private String ruleCode;
    private Long contractTypeId;
    private Integer versionNo;
    private String status;
    private String prefix;
    private String datePattern;
    private String separator;
    private Integer sequenceLength;
    private String resetPeriod;
    private Boolean includePartyShortName;
    private LocalDateTime effectiveTime;
    private String sample;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
