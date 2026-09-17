package cn.iocoder.yudao.module.clm.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiReviewRunRespVO {
    private AiReviewRunDO run;
    private Boolean stale;
    private List<AiReviewFindingDO> findings;
}
