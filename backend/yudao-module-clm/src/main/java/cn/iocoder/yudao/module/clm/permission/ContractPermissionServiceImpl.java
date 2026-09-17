package cn.iocoder.yudao.module.clm.permission;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditActionEnum;
import cn.iocoder.yudao.module.clm.enums.audit.ClmAuditAggregateTypeEnum;
import cn.iocoder.yudao.module.clm.service.audit.ClmAuditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.clm.enums.ErrorCodeConstants.*;

@Service
public class ContractPermissionServiceImpl implements ContractPermissionService {

    public static final Set<String> CLM_ROLE_CODES = Set.of(
            "clm_business", "clm_legal", "clm_contract_admin", "clm_system_admin");
    private static final Set<String> SCOPE_LEVELS = Set.of("NONE", "ASSIGNED", "ALL");
    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "name", "amount", "currency", "startDate", "endDate", "description", "customData", "parties", "document");
    private static final Map<String, Set<String>> ROLE_CAPABILITIES = Map.of(
            "clm_business", Set.of("CREATE_DRAFT", "EDIT_OWN", "START_COLLABORATION", "SUBMIT_APPROVAL"),
            "clm_legal", Set.of("HANDLE_COLLABORATION", "REQUEST_CHANGE", "COMPLETE_COLLABORATION", "VIEW_AUTHORIZED"),
            "clm_contract_admin", Set.of("PUBLISH_GOVERNANCE", "RESOLVE_GOVERNANCE", "CONFIRM_RECONCILIATION", "VIEW_AUTHORIZED"),
            "clm_system_admin", Set.of("ASSIGN_USER_SCOPE", "RUN_INTEGRATION", "REPLAY_RECONCILIATION"));

    @Resource private PermissionPolicyMapper policyMapper;
    @Resource private UserScopeMapper userScopeMapper;
    @Resource private UserScopeItemMapper itemMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private RoleMapper roleMapper;
    @Resource private PermissionService permissionService;
    @Resource private ClmAuditService auditService;

    @Override
    public List<PermissionPolicyVersionDO> getPolicyList() {
        return policyMapper.selectList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePolicy(PermissionPolicySaveReqVO reqVO) {
        validatePolicy(reqVO);
        PermissionPolicyVersionDO policy;
        if (reqVO.getId() == null) {
            if (policyMapper.selectDraft() != null) throw exception(PERMISSION_POLICY_DRAFT_EXISTS);
            Integer max = policyMapper.selectMaxVersionNo();
            policy = new PermissionPolicyVersionDO().setVersionNo(max == null ? 1 : max + 1).setStatus("DRAFT");
        } else {
            policy = policyMapper.selectById(reqVO.getId());
            if (policy == null) throw exception(PERMISSION_POLICY_NOT_EXISTS);
            if (!"DRAFT".equals(policy.getStatus())) throw exception(PERMISSION_POLICY_NOT_DRAFT);
        }
        policy.setRoleCapabilitiesJson(reqVO.getRoleCapabilitiesJson())
                .setScopeLimitsJson(reqVO.getScopeLimitsJson())
                .setNodeEditPolicyJson(reqVO.getNodeEditPolicyJson()).setRemark(reqVO.getRemark());
        if (policy.getId() == null) policyMapper.insert(policy); else policyMapper.updateById(policy);
        auditService.record(ClmAuditAggregateTypeEnum.PERMISSION_POLICY, policy.getId(), null,
                ClmAuditActionEnum.PERMISSION_POLICY_DRAFT_SAVE,
                Map.of("versionNo", policy.getVersionNo(), "status", policy.getStatus()));
        return policy.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishPolicy(Long id, Long userId) {
        PermissionPolicyVersionDO policy = policyMapper.selectById(id);
        if (policy == null) throw exception(PERMISSION_POLICY_NOT_EXISTS);
        if (!"DRAFT".equals(policy.getStatus())) throw exception(PERMISSION_POLICY_NOT_DRAFT);
        PermissionPolicyVersionDO current = policyMapper.selectPublished();
        if (current != null) {
            policyMapper.updateById(new PermissionPolicyVersionDO().setId(current.getId()).setStatus("INACTIVE"));
        }
        policyMapper.updateById(new PermissionPolicyVersionDO().setId(id).setStatus("PUBLISHED")
                .setPublishedBy(userId).setPublishedTime(LocalDateTime.now()));
        auditService.record(ClmAuditAggregateTypeEnum.PERMISSION_POLICY, id, null,
                ClmAuditActionEnum.PERMISSION_POLICY_PUBLISH,
                Map.of("versionNo", policy.getVersionNo(), "replacedPolicyId", current == null ? "" : current.getId()));
    }

    @Override
    public List<UserScopeDO> getUserScopes(Long userId) {
        return userScopeMapper.selectListByUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveUserScope(UserScopeSaveReqVO reqVO) {
        if (!CLM_ROLE_CODES.contains(reqVO.getRoleCode())) throw exception(USER_SCOPE_ROLE_INVALID);
        var targetUser = adminUserApi.getUser(reqVO.getUserId());
        if (targetUser == null || !CommonStatusEnum.ENABLE.getStatus().equals(targetUser.getStatus())) {
            throw exception(PARTICIPANT_USER_NOT_EXISTS, reqVO.getUserId());
        }
        PermissionPolicyVersionDO policy = policyMapper.selectById(reqVO.getPolicyVersionId());
        if (policy == null || !"PUBLISHED".equals(policy.getStatus())) {
            throw exception(PERMISSION_POLICY_NOT_PUBLISHED);
        }
        UserScopeDO scope = userScopeMapper.selectByUserAndRole(reqVO.getUserId(), reqVO.getRoleCode());
        if (scope == null) {
            scope = new UserScopeDO().setUserId(reqVO.getUserId()).setRoleCode(reqVO.getRoleCode());
        }
        List<Long> orgIds = unique(reqVO.getOrgIds());
        List<Long> typeIds = unique(reqVO.getContractTypeIds());
        validateScopeLimit(policy, reqVO.getRoleCode(), orgIds, typeIds);
        if ("ACTIVE".equals(reqVO.getStatus()) && !"clm_system_admin".equals(reqVO.getRoleCode())
                && (orgIds.isEmpty() || typeIds.isEmpty())) {
            throw exception(USER_SCOPE_EXCEEDS_POLICY, "有效合同域角色必须显式选择组织和合同类型，空范围按默认拒绝处理");
        }
        if (reqVO.getEffectiveFrom() != null && reqVO.getEffectiveTo() != null
                && reqVO.getEffectiveFrom().isAfter(reqVO.getEffectiveTo())) {
            throw exception(USER_SCOPE_EXCEEDS_POLICY, "生效时间晚于失效时间");
        }
        scope.setPolicyVersionId(reqVO.getPolicyVersionId()).setOrgScopeJson(JsonUtils.toJsonString(orgIds))
                .setTypeScopeJson(JsonUtils.toJsonString(typeIds)).setEffectiveFrom(reqVO.getEffectiveFrom())
                .setEffectiveTo(reqVO.getEffectiveTo()).setStatus(reqVO.getStatus());
        if (scope.getId() == null) userScopeMapper.insert(scope); else userScopeMapper.updateById(scope);
        itemMapper.deletePhysicallyByUserScopeId(scope.getId());
        insertItems(scope.getId(), UserScopeItemDO.TYPE_ORG, orgIds);
        insertItems(scope.getId(), UserScopeItemDO.TYPE_CONTRACT_TYPE, typeIds);
        syncSystemRoles(reqVO.getUserId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userId", reqVO.getUserId()); detail.put("roleCode", reqVO.getRoleCode());
        detail.put("policyVersionId", reqVO.getPolicyVersionId()); detail.put("orgCount", orgIds.size());
        detail.put("contractTypeCount", typeIds.size()); detail.put("status", reqVO.getStatus());
        auditService.record(ClmAuditAggregateTypeEnum.USER_SCOPE, scope.getId(), null,
                ClmAuditActionEnum.USER_SCOPE_SAVE, detail);
        return scope.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserScope(Long id) {
        UserScopeDO scope = userScopeMapper.selectById(id);
        if (scope == null) throw exception(USER_SCOPE_NOT_EXISTS);
        itemMapper.deletePhysicallyByUserScopeId(id);
        userScopeMapper.deleteById(id);
        syncSystemRoles(scope.getUserId());
        auditService.record(ClmAuditAggregateTypeEnum.USER_SCOPE, id, null,
                ClmAuditActionEnum.USER_SCOPE_DELETE,
                Map.of("userId", scope.getUserId(), "roleCode", scope.getRoleCode()));
    }

    private void syncSystemRoles(Long userId) {
        Set<Long> current = new HashSet<>(permissionService.getUserRoleIdListByUserId(userId));
        Set<Long> clmRoleIds = new HashSet<>();
        for (String code : CLM_ROLE_CODES) {
            RoleDO role = roleMapper.selectByCode(code);
            if (role == null) throw exception(USER_SCOPE_ROLE_NOT_INSTALLED, code);
            clmRoleIds.add(role.getId());
        }
        current.removeAll(clmRoleIds);
        for (UserScopeDO scope : userScopeMapper.selectListByUser(userId)) {
            if (!"ACTIVE".equals(scope.getStatus())) continue;
            RoleDO role = roleMapper.selectByCode(scope.getRoleCode());
            if (role != null) current.add(role.getId());
        }
        permissionService.assignUserRole(userId, current);
    }

    private void insertItems(Long scopeId, String type, List<Long> ids) {
        for (Long id : ids) {
            itemMapper.insert(new UserScopeItemDO().setUserScopeId(scopeId).setScopeType(type).setScopeId(id));
        }
    }

    private List<Long> unique(List<Long> ids) {
        return ids == null ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private void validatePolicy(PermissionPolicySaveReqVO reqVO) {
        Map<String, Object> capabilities = parseObject(reqVO.getRoleCapabilitiesJson());
        Map<String, Object> limits = parseObject(reqVO.getScopeLimitsJson());
        Map<String, Object> nodePolicy = parseObject(reqVO.getNodeEditPolicyJson());
        if (!capabilities.keySet().equals(CLM_ROLE_CODES) || !limits.keySet().equals(CLM_ROLE_CODES)) {
            throw exception(PERMISSION_POLICY_INVALID);
        }
        for (String role : CLM_ROLE_CODES) {
            Object values = capabilities.get(role);
            if (!(values instanceof List<?> list)) {
                throw exception(PERMISSION_POLICY_INVALID);
            }
            Set<String> configured = new LinkedHashSet<>();
            for (Object value : list) {
                if (!(value instanceof String capability)) throw exception(PERMISSION_POLICY_INVALID);
                configured.add(capability);
            }
            if (!configured.equals(ROLE_CAPABILITIES.get(role))) throw exception(PERMISSION_POLICY_INVALID);
            ScopeLimit limit = scopeLimit(limits, role);
            if ("clm_system_admin".equals(role)
                    && (!"NONE".equals(limit.orgScope()) || !"NONE".equals(limit.typeScope()))) {
                throw exception(PERMISSION_POLICY_INVALID);
            }
        }
        validateNodePolicy(nodePolicy.get("default"));
        Object nodes = nodePolicy.get("nodes");
        if (!(nodes instanceof Map<?, ?> nodeMap)) throw exception(PERMISSION_POLICY_INVALID);
        for (Map.Entry<?, ?> entry : nodeMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank() || key.length() > 128) {
                throw exception(PERMISSION_POLICY_INVALID);
            }
            validateNodePolicy(entry.getValue());
        }
    }

    private void validateScopeLimit(PermissionPolicyVersionDO policy, String roleCode,
                                    List<Long> orgIds, List<Long> typeIds) {
        ScopeLimit limit = scopeLimit(parseObject(policy.getScopeLimitsJson()), roleCode);
        if ("NONE".equals(limit.orgScope()) && !orgIds.isEmpty()) {
            throw exception(USER_SCOPE_EXCEEDS_POLICY, "该角色不可分配组织范围");
        }
        if ("NONE".equals(limit.typeScope()) && !typeIds.isEmpty()) {
            throw exception(USER_SCOPE_EXCEEDS_POLICY, "该角色不可分配合同类型范围");
        }
    }

    private ScopeLimit scopeLimit(Map<String, Object> limits, String role) {
        Object raw = limits.get(role);
        if (!(raw instanceof Map<?, ?> value)) throw exception(PERMISSION_POLICY_INVALID);
        Object org = value.get("orgScope"); Object type = value.get("typeScope");
        if (!(org instanceof String orgScope) || !(type instanceof String typeScope)
                || !SCOPE_LEVELS.contains(orgScope) || !SCOPE_LEVELS.contains(typeScope)) {
            throw exception(PERMISSION_POLICY_INVALID);
        }
        return new ScopeLimit(orgScope, typeScope);
    }

    private void validateNodePolicy(Object raw) {
        if (!(raw instanceof Map<?, ?> policy) || !(policy.get("enabled") instanceof Boolean)
                || !(policy.get("editableFields") instanceof List<?> editable)
                || !(policy.get("majorFields") instanceof List<?> major)) {
            throw exception(PERMISSION_POLICY_INVALID);
        }
        Set<String> editableSet = new LinkedHashSet<>();
        for (Object field : editable) {
            if (!(field instanceof String value) || !EDITABLE_FIELDS.contains(value)) {
                throw exception(PERMISSION_POLICY_INVALID);
            }
            editableSet.add(value);
        }
        for (Object field : major) {
            if (!(field instanceof String value) || !editableSet.contains(value)) {
                throw exception(PERMISSION_POLICY_INVALID);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseObject(String json) {
        try {
            Object parsed = JsonUtils.parseObject(json, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException();
            return (Map<String, Object>) map;
        } catch (RuntimeException ex) {
            throw exception(PERMISSION_POLICY_INVALID);
        }
    }

    private record ScopeLimit(String orgScope, String typeScope) {}
}
