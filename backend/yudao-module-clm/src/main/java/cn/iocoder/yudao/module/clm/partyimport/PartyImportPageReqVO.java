package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PartyImportPageReqVO extends PageParam {
    private String fileName;
    private String status;
}
