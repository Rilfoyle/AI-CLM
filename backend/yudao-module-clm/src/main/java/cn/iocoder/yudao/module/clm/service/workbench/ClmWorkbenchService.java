package cn.iocoder.yudao.module.clm.service.workbench;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemRespVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchItemsReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.workbench.vo.ClmWorkbenchSummaryRespVO;

/**
 * CLM 角色工作台查询服务。
 *
 * <p>工作台只聚合合同、协同和 Flowable 的权威事实，不持久化第二套任务状态。</p>
 */
public interface ClmWorkbenchService {

    ClmWorkbenchSummaryRespVO getSummary(Long userId);

    PageResult<ClmWorkbenchItemRespVO> getItems(ClmWorkbenchItemsReqVO reqVO, Long userId);

}
