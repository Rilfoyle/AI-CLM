package cn.iocoder.yudao.module.clm.reconciliation;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReconciliationRunMapper extends BaseMapperX<ReconciliationRunDO> {

    @Select("SELECT * FROM clm_reconciliation_run WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    ReconciliationRunDO selectByIdForUpdate(@Param("id") Long id);

    default ReconciliationRunDO selectByRunKey(String runKey) {
        return selectOne(ReconciliationRunDO::getRunKey, runKey);
    }

    default PageResult<ReconciliationRunDO> selectPage(PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<ReconciliationRunDO>()
                .orderByDesc(ReconciliationRunDO::getId));
    }
}
