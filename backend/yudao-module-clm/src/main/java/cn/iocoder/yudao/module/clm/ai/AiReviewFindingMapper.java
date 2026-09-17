package cn.iocoder.yudao.module.clm.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiReviewFindingMapper extends BaseMapperX<AiReviewFindingDO> {
    default List<AiReviewFindingDO> selectListByRunId(Long runId) {
        return selectList(new LambdaQueryWrapperX<AiReviewFindingDO>()
                .eq(AiReviewFindingDO::getRunId, runId).orderByAsc(AiReviewFindingDO::getId));
    }
}
