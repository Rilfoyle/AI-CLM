package cn.iocoder.yudao.module.clm.enums.audit;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计动作
 */
@Getter
@AllArgsConstructor
public enum ClmAuditActionEnum {

    CONTRACT_CREATE("CONTRACT_CREATE", "创建合同"),
    CONTRACT_UPDATE("CONTRACT_UPDATE", "更新合同"),
    CONTRACT_DELETE("CONTRACT_DELETE", "删除合同"),
    CONTRACT_SUBMIT("CONTRACT_SUBMIT", "提交审批"),
    CONTRACT_ARCHIVE("CONTRACT_ARCHIVE", "归档定稿"),
    APPROVAL_RESULT("APPROVAL_RESULT", "审批结果"),
    DOCUMENT_UPLOAD("DOCUMENT_UPLOAD", "上传文档"),
    DOCUMENT_DOWNLOAD("DOCUMENT_DOWNLOAD", "下载文档"),
    PARTICIPANT_UPDATE("PARTICIPANT_UPDATE", "更新参与人"),
    TYPE_PUBLISH("TYPE_PUBLISH", "发布类型版本"),
    ONLINE_EDIT_OPEN("ONLINE_EDIT_OPEN", "打开在线编辑"),
    ONLINE_EDIT_SAVE("ONLINE_EDIT_SAVE", "保存在线编辑");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmAuditActionEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
