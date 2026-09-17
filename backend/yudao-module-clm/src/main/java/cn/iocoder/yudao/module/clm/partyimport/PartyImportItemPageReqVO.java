package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartyImportItemPageReqVO extends PageParam {
    @NotNull(message = "导入任务编号不能为空")
    private Long jobId;
    private String status;
}
