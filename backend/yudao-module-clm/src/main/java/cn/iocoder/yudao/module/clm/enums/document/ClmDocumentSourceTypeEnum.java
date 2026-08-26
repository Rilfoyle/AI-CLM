package cn.iocoder.yudao.module.clm.enums.document;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档版本来源
 */
@Getter
@AllArgsConstructor
public enum ClmDocumentSourceTypeEnum {

    UPLOAD("UPLOAD", "上传"),
    ONLINE_EDIT("ONLINE_EDIT", "在线编辑"),
    MANUAL_FINAL("MANUAL_FINAL", "线下定稿回传");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmDocumentSourceTypeEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
