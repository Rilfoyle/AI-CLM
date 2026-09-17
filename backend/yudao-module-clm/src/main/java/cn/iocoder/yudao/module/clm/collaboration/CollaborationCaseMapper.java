package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CollaborationCaseMapper extends BaseMapperX<CollaborationCaseDO> {

    default CollaborationCaseDO selectActiveByContractId(Long contractId) {
        return selectOne(new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eq(CollaborationCaseDO::getContractId, contractId)
                .in(CollaborationCaseDO::getStatus,
                        CollaborationCaseDO.STATUS_RUNNING, CollaborationCaseDO.STATUS_CHANGE_REQUESTED)
                .orderByDesc(CollaborationCaseDO::getId)
                .last("LIMIT 1"));
    }

    default boolean hasUserRelation(Long contractId, Long userId) {
        return selectCount(new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eq(CollaborationCaseDO::getContractId, contractId)
                .and(query -> query.eq(CollaborationCaseDO::getInitiatorUserId, userId)
                        .or().eq(CollaborationCaseDO::getLegalUserId, userId))) > 0;
    }

    default boolean hasActiveLegalAssignment(Long contractId, Long userId) {
        return selectCount(new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eq(CollaborationCaseDO::getContractId, contractId)
                .eq(CollaborationCaseDO::getLegalUserId, userId)
                .in(CollaborationCaseDO::getStatus,
                        CollaborationCaseDO.STATUS_RUNNING, CollaborationCaseDO.STATUS_CHANGE_REQUESTED)) > 0;
    }

    default long countTodo(Long userId) {
        return selectCount(new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eq(CollaborationCaseDO::getLegalUserId, userId)
                .in(CollaborationCaseDO::getStatus,
                        CollaborationCaseDO.STATUS_RUNNING, CollaborationCaseDO.STATUS_CHANGE_REQUESTED));
    }

    default CollaborationCaseDO selectValidConclusion(Long contractId, Long revisionId) {
        return selectOne(new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eq(CollaborationCaseDO::getContractId, contractId)
                .eq(CollaborationCaseDO::getCompletedRevisionId, revisionId)
                .eq(CollaborationCaseDO::getStatus, CollaborationCaseDO.STATUS_COMPLETED)
                .orderByDesc(CollaborationCaseDO::getId)
                .last("LIMIT 1"));
    }

    default PageResult<CollaborationCaseDO> selectPage(CollaborationPageReqVO reqVO, Long userId) {
        LambdaQueryWrapperX<CollaborationCaseDO> query = new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eqIfPresent(CollaborationCaseDO::getStatus, reqVO.getStatus())
                .eqIfPresent(CollaborationCaseDO::getContractId, reqVO.getContractId())
                .orderByDesc(CollaborationCaseDO::getId);
        String view = reqVO.getView();
        if (CollaborationPageReqVO.VIEW_TODO.equals(view)) {
            query.eq(CollaborationCaseDO::getLegalUserId, userId)
                    .in(CollaborationCaseDO::getStatus,
                            CollaborationCaseDO.STATUS_RUNNING, CollaborationCaseDO.STATUS_CHANGE_REQUESTED);
        } else if (CollaborationPageReqVO.VIEW_STARTED.equals(view)) {
            query.eq(CollaborationCaseDO::getInitiatorUserId, userId);
        } else if (CollaborationPageReqVO.VIEW_COMPLETED.equals(view)) {
            query.and(item -> item.eq(CollaborationCaseDO::getLegalUserId, userId)
                            .or().eq(CollaborationCaseDO::getInitiatorUserId, userId))
                    .in(CollaborationCaseDO::getStatus,
                            CollaborationCaseDO.STATUS_COMPLETED, CollaborationCaseDO.STATUS_CANCELED);
        } else {
            query.and(item -> item.eq(CollaborationCaseDO::getLegalUserId, userId)
                    .or().eq(CollaborationCaseDO::getInitiatorUserId, userId));
        }
        return selectPage(reqVO, query);
    }

    default List<CollaborationCaseDO> selectListByContractId(Long contractId) {
        return selectList(new LambdaQueryWrapperX<CollaborationCaseDO>()
                .eq(CollaborationCaseDO::getContractId, contractId)
                .orderByDesc(CollaborationCaseDO::getId));
    }

}
