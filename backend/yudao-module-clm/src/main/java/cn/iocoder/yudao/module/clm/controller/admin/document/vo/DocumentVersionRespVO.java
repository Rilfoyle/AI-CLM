package cn.iocoder.yudao.module.clm.controller.admin.document.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 文档版本 Response VO")
@Data
public class DocumentVersionRespVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "文档编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long documentId;

    @Schema(description = "合同编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long contractId;

    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer versionNo;

    @Schema(description = "父版本编号", example = "0")
    private Long parentVersionId;

    @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED, example = "合同.docx")
    private String fileName;

    @Schema(description = "MIME 类型", example = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    private String mimeType;

    @Schema(description = "文件大小（字节）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long fileSize;

    @Schema(description = "SHA-256 校验和", requiredMode = Schema.RequiredMode.REQUIRED)
    private String checksumSha256;

    @Schema(description = "来源：UPLOAD / ONLINE_EDIT / MANUAL_FINAL", requiredMode = Schema.RequiredMode.REQUIRED, example = "UPLOAD")
    private String sourceType;

    @Schema(description = "是否冻结", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean frozen;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "上传人编号", example = "1")
    private String creator;

    @Schema(description = "上传人名称", example = "芋道")
    private String creatorName;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
