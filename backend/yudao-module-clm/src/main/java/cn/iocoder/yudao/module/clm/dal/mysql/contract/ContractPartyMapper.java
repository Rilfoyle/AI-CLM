package cn.iocoder.yudao.module.clm.dal.mysql.contract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * CLM 合同签约方 Mapper
 */
@Mapper
public interface ContractPartyMapper extends BaseMapperX<ContractPartyDO> {

    default List<ContractPartyDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<ContractPartyDO>()
                .eq(ContractPartyDO::getContractId, contractId)
                .orderByAsc(ContractPartyDO::getSort)
                .orderByAsc(ContractPartyDO::getId));
    }

    default List<ContractPartyDO> selectListByContractIds(Collection<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Collections.emptyList();
        }
        return selectList(new LambdaQueryWrapperX<ContractPartyDO>()
                .in(ContractPartyDO::getContractId, contractIds)
                .orderByAsc(ContractPartyDO::getContractId)
                .orderByAsc(ContractPartyDO::getSort)
                .orderByAsc(ContractPartyDO::getId));
    }

    default Long selectCountByPartyId(Long partyId) {
        return selectCount(ContractPartyDO::getPartyId, partyId);
    }

    default void deleteByContractId(Long contractId) {
        delete(new LambdaQueryWrapperX<ContractPartyDO>().eq(ContractPartyDO::getContractId, contractId));
    }

}
