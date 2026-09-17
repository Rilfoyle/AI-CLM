package cn.iocoder.yudao.module.clm.enums.audit;

import cn.hutool.core.util.ArrayUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计聚合类型
 */
@Getter
@AllArgsConstructor
public enum ClmAuditAggregateTypeEnum {

    CONTRACT("CONTRACT", "合同"),
    DOCUMENT("DOCUMENT", "文档"),
    CONTRACT_TYPE("CONTRACT_TYPE", "合同分类"),
    TEMPLATE("TEMPLATE", "合同范本"),
    NUMBERING_RULE("NUMBERING_RULE", "编码规则"),
    ROUTING_RULE("ROUTING_RULE", "业务单据流程配置"),
    PERMISSION_POLICY("PERMISSION_POLICY", "数据权限规则"),
    USER_SCOPE("USER_SCOPE", "用户合同域授权"),
    PARTY("PARTY", "相对方信息"),
    PARTY_IMPORT("PARTY_IMPORT", "相对方导入"),
    HANDOVER("HANDOVER", "经办人变更"),
    INTEGRATION("INTEGRATION", "外部集成");

    /**
     * 编码
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    public static ClmAuditAggregateTypeEnum of(String code) {
        return ArrayUtil.firstMatch(item -> item.getCode().equals(code), values());
    }

    public static boolean contains(String code) {
        return of(code) != null;
    }

}
