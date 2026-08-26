package cn.iocoder.yudao.module.clm.workflow;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import cn.iocoder.yudao.module.clm.dal.mysql.workflow.WorkflowBindingMapper;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * CLM 合同审批流程实例状态监听器
 *
 * 不限定单一流程定义 KEY：只要 businessKey 能解析为 binding 且 binding.processInstanceId == event.id 即处理。
 * 不继承 {@link cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener}（该基类按单一 key 过滤）。
 */
@Component
@Slf4j
public class ClmProcessInstanceStatusListener implements ApplicationListener<BpmProcessInstanceStatusEvent> {

    @Resource
    private WorkflowBindingMapper workflowBindingMapper;
    @Resource
    private ContractWorkflowService contractWorkflowService;

    @Override
    public void onApplicationEvent(BpmProcessInstanceStatusEvent event) {
        Long bindingId = tryParseLong(event.getBusinessKey());
        if (bindingId == null) {
            return;
        }
        // 忽略租户上下文查询 binding，再按 binding.tenantId 执行后续逻辑
        WorkflowBindingDO binding = TenantUtils.executeIgnore(() -> workflowBindingMapper.selectById(bindingId));
        if (binding == null || !Objects.equals(binding.getProcessInstanceId(), event.getId())) {
            return;
        }
        log.info("[onApplicationEvent][binding({}) processInstance({}) status({}) reason({})]",
                binding.getId(), event.getId(), event.getStatus(), event.getReason());
        contractWorkflowService.handleProcessResult(binding, event.getStatus(), event.getReason());
    }

    private static Long tryParseLong(String value) {
        if (StrUtil.isBlank(value) || !NumberUtil.isLong(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
