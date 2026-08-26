package cn.iocoder.yudao.module.clm.dal.mysql.contracttype;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.contracttype.ContractTypeVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * CLM 合同类型版本 Mapper
 */
@Mapper
public interface ContractTypeVersionMapper extends BaseMapperX<ContractTypeVersionDO> {

    default List<ContractTypeVersionDO> selectListByTypeId(Long typeId) {
        return selectList(new LambdaQueryWrapperX<ContractTypeVersionDO>()
                .eq(ContractTypeVersionDO::getTypeId, typeId)
                .orderByDesc(ContractTypeVersionDO::getVersionNo));
    }

    default List<ContractTypeVersionDO> selectListByTypeIds(Collection<Long> typeIds) {
        return selectList(ContractTypeVersionDO::getTypeId, typeIds);
    }

    default ContractTypeVersionDO selectByTypeIdAndStatus(Long typeId, Integer status) {
        return selectOne(ContractTypeVersionDO::getTypeId, typeId, ContractTypeVersionDO::getStatus, status);
    }

    default ContractTypeVersionDO selectLatestByTypeId(Long typeId) {
        return CollUtil.getFirst(selectList(new LambdaQueryWrapperX<ContractTypeVersionDO>()
                .eq(ContractTypeVersionDO::getTypeId, typeId)
                .orderByDesc(ContractTypeVersionDO::getVersionNo)));
    }

}
