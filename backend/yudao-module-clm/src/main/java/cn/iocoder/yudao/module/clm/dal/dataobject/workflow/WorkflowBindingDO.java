package cn.iocoder.yudao.module.clm.dal.dataobject.workflow;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * CLM 流程绑定 DO（一次流程一个 binding；businessKey = binding.id）
 */
@TableName("clm_workflow_binding")
@KeySequence("clm_workflow_binding_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class WorkflowBindingDO extends TenantBaseDO {

    /**
     * 用途 - 合同审批
     */
    public static final String PURPOSE_APPROVAL = "APPROVAL";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 合同编号
     */
    private Long contractId;
    /**
     * 用途：APPROVAL 合同审批
     */
    private String purpose;
    /**
     * 流程定义 KEY
     */
    private String processDefinitionKey;
    /**
     * 流程定义编号
     */
    private String processDefinitionId;
    /**
     * 流程实例编号
     */
    private String processInstanceId;
    /**
     * 绑定的正文版本编号
     */
    private Long documentVersionId;
    /**
     * 绑定的合同类型版本编号
     */
    private Long contractTypeVersionId;
    /**
     * 提交时的表单快照 JSON
     */
    private String formSnapshot;
    /**
     * 绑定版本的 SHA-256
     */
    private String checksumSha256;
    /**
     * 状态
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.workflow.ClmWorkflowBindingStatusEnum}
     */
    private Integer status;
    /**
     * 审批结果说明
     */
    private String resultReason;
    /**
     * 结束时间
     */
    private LocalDateTime finishedTime;

}
