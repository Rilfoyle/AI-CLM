package cn.iocoder.yudao.module.clm.onlyoffice;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 在线编辑访问令牌 payload
 *
 * 字段名刻意使用短名，与规格 §2 的 JSON 一致：
 * { "v": versionId, "d": documentId, "c": contractId, "u": userId, "t": tenantId, "m": "edit"|"view", "p": "file"|"callback", "exp": epochSeconds }
 */
@Data
@Accessors(chain = true)
public class OnlyOfficeTokenPayload {

    public static final String MODE_EDIT = "edit";
    public static final String MODE_VIEW = "view";
    public static final String PURPOSE_FILE = "file";
    public static final String PURPOSE_CALLBACK = "callback";

    /**
     * 文档版本编号
     */
    private Long v;
    /**
     * 文档编号
     */
    private Long d;
    /**
     * 合同编号
     */
    private Long c;
    /**
     * 用户编号
     */
    private Long u;
    /**
     * 租户编号
     */
    private Long t;
    /**
     * 模式：edit / view
     */
    private String m;
    /**
     * 用途：file / callback
     */
    private String p;
    /**
     * 过期时间（epoch 秒）
     */
    private Long exp;

}
