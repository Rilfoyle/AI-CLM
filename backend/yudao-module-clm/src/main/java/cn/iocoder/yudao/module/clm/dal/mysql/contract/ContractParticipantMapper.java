package cn.iocoder.yudao.module.clm.dal.mysql.contract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CLM 合同参与人 Mapper
 */
@Mapper
public interface ContractParticipantMapper extends BaseMapperX<ContractParticipantDO> {

    default List<ContractParticipantDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<ContractParticipantDO>()
                .eq(ContractParticipantDO::getContractId, contractId)
                .orderByAsc(ContractParticipantDO::getId));
    }

    default ContractParticipantDO selectByContractIdAndUserId(Long contractId, Long userId) {
        return selectFirstOne(ContractParticipantDO::getContractId, contractId,
                ContractParticipantDO::getPrincipalType, ContractParticipantDO.PRINCIPAL_TYPE_USER,
                ContractParticipantDO::getPrincipalId, userId);
    }

    default void deleteByContractId(Long contractId) {
        delete(new LambdaQueryWrapperX<ContractParticipantDO>().eq(ContractParticipantDO::getContractId, contractId));
    }

}
