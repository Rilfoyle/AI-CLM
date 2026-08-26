package cn.iocoder.yudao.module.clm.controller.admin.workflow.vo;

import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.document.vo.DocumentVersionRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - CLM 流程绑定详情 Response VO（BPM 业务表单用）")
@Data
public class WorkflowBindingDetailRespVO {

    @Schema(description = "流程绑定", requiredMode = Schema.RequiredMode.REQUIRED)
    private WorkflowBindingRespVO binding;

    @Schema(description = "合同（含当前用户权限）", requiredMode = Schema.RequiredMode.REQUIRED)
    private ContractRespVO contract;

    @Schema(description = "提交时的表单快照")
    private Map<String, Object> formSnapshot;

    @Schema(description = "绑定的正文版本")
    private DocumentVersionRespVO documentVersion;

    @Schema(description = "合同类型版本的 FormCreate 表单配置")
    private String formConf;

    @Schema(description = "合同类型版本的 FormCreate 字段规则")
    private List<String> formFields;

}
