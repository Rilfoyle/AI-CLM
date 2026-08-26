package cn.iocoder.yudao.module.clm.dal.mysql.audit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.AuditEventPageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.audit.AuditEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * CLM 审计事件 Mapper
 */
@Mapper
public interface AuditEventMapper extends BaseMapperX<AuditEventDO> {

    default PageResult<AuditEventDO> selectPage(AuditEventPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<AuditEventDO>()
                .eq(AuditEventDO::getContractId, reqVO.getContractId())
                .orderByDesc(AuditEventDO::getOccurredAt)
                .orderByDesc(AuditEventDO::getId));
    }

}
