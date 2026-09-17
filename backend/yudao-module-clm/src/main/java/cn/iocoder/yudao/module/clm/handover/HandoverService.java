package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface HandoverService {
    HandoverRefreshRespVO refresh(Long sourceUserId, Long administratorUserId);
    PageResult<HandoverCaseSummaryRespVO> getPage(HandoverPageReqVO reqVO);
    HandoverDetailRespVO get(Long id);
    HandoverReassignRespVO reassign(HandoverReassignReqVO reqVO, Long administratorUserId);
}
