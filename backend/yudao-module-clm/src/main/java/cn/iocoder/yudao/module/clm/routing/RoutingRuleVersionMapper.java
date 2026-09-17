package cn.iocoder.yudao.module.clm.routing;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoutingRuleVersionMapper extends BaseMapperX<RoutingRuleVersionDO> {
    default List<RoutingRuleVersionDO> selectPublishedCandidates(Long contractTypeId) {
        return selectList(new LambdaQueryWrapperX<RoutingRuleVersionDO>()
                .and(w -> w.eq(RoutingRuleVersionDO::getContractTypeId, contractTypeId)
                        .or().isNull(RoutingRuleVersionDO::getContractTypeId))
                .eq(RoutingRuleVersionDO::getStatus, "PUBLISHED")
                .orderByAsc(RoutingRuleVersionDO::getPriority).orderByDesc(RoutingRuleVersionDO::getVersionNo));
    }

    default PageResult<RoutingRuleVersionDO> selectPage(RoutingRulePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RoutingRuleVersionDO>()
                .likeIfPresent(RoutingRuleVersionDO::getRuleCode, reqVO.getRuleCode())
                .likeIfPresent(RoutingRuleVersionDO::getName, reqVO.getName())
                .eqIfPresent(RoutingRuleVersionDO::getContractTypeId, reqVO.getContractTypeId())
                .eqIfPresent(RoutingRuleVersionDO::getStatus, reqVO.getStatus())
                .orderByDesc(RoutingRuleVersionDO::getUpdateTime)
                .orderByDesc(RoutingRuleVersionDO::getId));
    }

    default List<RoutingRuleVersionDO> selectListByRuleCode(String ruleCode) {
        return selectList(new LambdaQueryWrapperX<RoutingRuleVersionDO>()
                .eq(RoutingRuleVersionDO::getRuleCode, ruleCode)
                .orderByDesc(RoutingRuleVersionDO::getVersionNo));
    }

    default int selectMaxVersionNo(String ruleCode) {
        RoutingRuleVersionDO latest = selectOne(new LambdaQueryWrapperX<RoutingRuleVersionDO>()
                .eq(RoutingRuleVersionDO::getRuleCode, ruleCode)
                .orderByDesc(RoutingRuleVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 0 : latest.getVersionNo();
    }

    @Select("SELECT * FROM clm_routing_rule_version WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    RoutingRuleVersionDO selectByIdForUpdate(Long id);
}
