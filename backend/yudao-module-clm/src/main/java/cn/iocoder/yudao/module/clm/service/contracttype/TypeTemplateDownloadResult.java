package cn.iocoder.yudao.module.clm.service.contracttype;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 合同类型范本下载结果：文件名 + 内容
 */
@Data
@AllArgsConstructor
public class TypeTemplateDownloadResult {

    /**
     * 文件名
     */
    private String fileName;
    /**
     * 内容
     */
    private byte[] content;

}
