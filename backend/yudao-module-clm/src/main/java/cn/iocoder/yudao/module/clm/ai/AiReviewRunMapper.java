package cn.iocoder.yudao.module.clm.ai;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AiReviewRunMapper extends BaseMapperX<AiReviewRunDO> {
    default List<AiReviewRunDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<AiReviewRunDO>()
                .eq(AiReviewRunDO::getContractId, contractId).orderByDesc(AiReviewRunDO::getId));
    }
}
