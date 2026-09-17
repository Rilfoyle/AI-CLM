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
    CONTRACT_RESTORE("CONTRACT_RESTORE", "恢复合同草稿"),
    CONTRACT_SUBMIT("CONTRACT_SUBMIT", "提交审批"),
    CONTRACT_ARCHIVE("CONTRACT_ARCHIVE", "归档定稿"),
    APPROVAL_RESULT("APPROVAL_RESULT", "审批结果"),
    APPROVAL_EDIT("APPROVAL_EDIT", "审批中编辑合同"),
    APPROVAL_SUPERSEDE("APPROVAL_SUPERSEDE", "重大修订重新审批"),
    APPROVAL_TRANSFER("APPROVAL_TRANSFER", "转交审批任务"),
    APPROVAL_COPY("APPROVAL_COPY", "抄送审批任务"),
    APPROVAL_ADD_SIGN("APPROVAL_ADD_SIGN", "审批任务加审"),
    APPROVAL_WITHDRAW_TASK("APPROVAL_WITHDRAW_TASK", "审批人撤回已办"),
    APPROVAL_STARTER_WITHDRAW("APPROVAL_STARTER_WITHDRAW", "发起人撤回审批"),
    DOCUMENT_UPLOAD("DOCUMENT_UPLOAD", "上传文档"),
    DOCUMENT_DOWNLOAD("DOCUMENT_DOWNLOAD", "下载文档"),
    PARTICIPANT_UPDATE("PARTICIPANT_UPDATE", "更新参与人"),
    TYPE_PUBLISH("TYPE_PUBLISH", "发布合同分类版本"),
    TEMPLATE_DRAFT_SAVE("TEMPLATE_DRAFT_SAVE", "保存范本草稿"),
    TEMPLATE_PUBLISH("TEMPLATE_PUBLISH", "发布范本版本"),
    TEMPLATE_DISABLE("TEMPLATE_DISABLE", "停用范本"),
    TEMPLATE_UPGRADE("TEMPLATE_UPGRADE", "显式升级合同范本"),
    NUMBERING_RULE_DRAFT_SAVE("NUMBERING_RULE_DRAFT_SAVE", "保存编码规则草稿"),
    NUMBERING_RULE_PUBLISH("NUMBERING_RULE_PUBLISH", "发布编码规则"),
    NUMBERING_RULE_DISABLE("NUMBERING_RULE_DISABLE", "停用编码规则"),
    ROUTING_RULE_DRAFT_SAVE("ROUTING_RULE_DRAFT_SAVE", "保存业务单据流程配置草稿"),
    ROUTING_RULE_PUBLISH("ROUTING_RULE_PUBLISH", "发布业务单据流程配置"),
    ROUTING_RULE_DISABLE("ROUTING_RULE_DISABLE", "停用业务单据流程配置"),
    PERMISSION_POLICY_DRAFT_SAVE("PERMISSION_POLICY_DRAFT_SAVE", "保存数据权限规则草稿"),
    PERMISSION_POLICY_PUBLISH("PERMISSION_POLICY_PUBLISH", "发布数据权限规则"),
    USER_SCOPE_SAVE("USER_SCOPE_SAVE", "保存用户合同域授权"),
    USER_SCOPE_DELETE("USER_SCOPE_DELETE", "删除用户合同域授权"),
    PARTY_MERGE("PARTY_MERGE", "合并相对方信息记录"),
    PARTY_IMPORT_UPLOAD("PARTY_IMPORT_UPLOAD", "上传相对方导入作业"),
    PARTY_IMPORT_CONFIRM("PARTY_IMPORT_CONFIRM", "确认相对方导入"),
    PARTY_IMPORT_RETRY("PARTY_IMPORT_RETRY", "重试相对方导入失败行"),
    HANDOVER_OPEN("HANDOVER_OPEN", "创建经办人变更事项"),
    HANDOVER_REASSIGN("HANDOVER_REASSIGN", "执行经办人变更"),
    INTEGRATION_SANDBOX_RUN("INTEGRATION_SANDBOX_RUN", "运行外部集成沙箱检查"),
    ONLINE_EDIT_OPEN("ONLINE_EDIT_OPEN", "打开在线编辑"),
    ONLINE_EDIT_SAVE("ONLINE_EDIT_SAVE", "保存在线编辑"),
    COLLABORATION_START("COLLABORATION_START", "发起法务协同"),
    COLLABORATION_COMMENT("COLLABORATION_COMMENT", "追加协同意见"),
    COLLABORATION_REQUEST_CHANGE("COLLABORATION_REQUEST_CHANGE", "法务要求修改"),
    COLLABORATION_COMPLETE("COLLABORATION_COMPLETE", "完成法务协同"),
    COLLABORATION_CANCEL("COLLABORATION_CANCEL", "取消法务协同");

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
