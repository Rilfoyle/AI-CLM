package cn.iocoder.yudao.module.clm.service.party;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PARTY_IN_USE;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PARTY_NOT_EXISTS;

/**
 * CLM 签约方 Service 实现类
 */
@Service
@Validated
public class PartyServiceImpl implements PartyService {

    @Resource
    private PartyMapper partyMapper;
    @Resource
    private ContractPartyMapper contractPartyMapper;

    @Override
    public Long createParty(PartySaveReqVO createReqVO) {
        PartyDO party = BeanUtils.toBean(createReqVO, PartyDO.class)
                .setId(null)
                .setStatus(ObjUtil.defaultIfNull(createReqVO.getStatus(), CommonStatusEnum.ENABLE.getStatus()));
        partyMapper.insert(party);
        return party.getId();
    }

    @Override
    public void updateParty(PartySaveReqVO updateReqVO) {
        getRequiredParty(updateReqVO.getId());
        PartyDO updateObj = BeanUtils.toBean(updateReqVO, PartyDO.class);
        partyMapper.updateById(updateObj);
    }

    @Override
    public void deleteParty(Long id) {
        getRequiredParty(id);
        if (contractPartyMapper.selectCountByPartyId(id) > 0) {
            throw exception(PARTY_IN_USE);
        }
        partyMapper.deleteById(id);
    }

    @Override
    public PartyDO getParty(Long id) {
        return partyMapper.selectById(id);
    }

    @Override
    public PartyDO getRequiredParty(Long id) {
        PartyDO party = partyMapper.selectById(id);
        if (party == null) {
            throw exception(PARTY_NOT_EXISTS);
        }
        return party;
    }

    @Override
    public PageResult<PartyDO> getPartyPage(PartyPageReqVO pageReqVO) {
        return partyMapper.selectPage(pageReqVO);
    }

    @Override
    public List<PartyDO> getPartySimpleList(Boolean internalFlag) {
        return partyMapper.selectSimpleList(CommonStatusEnum.ENABLE.getStatus(), internalFlag);
    }

    @Override
    public Map<Long, PartyDO> validatePartyList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new HashMap<>();
        }
        List<PartyDO> parties = partyMapper.selectByIds(ids);
        Map<Long, PartyDO> map = convertMap(parties, PartyDO::getId);
        for (Long id : ids) {
            if (!map.containsKey(id)) {
                throw exception(PARTY_NOT_EXISTS);
            }
        }
        return map;
    }

}
