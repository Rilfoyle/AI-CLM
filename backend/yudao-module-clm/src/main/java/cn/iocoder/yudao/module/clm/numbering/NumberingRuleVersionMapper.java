package cn.iocoder.yudao.module.clm.numbering;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NumberingRuleVersionMapper extends BaseMapperX<NumberingRuleVersionDO> {
    default NumberingRuleVersionDO selectPublished(Long contractTypeId) {
        NumberingRuleVersionDO exact = selectOne(new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .eq(NumberingRuleVersionDO::getContractTypeId, contractTypeId)
                .eq(NumberingRuleVersionDO::getStatus, "PUBLISHED")
                .orderByDesc(NumberingRuleVersionDO::getVersionNo).last("LIMIT 1"));
        if (exact != null) return exact;
        return selectOne(new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .isNull(NumberingRuleVersionDO::getContractTypeId)
                .eq(NumberingRuleVersionDO::getStatus, "PUBLISHED")
                .orderByDesc(NumberingRuleVersionDO::getVersionNo).last("LIMIT 1"));
    }

    default PageResult<NumberingRuleVersionDO> selectPage(NumberingRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .likeIfPresent(NumberingRuleVersionDO::getRuleCode, reqVO.getRuleCode())
                .eqIfPresent(NumberingRuleVersionDO::getContractTypeId, reqVO.getContractTypeId())
                .eqIfPresent(NumberingRuleVersionDO::getStatus, reqVO.getStatus())
                .orderByDesc(NumberingRuleVersionDO::getUpdateTime)
                .orderByDesc(NumberingRuleVersionDO::getId));
    }

    default List<NumberingRuleVersionDO> selectListByRuleCode(String ruleCode) {
        return selectList(new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .eq(NumberingRuleVersionDO::getRuleCode, ruleCode)
                .orderByDesc(NumberingRuleVersionDO::getVersionNo));
    }

    default NumberingRuleVersionDO selectDraftByRuleCode(String ruleCode) {
        return selectOne(new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .eq(NumberingRuleVersionDO::getRuleCode, ruleCode)
                .eq(NumberingRuleVersionDO::getStatus, "DRAFT")
                .orderByDesc(NumberingRuleVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }

    default int selectMaxVersionNo(String ruleCode) {
        NumberingRuleVersionDO latest = selectOne(new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .eq(NumberingRuleVersionDO::getRuleCode, ruleCode)
                .orderByDesc(NumberingRuleVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 0 : latest.getVersionNo();
    }

    default List<NumberingRuleVersionDO> selectPublishedByScope(Long contractTypeId) {
        LambdaQueryWrapperX<NumberingRuleVersionDO> query = new LambdaQueryWrapperX<NumberingRuleVersionDO>()
                .eq(NumberingRuleVersionDO::getStatus, "PUBLISHED");
        if (contractTypeId == null) query.isNull(NumberingRuleVersionDO::getContractTypeId);
        else query.eq(NumberingRuleVersionDO::getContractTypeId, contractTypeId);
        return selectList(query);
    }

    @Select("SELECT * FROM clm_numbering_rule_version WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    NumberingRuleVersionDO selectByIdForUpdate(Long id);
}
