package cn.iocoder.yudao.module.clm.governance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GovernanceIssueMapper extends BaseMapperX<GovernanceIssueDO> {

    default PageResult<GovernanceIssueDO> selectPage(GovernanceIssuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<GovernanceIssueDO>()
                .eqIfPresent(GovernanceIssueDO::getIssueType, reqVO.getIssueType())
                .eqIfPresent(GovernanceIssueDO::getStatus, reqVO.getStatus())
                .eqIfPresent(GovernanceIssueDO::getContractId, reqVO.getContractId())
                .eqIfPresent(GovernanceIssueDO::getOwnerUserId, reqVO.getOwnerUserId())
                .orderByDesc(GovernanceIssueDO::getId));
    }

    default GovernanceIssueDO selectOpenBySource(String issueType, String sourceRef) {
        return selectOne(new LambdaQueryWrapperX<GovernanceIssueDO>()
                .eq(GovernanceIssueDO::getIssueType, issueType)
                .eq(GovernanceIssueDO::getSourceRef, sourceRef)
                .eq(GovernanceIssueDO::getStatus, GovernanceIssueDO.STATUS_OPEN)
                .last("LIMIT 1"));
    }

    default long countOpen(Long ownerUserId) {
        return selectCount(new LambdaQueryWrapperX<GovernanceIssueDO>()
                .eq(GovernanceIssueDO::getStatus, GovernanceIssueDO.STATUS_OPEN)
                .eqIfPresent(GovernanceIssueDO::getOwnerUserId, ownerUserId));
    }
}
