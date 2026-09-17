package cn.iocoder.yudao.module.clm.partyimport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_party_import_job")
@KeySequence("clm_party_import_job_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PartyImportJobDO extends TenantBaseDO {
    @TableId private Long id;
    private String jobKey;
    private String fileName;
    private String status;
    private String mappingJson;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private LocalDateTime finishedTime;
}
