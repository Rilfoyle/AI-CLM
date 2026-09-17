package cn.iocoder.yudao.module.clm.controller.admin.processdesign.vo;

import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.BpmModelSaveReqVO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CLM 流程定义保存 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessDesignSaveReqVO extends BpmModelSaveReqVO {

    @Schema(description = "读取流程定义时的 Flowable 模型版本；更新时用于防止并发覆盖", example = "3")
    @Positive(message = "模型版本必须大于 0")
    private Integer expectedModelVersion;

}
