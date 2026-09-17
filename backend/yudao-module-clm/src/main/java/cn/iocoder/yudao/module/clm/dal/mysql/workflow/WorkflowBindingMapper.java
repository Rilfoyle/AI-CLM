package cn.iocoder.yudao.module.clm.dal.mysql.workflow;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.workflow.WorkflowBindingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    default WorkflowBindingDO selectByProcessInstanceId(String processInstanceId) {
        return selectOne(WorkflowBindingDO::getProcessInstanceId, processInstanceId);
    }

    default WorkflowBindingDO selectBySubmitRequestId(Long contractId, String submitRequestId) {
        return selectOne(new LambdaQueryWrapperX<WorkflowBindingDO>()
                .eq(WorkflowBindingDO::getContractId, contractId)
                .eq(WorkflowBindingDO::getSubmitRequestId, submitRequestId));
    }

    @Select("SELECT COUNT(*) > 0 FROM clm_workflow_binding wb "
            + "JOIN bpm_process_instance_copy copy_record "
            + "ON copy_record.process_instance_id = wb.process_instance_id "
            + "WHERE wb.contract_id = #{contractId} AND copy_record.user_id = #{userId} "
            + "AND wb.purpose = 'APPROVAL' AND wb.deleted = FALSE")
    boolean hasCopyRelation(@Param("contractId") Long contractId, @Param("userId") Long userId);

    @Select("SELECT DISTINCT wb.* FROM clm_workflow_binding wb "
            + "JOIN bpm_process_instance_copy copy_record "
            + "ON copy_record.process_instance_id = wb.process_instance_id "
            + "WHERE copy_record.user_id = #{userId} AND wb.purpose = 'APPROVAL' "
            + "AND wb.deleted = FALSE ORDER BY wb.id DESC")
    List<WorkflowBindingDO> selectListCopiedByUser(@Param("userId") Long userId);

}
