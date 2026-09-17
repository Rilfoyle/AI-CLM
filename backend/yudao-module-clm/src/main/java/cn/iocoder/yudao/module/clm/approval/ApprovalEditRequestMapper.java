package cn.iocoder.yudao.module.clm.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApprovalEditRequestMapper extends BaseMapperX<ApprovalEditRequestDO> {
    default ApprovalEditRequestDO selectByRequestId(String requestId) {
        return selectOne(ApprovalEditRequestDO::getRequestId, requestId);
    }
}
