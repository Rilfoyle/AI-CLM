package cn.iocoder.yudao.module.clm.collaboration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface CollaborationService {
    PageResult<CollaborationCaseRespVO> getPage(CollaborationPageReqVO reqVO, Long userId);
    CollaborationCaseRespVO get(Long id, Long userId);
    Long start(CollaborationStartReqVO reqVO, Long userId);
    void comment(CollaborationActionReqVO reqVO, Long userId);
    void requestChange(CollaborationActionReqVO reqVO, Long userId);
    void complete(CollaborationActionReqVO reqVO, Long userId);
    void cancel(CollaborationActionReqVO reqVO, Long userId);
    boolean hasValidConclusion(Long contractId, Long revisionId);
}
