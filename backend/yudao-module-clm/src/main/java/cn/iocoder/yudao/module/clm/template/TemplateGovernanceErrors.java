package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface TemplateGovernanceErrors {
    ErrorCode VERSION_NOT_EXISTS = new ErrorCode(1_070_010_002, "合同范本版本不存在");
    ErrorCode VERSION_NOT_DRAFT = new ErrorCode(1_070_010_003, "只有草稿范本版本可以修改或发布");
    ErrorCode CODE_DUPLICATE = new ErrorCode(1_070_010_004, "范本编码【{}】已存在");
    ErrorCode CODE_IMMUTABLE = new ErrorCode(1_070_010_005, "范本编码创建后不可修改");
    ErrorCode FILE_REQUIRED = new ErrorCode(1_070_010_006, "新建范本版本必须上传文件或基于当前发布版本创建");
    ErrorCode VERSION_MISMATCH = new ErrorCode(1_070_010_007, "范本版本不属于指定范本");
    ErrorCode SCOPE_IMMUTABLE = new ErrorCode(1_070_010_008, "已发布范本的适用合同类型不可修改，请新建范本");
    ErrorCode UPGRADE_NOT_TEMPLATE_DRAFT = new ErrorCode(1_070_010_009, "只有由范本起草且当前可编辑的合同可以升级范本");
    ErrorCode UPGRADE_SOURCE_NOT_EXISTS = new ErrorCode(1_070_010_010, "合同缺少来源范本版本，无法升级");
    ErrorCode UPGRADE_TARGET_INVALID = new ErrorCode(1_070_010_011, "目标必须是同一范本当前已发布的新版本");
}
