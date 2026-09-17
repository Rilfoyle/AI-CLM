package cn.iocoder.yudao.module.clm.processdesign;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CLM 流程定义列表项 Response VO")
@Data
public class ProcessDesignListItemRespVO {

    @Schema(description = "Flowable 模型编号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "流程编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @Schema(description = "流程名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "流程描述")
    private String description;

    @Schema(description = "已发布流程版本")
    private Integer deployedVersion;

    @Schema(description = "是否已发布", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean deployed;

    @Schema(description = "最后更新时间")
    private LocalDateTime updateTime;

}
