package cn.iocoder.yudao.module.clm.integration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationDeliveryMapper extends BaseMapperX<IntegrationDeliveryDO> {
    default IntegrationDeliveryDO selectByKey(String key) {
        return selectOne(IntegrationDeliveryDO::getDeliveryKey, key);
    }

    default PageResult<IntegrationDeliveryDO> selectPage(PageParam pageParam, String status) {
        return selectPage(pageParam, new LambdaQueryWrapperX<IntegrationDeliveryDO>()
                .eqIfPresent(IntegrationDeliveryDO::getStatus, status).orderByDesc(IntegrationDeliveryDO::getId));
    }
}
