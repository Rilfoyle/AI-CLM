package cn.iocoder.yudao.module.clm.template;

import lombok.Data;

@Data
public class PublishedTemplateRespVO {
    private Long id;
    private String code;
    private String name;
    private Long contractTypeId;
    private Long currentVersionId;
    private Integer currentVersionNo;
    private String fileName;
}
