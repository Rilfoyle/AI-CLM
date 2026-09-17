package cn.iocoder.yudao.module.clm.template;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TemplateGovernanceRespVO {
    private Long id;
    private String code;
    private String name;
    private Long contractTypeId;
    private Long currentVersionId;
    private Integer status;
    private String description;
    private Long draftVersionId;
    private Integer currentVersionNo;
    private String currentFileName;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<TemplateVersionRespVO> versions;
}
