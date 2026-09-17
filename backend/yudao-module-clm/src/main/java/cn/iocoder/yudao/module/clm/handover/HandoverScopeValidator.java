package cn.iocoder.yudao.module.clm.handover;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.clm.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.clm.permission.UserScopeDO;
import cn.iocoder.yudao.module.clm.permission.UserScopeItemDO;
import cn.iocoder.yudao.module.clm.permission.UserScopeItemMapper;
import cn.iocoder.yudao.module.clm.permission.UserScopeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.handover.HandoverErrors.TARGET_SCOPE_DENIED;

@Component
public class HandoverScopeValidator {

    private static final Set<String> CONTRACT_DOMAIN_ROLES = Set.of(
            "clm_business", "clm_legal", "clm_contract_admin");

    @Resource private UserScopeMapper userScopeMapper;
    @Resource private UserScopeItemMapper userScopeItemMapper;

    public void assertCanOwnContracts(Long targetUserId, List<ContractDO> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<UserScopeDO> activeScopes = new ArrayList<>();
        for (UserScopeDO scope : userScopeMapper.selectListByUser(targetUserId)) {
            if (!CONTRACT_DOMAIN_ROLES.contains(scope.getRoleCode()) || !"ACTIVE".equals(scope.getStatus())) {
                continue;
            }
            if (scope.getEffectiveFrom() != null && scope.getEffectiveFrom().isAfter(now)) {
                continue;
            }
            if (scope.getEffectiveTo() != null && !scope.getEffectiveTo().isAfter(now)) {
                continue;
            }
            activeScopes.add(scope);
        }
        if (activeScopes.isEmpty()) {
            throw exception(TARGET_SCOPE_DENIED);
        }

        Map<Long, ScopeCoverage> coverageByScope = new HashMap<>();
        for (UserScopeDO scope : activeScopes) {
            List<UserScopeItemDO> items = userScopeItemMapper.selectList(
                    new LambdaQueryWrapperX<UserScopeItemDO>()
                            .eq(UserScopeItemDO::getUserScopeId, scope.getId()));
            coverageByScope.put(scope.getId(), ScopeCoverage.of(items));
        }
        for (ContractDO contract : contracts) {
            boolean covered = false;
            for (UserScopeDO scope : activeScopes) {
                ScopeCoverage coverage = coverageByScope.get(scope.getId());
                if (coverage != null && coverage.covers(contract)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                throw exception(TARGET_SCOPE_DENIED);
            }
        }
    }

    private record ScopeCoverage(Set<Long> orgIds, Set<Long> contractTypeIds) {

        static ScopeCoverage of(List<UserScopeItemDO> items) {
            Set<Long> orgIds = new HashSet<>();
            Set<Long> typeIds = new HashSet<>();
            if (items != null) {
                for (UserScopeItemDO item : items) {
                    if (UserScopeItemDO.TYPE_ORG.equals(item.getScopeType())) {
                        orgIds.add(item.getScopeId());
                    } else if (UserScopeItemDO.TYPE_CONTRACT_TYPE.equals(item.getScopeType())) {
                        typeIds.add(item.getScopeId());
                    }
                }
            }
            return new ScopeCoverage(orgIds, typeIds);
        }

        boolean covers(ContractDO contract) {
            if (orgIds.isEmpty() || contractTypeIds.isEmpty()) {
                return false; // 组织或合同类型任一维度为空，都不能证明覆盖目标合同
            }
            boolean orgCovered = contract.getOwnerDeptId() != null && orgIds.contains(contract.getOwnerDeptId());
            boolean typeCovered = contract.getTypeId() != null && contractTypeIds.contains(contract.getTypeId());
            return orgCovered && typeCovered;
        }
    }
}
