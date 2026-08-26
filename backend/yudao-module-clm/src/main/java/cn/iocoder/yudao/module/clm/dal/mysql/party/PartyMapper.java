package cn.iocoder.yudao.module.clm.dal.mysql.party;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyPageReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * CLM 签约方 Mapper
 */
@Mapper
public interface PartyMapper extends BaseMapperX<PartyDO> {

    default PageResult<PartyDO> selectPage(PartyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PartyDO>()
                .likeIfPresent(PartyDO::getName, reqVO.getName())
                .eqIfPresent(PartyDO::getPartyType, reqVO.getPartyType())
                .eqIfPresent(PartyDO::getInternalFlag, reqVO.getInternalFlag())
                .eqIfPresent(PartyDO::getStatus, reqVO.getStatus())
                .orderByDesc(PartyDO::getId));
    }

    default List<PartyDO> selectSimpleList(Integer status, Boolean internalFlag) {
        return selectList(new LambdaQueryWrapperX<PartyDO>()
                .eq(PartyDO::getStatus, status)
                .eqIfPresent(PartyDO::getInternalFlag, internalFlag)
                .orderByDesc(PartyDO::getId));
    }

}
