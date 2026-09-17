package cn.iocoder.yudao.module.clm.commitment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommitmentMapper extends BaseMapperX<CommitmentDO> {
    default List<CommitmentDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<CommitmentDO>()
                .eq(CommitmentDO::getContractId, contractId).orderByAsc(CommitmentDO::getId));
    }
    default Long selectCountByContractId(Long contractId) {
        return selectCount(CommitmentDO::getContractId, contractId);
    }
}
