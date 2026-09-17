package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApprovalTaskRevisionBindingMapper extends BaseMapperX<ApprovalTaskRevisionBindingDO> {
    default ApprovalTaskRevisionBindingDO selectByTaskId(String taskId) {
        return selectOne(ApprovalTaskRevisionBindingDO::getTaskId, taskId);
    }
    default List<ApprovalTaskRevisionBindingDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<ApprovalTaskRevisionBindingDO>()
                .eq(ApprovalTaskRevisionBindingDO::getContractId, contractId).orderByAsc(ApprovalTaskRevisionBindingDO::getId));
    }
    default List<ApprovalTaskRevisionBindingDO> selectListByActor(Long userId) {
        return selectList(new LambdaQueryWrapperX<ApprovalTaskRevisionBindingDO>()
                .eq(ApprovalTaskRevisionBindingDO::getCreator, String.valueOf(userId))
                .orderByDesc(ApprovalTaskRevisionBindingDO::getId));
    }
}
