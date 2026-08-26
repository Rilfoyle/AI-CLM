package cn.iocoder.yudao.module.clm.service.party;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartyPageReqVO;
import cn.iocoder.yudao.module.clm.controller.admin.party.vo.PartySaveReqVO;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractPartyDO;
import cn.iocoder.yudao.module.clm.dal.dataobject.party.PartyDO;
import cn.iocoder.yudao.module.clm.dal.mysql.contract.ContractPartyMapper;
import cn.iocoder.yudao.module.clm.dal.mysql.party.PartyMapper;
import cn.iocoder.yudao.module.clm.enums.contract.ClmContractPartyRoleEnum;
import cn.iocoder.yudao.module.clm.enums.party.ClmPartyTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PARTY_IN_USE;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PARTY_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PartyServiceImpl} 的单元测试
 */
@Import(PartyServiceImpl.class)
public class PartyServiceImplTest extends BaseDbUnitTest {

    private static final String[] BASE_IGNORE_FIELDS = {"createTime", "updateTime", "creator", "updater", "deleted", "tenantId"};

    @Resource
    private PartyServiceImpl partyService;

    @Resource
    private PartyMapper partyMapper;
    @Resource
    private ContractPartyMapper contractPartyMapper;

    @Test
    public void testCreateParty_success() {
        PartySaveReqVO reqVO = buildSaveReqVO("芋道科技", true);
        reqVO.setStatus(null); // 默认 0

        Long id = partyService.createParty(reqVO);

        assertNotNull(id);
        PartyDO party = partyMapper.selectById(id);
        assertPojoEquals(reqVO, party, "id", "status");
        assertEquals(CommonStatusEnum.ENABLE.getStatus(), party.getStatus());
    }

    @Test
    public void testUpdateParty_success() {
        PartyDO db = buildPartyDO("旧名称", false);
        partyMapper.insert(db);
        PartySaveReqVO reqVO = buildSaveReqVO("新名称", true);
        reqVO.setId(db.getId());
        reqVO.setStatus(CommonStatusEnum.DISABLE.getStatus());

        partyService.updateParty(reqVO);

        PartyDO party = partyMapper.selectById(db.getId());
        assertPojoEquals(reqVO, party);
    }

    @Test
    public void testUpdateParty_notExists() {
        PartySaveReqVO reqVO = buildSaveReqVO("x", true);
        reqVO.setId(99999L);
        assertServiceException(() -> partyService.updateParty(reqVO), PARTY_NOT_EXISTS);
    }

    @Test
    public void testDeleteParty_success() {
        PartyDO db = buildPartyDO("待删除", false);
        partyMapper.insert(db);

        partyService.deleteParty(db.getId());

        assertNull(partyMapper.selectById(db.getId()));
    }

    @Test
    public void testDeleteParty_inUse() {
        PartyDO db = buildPartyDO("被引用", false);
        partyMapper.insert(db);
        contractPartyMapper.insert(new ContractPartyDO().setContractId(1L).setPartyId(db.getId())
                .setRoleCode(ClmContractPartyRoleEnum.COUNTERPARTY.getCode()).setSort(0));

        assertServiceException(() -> partyService.deleteParty(db.getId()), PARTY_IN_USE);
        assertNotNull(partyMapper.selectById(db.getId()));
    }

    @Test
    public void testGetPartyPage() {
        PartyDO db = buildPartyDO("芋道科技", true);
        partyMapper.insert(db);
        partyMapper.insert(buildPartyDO("土豆公司", false)); // 不匹配 name
        partyMapper.insert(buildPartyDO("芋道个人", false).setPartyType(ClmPartyTypeEnum.INDIVIDUAL.getStatus())); // 不匹配 partyType

        PartyPageReqVO reqVO = new PartyPageReqVO();
        reqVO.setName("芋道");
        reqVO.setPartyType(ClmPartyTypeEnum.COMPANY.getStatus());
        reqVO.setInternalFlag(true);
        PageResult<PartyDO> pageResult = partyService.getPartyPage(reqVO);

        assertEquals(1, pageResult.getTotal());
        assertPojoEquals(db, pageResult.getList().get(0), BASE_IGNORE_FIELDS);
    }

    @Test
    public void testGetPartySimpleList() {
        PartyDO internal = buildPartyDO("我方", true);
        PartyDO external = buildPartyDO("相对方", false);
        PartyDO disabled = buildPartyDO("关闭", true).setStatus(CommonStatusEnum.DISABLE.getStatus());
        partyMapper.insert(internal);
        partyMapper.insert(external);
        partyMapper.insert(disabled);

        List<PartyDO> all = partyService.getPartySimpleList(null);
        assertEquals(2, all.size());
        List<PartyDO> internals = partyService.getPartySimpleList(true);
        assertEquals(1, internals.size());
        assertEquals(internal.getId(), internals.get(0).getId());
    }

    @Test
    public void testValidatePartyList() {
        PartyDO a = buildPartyDO("A", true);
        PartyDO b = buildPartyDO("B", false);
        partyMapper.insert(a);
        partyMapper.insert(b);

        Map<Long, PartyDO> map = partyService.validatePartyList(Arrays.asList(a.getId(), b.getId()));
        assertEquals(2, map.size());
        assertServiceException(() -> partyService.validatePartyList(Arrays.asList(a.getId(), 99999L)), PARTY_NOT_EXISTS);
    }

    // ========== 构造对象 ==========

    private static PartySaveReqVO buildSaveReqVO(String name, boolean internalFlag) {
        PartySaveReqVO reqVO = new PartySaveReqVO();
        reqVO.setPartyType(ClmPartyTypeEnum.COMPANY.getStatus());
        reqVO.setName(name);
        reqVO.setUnifiedCreditCode("91330100MA2XXXXXXX");
        reqVO.setInternalFlag(internalFlag);
        reqVO.setContactName("张三");
        reqVO.setContactPhone("13800000000");
        reqVO.setAddress("杭州");
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setRemark("备注");
        return reqVO;
    }

    private static PartyDO buildPartyDO(String name, boolean internalFlag) {
        return new PartyDO()
                .setPartyType(ClmPartyTypeEnum.COMPANY.getStatus())
                .setName(name)
                .setUnifiedCreditCode("91330100MA2XXXXXXX")
                .setInternalFlag(internalFlag)
                .setContactName("张三")
                .setContactPhone("13800000000")
                .setAddress("杭州")
                .setStatus(CommonStatusEnum.ENABLE.getStatus())
                .setRemark("备注");
    }

}
