package cn.iocoder.yudao.module.clm.revision;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RevisionChangeRespVO {
    private String field;
    private String fieldLabel;
    private Object before;
    private Object after;
}
