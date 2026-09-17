package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.PERMISSION_POLICY_INVALID;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.USER_SCOPE_EXCEEDS_POLICY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractPermissionServiceImplTest {
    @InjectMocks private ContractPermissionServiceImpl service;
    @Mock private PermissionPolicyMapper policyMapper;
    @Mock private UserScopeMapper userScopeMapper;
    @Mock private UserScopeItemMapper itemMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private RoleMapper roleMapper;
    @Mock private PermissionService permissionService;
    @Mock private ClmAuditService auditService;

    @Test
    void savePolicy_rejectsSystemAdminContractDataScope() {
        PermissionPolicySaveReqVO req = policyRequest();
        req.setScopeLimitsJson("{\"clm_business\":{\"orgScope\":\"ASSIGNED\",\"typeScope\":\"ASSIGNED\"},"
                + "\"clm_legal\":{\"orgScope\":\"ASSIGNED\",\"typeScope\":\"ASSIGNED\"},"
                + "\"clm_contract_admin\":{\"orgScope\":\"ALL\",\"typeScope\":\"ALL\"},"
                + "\"clm_system_admin\":{\"orgScope\":\"ALL\",\"typeScope\":\"NONE\"}}");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.savePolicy(req));

        assertEquals(PERMISSION_POLICY_INVALID.getCode(), ex.getCode());
    }

    @Test
    void saveUserScope_enforcesPublishedNoneLimitOnServer() {
        UserScopeSaveReqVO req = new UserScopeSaveReqVO();
        req.setUserId(7L); req.setRoleCode("clm_system_admin"); req.setPolicyVersionId(3L);
        req.setOrgIds(List.of(99L)); req.setContractTypeIds(List.of()); req.setStatus("ACTIVE");
        when(adminUserApi.getUser(7L)).thenReturn(new AdminUserRespDTO().setId(7L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(policyMapper.selectById(3L)).thenReturn(new PermissionPolicyVersionDO().setId(3L)
                .setStatus("PUBLISHED").setScopeLimitsJson(policyRequest().getScopeLimitsJson()));
        when(userScopeMapper.selectByUserAndRole(7L, "clm_system_admin")).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.saveUserScope(req));

        assertEquals(USER_SCOPE_EXCEEDS_POLICY.getCode(), ex.getCode());
    }

    @Test
    void saveUserScope_physicallyReplacesDerivedScopeItems() {
        UserScopeSaveReqVO req = new UserScopeSaveReqVO();
        req.setUserId(7L); req.setRoleCode("clm_business"); req.setPolicyVersionId(3L);
        req.setOrgIds(List.of(99L)); req.setContractTypeIds(List.of(6L)); req.setStatus("ACTIVE");
        UserScopeDO existing = new UserScopeDO().setId(9L).setUserId(7L).setRoleCode("clm_business")
                .setStatus("ACTIVE");
        when(adminUserApi.getUser(7L)).thenReturn(new AdminUserRespDTO().setId(7L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        when(policyMapper.selectById(3L)).thenReturn(new PermissionPolicyVersionDO().setId(3L)
                .setStatus("PUBLISHED").setScopeLimitsJson(policyRequest().getScopeLimitsJson()));
        when(userScopeMapper.selectByUserAndRole(7L, "clm_business")).thenReturn(existing);
        when(userScopeMapper.selectListByUser(7L)).thenReturn(List.of(existing));
        when(permissionService.getUserRoleIdListByUserId(7L)).thenReturn(Set.of());
        when(roleMapper.selectByCode(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return new cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO()
                    .setId((long) code.hashCode()).setCode(code);
        });

        service.saveUserScope(req);

        var itemOrder = inOrder(itemMapper);
        itemOrder.verify(itemMapper).deletePhysicallyByUserScopeId(9L);
        itemOrder.verify(itemMapper, times(2)).insert(any(UserScopeItemDO.class));
    }

    private PermissionPolicySaveReqVO policyRequest() {
        PermissionPolicySaveReqVO req = new PermissionPolicySaveReqVO();
        req.setRoleCapabilitiesJson("{\"clm_business\":[\"CREATE_DRAFT\"],"
                + "\"clm_legal\":[\"HANDLE_COLLABORATION\"],"
                + "\"clm_contract_admin\":[\"PUBLISH_GOVERNANCE\"],"
                + "\"clm_system_admin\":[\"ASSIGN_USER_SCOPE\"]}");
        req.setScopeLimitsJson("{\"clm_business\":{\"orgScope\":\"ASSIGNED\",\"typeScope\":\"ASSIGNED\"},"
                + "\"clm_legal\":{\"orgScope\":\"ASSIGNED\",\"typeScope\":\"ASSIGNED\"},"
                + "\"clm_contract_admin\":{\"orgScope\":\"ALL\",\"typeScope\":\"ALL\"},"
                + "\"clm_system_admin\":{\"orgScope\":\"NONE\",\"typeScope\":\"NONE\"}}");
        req.setNodeEditPolicyJson("{\"default\":{\"enabled\":false,\"editableFields\":[],\"majorFields\":[]},\"nodes\":{}}");
        req.setRemark("test");
        return req;
    }
}
