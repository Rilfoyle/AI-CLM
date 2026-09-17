package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TemplatePageReqVO extends PageParam {
    private String name;
    private Long contractTypeId;
}
