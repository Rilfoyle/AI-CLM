package cn.iocoder.yudao.module.clm.savedfilter;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("clm_saved_filter")
@KeySequence("clm_saved_filter_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SavedFilterDO extends TenantBaseDO {
    @TableId private Long id;
    private Long userId;
    private String sceneCode;
    private String name;
    private String filterJson;
    private Boolean defaultFlag;
}
