package cn.iocoder.yudao.module.clm.template;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVersionRespVO {
    private Long id;
    private Long templateId;
    private Integer versionNo;
    private String status;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String checksumSha256;
    private LocalDateTime publishedTime;
    private String remark;
    private String creator;
    private LocalDateTime createTime;
}
