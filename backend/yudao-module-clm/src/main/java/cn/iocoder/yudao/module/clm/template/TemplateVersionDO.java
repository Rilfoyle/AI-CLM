package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@TableName("clm_template_version")
@KeySequence("clm_template_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TemplateVersionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long templateId;
    private Integer versionNo;
    private String status;
    private String fileKey;
    private String fileName;
    private String mimeType;
    private Long fileSize;
    private String checksumSha256;
    private LocalDateTime publishedTime;
    private String remark;
}
