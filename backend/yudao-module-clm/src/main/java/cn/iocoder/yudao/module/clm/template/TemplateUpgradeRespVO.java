package cn.iocoder.yudao.module.clm.template;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TemplateUpgradeRespVO {
    private Long contractId;
    private Long revisionId;
    private Long documentVersionId;
    private Long templateVersionId;
}
