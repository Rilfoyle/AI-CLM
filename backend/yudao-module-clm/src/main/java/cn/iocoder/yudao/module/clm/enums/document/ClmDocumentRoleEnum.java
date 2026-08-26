package cn.iocoder.yudao.module.clm.enums.document;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档角色
 */
@Getter
@AllArgsConstructor
public enum ClmDocumentRoleEnum {

    MAIN("MAIN", "正文"),
    ATTACHMENT("ATTACHMENT", "附件");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmDocumentRoleEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
