package cn.iocoder.yudao.module.clm.controller.admin.workbench.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 统一工作项 Response VO")
@Data
public class ClmWorkbenchItemRespVO {

    private String id;
    private String type;
    private Long contractId;
    private String contractName;
    private String contractNo;
    private Long revisionId;
    private String taskId;
    private String status;
    private String handlerName;
    private Long waitingMinutes;
    private LocalDateTime updatedTime;

}
