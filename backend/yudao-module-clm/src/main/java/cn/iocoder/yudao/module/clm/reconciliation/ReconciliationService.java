package cn.iocoder.yudao.module.clm.reconciliation;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface ReconciliationService {
    Long run(ReconciliationRunReqVO reqVO);

    Boolean confirm(ReconciliationConfirmReqVO reqVO, Long userId);

    PageResult<ReconciliationRunDO> getPage(PageParam pageParam);
}
