package cn.iocoder.yudao.module.clm.ai;

import java.util.List;

public interface AiReviewService {
    Long run(AiReviewRunReqVO reqVO, Long userId);
    List<AiReviewRunRespVO> getList(Long contractId, Long userId);
    void resolveFinding(Long findingId, String resolution, Long userId);
}
