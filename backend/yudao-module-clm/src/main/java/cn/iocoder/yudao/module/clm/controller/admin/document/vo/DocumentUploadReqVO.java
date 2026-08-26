package cn.iocoder.yudao.module.clm.controller.admin.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "管理后台 - CLM 合同文档上传 Request VO")
@Data
public class DocumentUploadReqVO {

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "合同编号不能为空")
    private Long contractId;

    @Schema(description = "文件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    @Schema(description = "文档角色：MAIN 正文 / ATTACHMENT 附件，默认 MAIN", example = "MAIN")
    private String roleCode;

    @Schema(description = "备注")
    private String remark;

}
