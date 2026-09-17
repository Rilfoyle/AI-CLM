package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HandoverPageReqVO extends PageParam {
    private Long sourceUserId;
    private String status;
}
