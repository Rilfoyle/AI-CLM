package cn.iocoder.yudao.module.clm.permission;

import java.util.List;

public interface ContractPermissionService {
    List<PermissionPolicyVersionDO> getPolicyList();
    Long savePolicy(PermissionPolicySaveReqVO reqVO);
    void publishPolicy(Long id, Long userId);
    List<UserScopeDO> getUserScopes(Long userId);
    Long saveUserScope(UserScopeSaveReqVO reqVO);
    void deleteUserScope(Long id);
}
