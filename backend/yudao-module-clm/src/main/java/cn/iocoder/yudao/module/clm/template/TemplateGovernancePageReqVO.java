package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TemplateGovernancePageReqVO extends PageParam {
    private String code;
    private String name;
    private Long contractTypeId;
    /** 0 停用，1 启用。 */
    private Integer status;
}
