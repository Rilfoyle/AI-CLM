package cn.iocoder.yudao.module.clm.service.workflow;

import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractApprovalPreviewRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractSubmitReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingDetailRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingRespVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * CLM 合同审批流程 Service 接口
 */
public interface ContractWorkflowService {

    /**
     * 提交审批：创建 binding、冻结版本、发起 BPM 流程
     *
     * @return bindingId
     */
    Long submit(@Valid ContractSubmitReqVO reqVO);

    /**
     * 处理 BPM 流程结果（由监听器调用）
     *
     * @param binding 绑定
     * @param status  BPM 流程实例状态（1/2/3/4）
     * @param reason  原因
     */
    void handleProcessResult(WorkflowBindingDO binding, Integer status, String reason);

    /**
     * 审批预览：类型版本的流程定义 KEY 及其激活的流程定义
     */
    ContractApprovalPreviewRespVO getApprovalPreview(Long contractId);

    /**
     * 合同的绑定列表（按 id 降序）；要求合同可见
     */
    List<WorkflowBindingRespVO> getBindingList(Long contractId);

    /**
     * 绑定详情（BPM 业务表单用）；合同可见或流程参与人
     */
    WorkflowBindingDetailRespVO getBindingDetail(Long id);

    WorkflowBindingDO getBinding(Long id);

}
