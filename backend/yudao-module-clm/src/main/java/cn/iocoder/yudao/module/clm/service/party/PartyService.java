package cn.iocoder.yudao.module.clm.service.party;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * CLM 签约方 Service 接口
 */
public interface PartyService {

    Long createParty(@Valid PartySaveReqVO createReqVO);

    void updateParty(@Valid PartySaveReqVO updateReqVO);

    /**
     * 删除签约方；被合同引用时抛 PARTY_IN_USE
     */
    void deleteParty(Long id);

    PartyDO getParty(Long id);

    /**
     * 获得签约方；不存在抛 PARTY_NOT_EXISTS
     */
    PartyDO getRequiredParty(Long id);

    PageResult<PartyDO> getPartyPage(PartyPageReqVO pageReqVO);

    /**
     * 精简列表：status=0；internalFlag 可空
     */
    List<PartyDO> getPartySimpleList(Boolean internalFlag);

    /**
     * 校验签约方都存在，并返回 id -> DO 映射
     */
    Map<Long, PartyDO> validatePartyList(Collection<Long> ids);

}
