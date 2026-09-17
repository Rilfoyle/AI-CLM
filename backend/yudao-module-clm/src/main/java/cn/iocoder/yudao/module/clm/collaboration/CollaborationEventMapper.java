package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollaborationEventMapper extends BaseMapperX<CollaborationEventDO> {

    default List<CollaborationEventDO> selectListByCaseId(Long caseId) {
        return selectList(new LambdaQueryWrapperX<CollaborationEventDO>()
                .eq(CollaborationEventDO::getCaseId, caseId)
                .orderByAsc(CollaborationEventDO::getId));
    }

    default long countHandledEvents(Long caseId, Long legalUserId) {
        return selectCount(new LambdaQueryWrapperX<CollaborationEventDO>()
                .eq(CollaborationEventDO::getCaseId, caseId)
                .eq(CollaborationEventDO::getActorUserId, legalUserId)
                .ne(CollaborationEventDO::getEventType, CollaborationEventDO.TYPE_START));
    }

}
