package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NumberSequenceMapper extends BaseMapperX<NumberSequenceDO> {
    @Select("SELECT * FROM clm_number_sequence WHERE rule_version_id = #{ruleVersionId} " +
            "AND period_key = #{periodKey} AND deleted = FALSE FOR UPDATE")
    NumberSequenceDO selectForUpdate(Long ruleVersionId, String periodKey);
}
