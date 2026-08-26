package cn.iocoder.yudao.module.clm.controller.admin.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CLM 合同文档 Response VO")
@Data
public class DocumentRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long contractId;

    @Schema(description = "文档角色：MAIN 正文 / ATTACHMENT 附件", requiredMode = Schema.RequiredMode.REQUIRED, example = "MAIN")
    private String roleCode;

    @Schema(description = "文档名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "合同正文")
    private String name;

    @Schema(description = "当前版本编号", example = "1")
    private Long currentVersionId;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "版本列表（按版本号降序）")
    private List<DocumentVersionRespVO> versions;

}
