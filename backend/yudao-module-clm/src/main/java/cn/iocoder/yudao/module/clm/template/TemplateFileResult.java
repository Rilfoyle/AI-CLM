package cn.iocoder.yudao.module.clm.template;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateFileResult {
    private String fileName;
    private String mimeType;
    private byte[] content;
}
