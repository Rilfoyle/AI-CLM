package cn.iocoder.yudao.module.clm.dal.dataobject.contracttype;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CLM 合同类型版本 DO
 */
@TableName(value = "clm_contract_type_version", autoResultMap = true)
@KeySequence("clm_contract_type_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContractTypeVersionDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 合同类型编号
     */
    private Long typeId;
    /**
     * 版本号，从 1 开始递增
     */
    private Integer versionNo;
    /**
     * 状态：0 草稿 1 已发布
     *
     * 枚举 {@link cn.iocoder.yudao.module.clm.enums.contracttype.ClmTypeVersionStatusEnum}
     */
    private Integer status;
    /**
     * FormCreate 表单配置（option）JSON 字符串
     */
    private String formConf;
    /**
     * FormCreate 字段规则数组，每项为 rule 的 JSON 串
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> formFields;
    /**
     * 审批使用的 BPM 流程定义 KEY
     */
    private String processDefinitionKey;
    /**
     * 版本说明
     */
    private String remark;
    /**
     * 发布时间
     */
    private LocalDateTime publishedTime;

}
