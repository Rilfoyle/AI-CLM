package cn.iocoder.yudao.module.clm.dal.mysql.workflow;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CLM 流程绑定 Mapper
 */
@Mapper
public interface WorkflowBindingMapper extends BaseMapperX<WorkflowBindingDO> {

    default List<WorkflowBindingDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<WorkflowBindingDO>()
                .eq(WorkflowBindingDO::getContractId, contractId)
                .orderByDesc(WorkflowBindingDO::getId));
    }

}
