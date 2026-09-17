package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HandoverItemMapper extends BaseMapperX<HandoverItemDO> {

    @Select("SELECT * FROM clm_handover_item WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    HandoverItemDO selectByIdForUpdate(@Param("id") Long id);

    default List<HandoverItemDO> selectListByCaseId(Long caseId) {
        return selectList(new LambdaQueryWrapperX<HandoverItemDO>()
                .eq(HandoverItemDO::getCaseId, caseId).orderByAsc(HandoverItemDO::getId));
    }

    default HandoverItemDO selectContractItem(Long caseId, Long contractId) {
        return selectOne(new LambdaQueryWrapperX<HandoverItemDO>()
                .eq(HandoverItemDO::getCaseId, caseId)
                .eq(HandoverItemDO::getItemType, HandoverItemDO.TYPE_CONTRACT)
                .eq(HandoverItemDO::getContractId, contractId).last("LIMIT 1"));
    }

    default HandoverItemDO selectTaskItem(Long caseId, String taskId) {
        return selectOne(new LambdaQueryWrapperX<HandoverItemDO>()
                .eq(HandoverItemDO::getCaseId, caseId)
                .eq(HandoverItemDO::getItemType, HandoverItemDO.TYPE_ACTIVE_TASK)
                .eq(HandoverItemDO::getTaskId, taskId).last("LIMIT 1"));
    }

    default long countOutstanding(Long caseId) {
        return selectCount(new LambdaQueryWrapperX<HandoverItemDO>()
                .eq(HandoverItemDO::getCaseId, caseId)
                .in(HandoverItemDO::getStatus,
                        List.of(HandoverItemDO.STATUS_PENDING, HandoverItemDO.STATUS_FAILED)));
    }
}
