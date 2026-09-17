package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.permission.UserScopeDO;
import cn.iocoder.yudao.module.clm.permission.UserScopeItemDO;
import cn.iocoder.yudao.module.clm.permission.UserScopeItemMapper;
import cn.iocoder.yudao.module.clm.permission.UserScopeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverScopeValidatorTest {

    @Mock private UserScopeMapper userScopeMapper;
    @Mock private UserScopeItemMapper userScopeItemMapper;
    @InjectMocks private HandoverScopeValidator validator;

    @Test
    void assertCanOwnContracts_rejectsActiveRoleWithEmptyContractScope() {
        UserScopeDO active = new UserScopeDO().setId(10L).setUserId(9L)
                .setRoleCode("clm_business").setStatus("ACTIVE");
        when(userScopeMapper.selectListByUser(9L)).thenReturn(List.of(active));
        when(userScopeItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        ServiceException ex = assertThrows(ServiceException.class, () -> validator.assertCanOwnContracts(9L,
                List.of(new ContractDO().setId(20L).setOwnerDeptId(30L).setTypeId(40L))));

        assertEquals(HandoverErrors.TARGET_SCOPE_DENIED.getCode(), ex.getCode());
    }

    @Test
    void assertCanOwnContracts_rejectsScopeMissingContractTypeDimension() {
        UserScopeDO active = new UserScopeDO().setId(10L).setUserId(9L)
                .setRoleCode("clm_business").setStatus("ACTIVE");
        when(userScopeMapper.selectListByUser(9L)).thenReturn(List.of(active));
        when(userScopeItemMapper.selectList(any())).thenReturn(List.of(
                new UserScopeItemDO().setUserScopeId(10L).setScopeType(UserScopeItemDO.TYPE_ORG).setScopeId(30L)));

        ServiceException ex = assertThrows(ServiceException.class, () -> validator.assertCanOwnContracts(9L,
                List.of(new ContractDO().setId(20L).setOwnerDeptId(30L).setTypeId(40L))));

        assertEquals(HandoverErrors.TARGET_SCOPE_DENIED.getCode(), ex.getCode());
    }

    @Test
    void assertCanOwnContracts_acceptsExplicitOrganizationAndContractTypeCoverage() {
        UserScopeDO active = new UserScopeDO().setId(10L).setUserId(9L)
                .setRoleCode("clm_business").setStatus("ACTIVE");
        when(userScopeMapper.selectListByUser(9L)).thenReturn(List.of(active));
        when(userScopeItemMapper.selectList(any())).thenReturn(List.of(
                new UserScopeItemDO().setUserScopeId(10L).setScopeType(UserScopeItemDO.TYPE_ORG).setScopeId(30L),
                new UserScopeItemDO().setUserScopeId(10L).setScopeType(UserScopeItemDO.TYPE_CONTRACT_TYPE)
                        .setScopeId(40L)));

        assertDoesNotThrow(() -> validator.assertCanOwnContracts(9L,
                List.of(new ContractDO().setId(20L).setOwnerDeptId(30L).setTypeId(40L))));
    }
}
