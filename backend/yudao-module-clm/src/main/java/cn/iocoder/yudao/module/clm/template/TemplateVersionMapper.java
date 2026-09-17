package cn.iocoder.yudao.module.clm.template;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TemplateVersionMapper extends BaseMapperX<TemplateVersionDO> {

    default List<TemplateVersionDO> selectListByTemplateId(Long templateId) {
        return selectList(new LambdaQueryWrapperX<TemplateVersionDO>()
                .eq(TemplateVersionDO::getTemplateId, templateId)
                .orderByDesc(TemplateVersionDO::getVersionNo));
    }

    default TemplateVersionDO selectDraftByTemplateId(Long templateId) {
        return selectOne(new LambdaQueryWrapperX<TemplateVersionDO>()
                .eq(TemplateVersionDO::getTemplateId, templateId)
                .eq(TemplateVersionDO::getStatus, "DRAFT")
                .orderByDesc(TemplateVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }

    default Integer selectMaxVersionNo(Long templateId) {
        TemplateVersionDO latest = selectOne(new LambdaQueryWrapperX<TemplateVersionDO>()
                .eq(TemplateVersionDO::getTemplateId, templateId)
                .orderByDesc(TemplateVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 0 : latest.getVersionNo();
    }

    @Select("SELECT * FROM clm_template_version WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    TemplateVersionDO selectByIdForUpdate(Long id);
}
