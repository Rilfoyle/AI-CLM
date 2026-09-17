package cn.iocoder.yudao.module.clm.controller.admin.processdesign.vo;

import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CLM 流程定义详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessDesignDetailRespVO extends BpmModelRespVO {

    @Schema(description = "Flowable 模型版本；保存时作为 expectedModelVersion 回传", example = "3")
    private Integer modelVersion;

}
