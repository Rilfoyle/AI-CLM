package cn.iocoder.yudao.module.clm.integration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IntegrationRunMapper extends BaseMapperX<IntegrationRunDO> {
    default IntegrationRunDO selectByTypeAndKey(String type, String key) {
        return selectOne(new LambdaQueryWrapperX<IntegrationRunDO>()
                .eq(IntegrationRunDO::getIntegrationType, type).eq(IntegrationRunDO::getRunKey, key));
    }

    default PageResult<IntegrationRunDO> selectPage(PageParam pageParam, String type) {
        return selectPage(pageParam, new LambdaQueryWrapperX<IntegrationRunDO>()
                .eqIfPresent(IntegrationRunDO::getIntegrationType, type).orderByDesc(IntegrationRunDO::getId));
    }
}
