package cn.iocoder.yudao.module.clm.template;

import lombok.Data;

@Data
public class TemplateUpgradePreviewRespVO {
    private Long contractId;
    private Boolean upgradeAvailable;
    private Long oldTemplateVersionId;
    private Integer oldTemplateVersionNo;
    private String oldChecksumSha256;
    private Long newTemplateVersionId;
    private Integer newTemplateVersionNo;
    private String newChecksumSha256;
}
