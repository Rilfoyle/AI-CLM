package cn.iocoder.yudao.module.clm.dal.dataobject.contracttype;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * CLM 合同类型 DO
 */
@TableName("clm_contract_type")
@KeySequence("clm_contract_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContractTypeDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 类型编码（租户内唯一）
     */
    private String code;
    /**
     * 类型名称
     */
    private String name;
    /**
     * 描述
     */
    private String description;
    /**
     * 状态：0 开启 1 关闭
     *
     * 枚举 {@link cn.iocoder.yudao.framework.common.enums.CommonStatusEnum}
     */
    private Integer status;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 当前发布版本编号（clm_contract_type_version.id），为空表示尚未发布
     */
    private Long currentVersionId;
    /**
     * 范本文件存储键（ClmDocumentStorage），为空表示未配置范本
     */
    private String templateFileKey;
    /**
     * 范本文件名
     */
    private String templateFileName;

}
