package cn.iocoder.yudao.module.clm.revision;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ContractRevisionMapper extends BaseMapperX<ContractRevisionDO> {

    default List<ContractRevisionDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<ContractRevisionDO>()
                .eq(ContractRevisionDO::getContractId, contractId)
                .orderByDesc(ContractRevisionDO::getRevisionNo));
    }

    default ContractRevisionDO selectLatest(Long contractId) {
        return selectOne(new LambdaQueryWrapperX<ContractRevisionDO>()
                .eq(ContractRevisionDO::getContractId, contractId)
                .orderByDesc(ContractRevisionDO::getRevisionNo)
                .last("LIMIT 1"));
    }
}
