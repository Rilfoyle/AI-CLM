package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TemplateMapper extends BaseMapperX<TemplateDO> {
    default PageResult<TemplateDO> selectPublishedPage(TemplatePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TemplateDO>()
                .likeIfPresent(TemplateDO::getName, reqVO.getName())
                .eqIfPresent(TemplateDO::getContractTypeId, reqVO.getContractTypeId())
                .eq(TemplateDO::getStatus, 1)
                .isNotNull(TemplateDO::getCurrentVersionId)
                .orderByDesc(TemplateDO::getId));
    }

    default PageResult<TemplateDO> selectGovernancePage(TemplateGovernancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TemplateDO>()
                .likeIfPresent(TemplateDO::getCode, reqVO.getCode())
                .likeIfPresent(TemplateDO::getName, reqVO.getName())
                .eqIfPresent(TemplateDO::getContractTypeId, reqVO.getContractTypeId())
                .eqIfPresent(TemplateDO::getStatus, reqVO.getStatus())
                .orderByDesc(TemplateDO::getUpdateTime)
                .orderByDesc(TemplateDO::getId));
    }

    default TemplateDO selectByCode(String code) {
        return selectOne(TemplateDO::getCode, code);
    }

    @Select("SELECT * FROM clm_template WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    TemplateDO selectByIdForUpdate(Long id);
}
