package cn.iocoder.yudao.module.clm.dal.mysql.contract;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.controller.admin.contract.vo.ContractPageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractParticipantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * CLM 合同 Mapper
 */
@Mapper
public interface ContractMapper extends BaseMapperX<ContractDO> {

    /**
     * 分页查询合同，并在 SQL 层面按对象权限过滤：
     * owner_user_id = userId OR id IN (参与人 can_view 的合同)
     *
     * @param reqVO  查询条件
     * @param userId 当前登录用户编号
     * @return 分页结果
     */
    default PageResult<ContractDO> selectPage(ContractPageReqVO reqVO, Long userId) {
        String participantSql = "SELECT contract_id FROM clm_contract_participant"
                + " WHERE principal_type = '" + ContractParticipantDO.PRINCIPAL_TYPE_USER + "'"
                + " AND principal_id = " + userId
                + " AND can_view = TRUE AND deleted = FALSE";
        return selectPage(reqVO, new LambdaQueryWrapperX<ContractDO>()
                .likeIfPresent(ContractDO::getTitle, reqVO.getTitle())
                .likeIfPresent(ContractDO::getContractNo, reqVO.getContractNo())
                .eqIfPresent(ContractDO::getTypeId, reqVO.getTypeId())
                .eqIfPresent(ContractDO::getApprovalStatus, reqVO.getApprovalStatus())
                .eqIfPresent(ContractDO::getLifecycleStatus, reqVO.getLifecycleStatus())
                .eqIfPresent(ContractDO::getOwnerUserId, reqVO.getOwnerUserId())
                .betweenIfPresent(ContractDO::getCreateTime, reqVO.getCreateTime())
                .and(w -> w.eq(ContractDO::getOwnerUserId, userId)
                        .or()
                        .inSql(ContractDO::getId, participantSql))
                .orderByDesc(ContractDO::getId));
    }

    default Long selectCountByTypeId(Long typeId) {
        return selectCount(ContractDO::getTypeId, typeId);
    }

}
