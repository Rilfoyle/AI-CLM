package cn.iocoder.yudao.module.clm.controller.admin.workflow;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingDetailRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workflow.vo.WorkflowBindingRespVO;
import cn.iocoder.yudao.module.clm.service.workflow.ContractWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CLM 流程绑定")
@RestController
@RequestMapping("/clm/workflow-binding")
@Validated
public class WorkflowBindingController {

    @Resource
    private ContractWorkflowService contractWorkflowService;

    @GetMapping("/list")
    @Operation(summary = "获得合同的流程绑定列表（按 id 降序）")
    @Parameter(name = "contractId", description = "合同编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<List<WorkflowBindingRespVO>> getBindingList(@RequestParam("contractId") Long contractId) {
        return success(contractWorkflowService.getBindingList(contractId));
    }

    @GetMapping("/get")
    @Operation(summary = "获得流程绑定详情（BPM 业务表单用）")
    @Parameter(name = "id", description = "绑定编号", required = true)
    @PreAuthorize("@ss.hasPermission('clm:contract:query')")
    public CommonResult<WorkflowBindingDetailRespVO> getBindingDetail(@RequestParam("id") Long id) {
        return success(contractWorkflowService.getBindingDetail(id));
    }

}
