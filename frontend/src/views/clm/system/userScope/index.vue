<template>
  <div class="system-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>产品角色与数据范围</h2>
          <p
            >在已发布数据权限规则的上限内，给具体用户分配产品角色、组织和合同分类范围；所有变更留痕。</p
          >
        </div>
        <el-button
          type="primary"
          :disabled="!selectedUserId || !publishedPolicies.length"
          @click="openScopeDialog()"
          v-hasPermi="['clm:user-scope:update']"
        >
          <Icon icon="ep:plus" class="mr-5px" />分配产品角色
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <div class="user-picker">
        <div>
          <span class="picker-label">选择用户</span>
          <el-select
            v-model="selectedUserId"
            filterable
            clearable
            placeholder="按姓名或账号搜索"
            class="!w-320px"
            @change="loadScopes"
          >
            <el-option
              v-for="user in users"
              :key="user.id"
              :label="`${user.nickname}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
        </div>
        <div v-if="selectedUser" class="user-summary">
          <el-avatar :size="36" :src="selectedUser.avatar" />
          <div>
            <strong>{{ selectedUser.nickname }}</strong>
            <span>{{
              selectedUser.deptName || deptName(selectedUser.deptId) || '未分配部门'
            }}</span>
          </div>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="!publishedPolicies.length && !metadataLoading"
        title="尚未发布数据权限规则"
        description="请先由合同管理员在“基础设置 → 数据权限规则”发布版本，再分配具体用户角色和数据范围。"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      />
      <el-empty v-if="!selectedUserId" description="请先选择一个用户查看其产品角色和数据范围" />
      <template v-else>
        <el-alert
          v-if="loadError"
          title="用户范围加载失败"
          type="warning"
          :closable="false"
          show-icon
          class="mb-16px"
        >
          <template #default
            ><el-link type="primary" @click="loadScopes">重新加载</el-link></template
          >
        </el-alert>
        <el-table v-loading="loading" :data="scopes" empty-text="该用户尚未分配合同产品角色">
          <el-table-column label="产品角色" width="150">
            <template #default="scope">
              <el-tag>{{ roleText(scope.row.roleCode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="政策版本" width="110" align="center">
            <template #default="scope">V{{ policyVersion(scope.row.policyVersionId) }}</template>
          </el-table-column>
          <el-table-column label="组织范围" min-width="220">
            <template #default="scope">{{ orgScopeText(scope.row.orgScopeJson) }}</template>
          </el-table-column>
          <el-table-column label="合同分类范围" min-width="220">
            <template #default="scope">{{ typeScopeText(scope.row.typeScopeJson) }}</template>
          </el-table-column>
          <el-table-column label="有效期" min-width="220">
            <template #default="scope">
              {{ validityText(scope.row.effectiveFrom, scope.row.effectiveTo) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ scope.row.status === 'ACTIVE' ? '有效' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                @click="openScopeDialog(scope.row)"
                v-hasPermi="['clm:user-scope:update']"
              >
                编辑
              </el-button>
              <el-button
                link
                type="danger"
                @click="handleDelete(scope.row)"
                v-hasPermi="['clm:user-scope:update']"
              >
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </ContentWrap>

    <el-dialog
      v-model="dialogVisible"
      title="分配产品角色与数据范围"
      width="700px"
      destroy-on-close
    >
      <el-form ref="scopeFormRef" :model="scopeForm" :rules="rules" label-width="115px">
        <el-form-item label="用户">
          <strong>{{ selectedUser?.nickname }}</strong>
          <span class="ml-8px secondary-text">{{ selectedUser?.username }}</span>
        </el-form-item>
        <el-form-item label="产品角色" prop="roleCode">
          <el-select
            v-model="scopeForm.roleCode"
            class="!w-100%"
            :disabled="Boolean(editingScopeId)"
          >
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数据权限规则" prop="policyVersionId">
          <el-select v-model="scopeForm.policyVersionId" class="!w-100%">
            <el-option
              v-for="policy in publishedPolicies"
              :key="policy.id"
              :label="`V${policy.versionNo} · ${policy.remark || '已发布政策'}`"
              :value="policy.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组织范围" prop="orgIds">
          <el-tree-select
            v-model="scopeForm.orgIds"
            :data="deptTree"
            :props="{ label: 'name', children: 'children' }"
            value-key="id"
            multiple
            show-checkbox
            check-strictly
            collapse-tags
            collapse-tags-tooltip
            clearable
            class="!w-100%"
            :disabled="currentScopeLimit.orgScope === 'NONE'"
            placeholder="选择允许查看的经办组织"
          />
          <div class="field-tip">政策上限：{{ scopeLimitText(currentScopeLimit.orgScope) }}</div>
        </el-form-item>
        <el-form-item label="合同分类范围" prop="contractTypeIds">
          <el-select
            v-model="scopeForm.contractTypeIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            class="!w-100%"
            :disabled="currentScopeLimit.typeScope === 'NONE'"
            placeholder="选择允许查看的合同分类"
          >
            <el-option
              v-for="item in contractTypes"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <div class="field-tip">政策上限：{{ scopeLimitText(currentScopeLimit.typeScope) }}</div>
        </el-form-item>
        <el-form-item label="有效期">
          <el-date-picker
            v-model="effectiveRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="立即生效"
            end-placeholder="长期有效"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="!w-100%"
          />
        </el-form-item>
        <el-form-item label="授权状态" prop="status">
          <el-radio-group v-model="scopeForm.status">
            <el-radio value="ACTIVE">有效</el-radio>
            <el-radio value="INACTIVE">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert :title="scopeValidationHint" type="info" :closable="false" show-icon />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveScope">保存授权</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { handleTree } from '@/utils/tree'
import { formatNullableDate } from '@/utils/formatTime'
import * as PermissionApi from '@/api/clm/governance/permission'
import * as UserApi from '@/api/system/user'
import * as DeptApi from '@/api/system/dept'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmSystemUserScope' })

type ScopeLevel = 'NONE' | 'ASSIGNED' | 'ALL'
type ScopeLimit = { orgScope: ScopeLevel; typeScope: ScopeLevel }

const roleOptions = [
  { label: '业务经办人', value: 'clm_business' as const },
  { label: '法务', value: 'clm_legal' as const },
  { label: '合同管理员', value: 'clm_contract_admin' as const },
  { label: '系统管理员', value: 'clm_system_admin' as const }
]

const message = useMessage()
const metadataLoading = ref(true)
const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)
const users = ref<UserApi.UserVO[]>([])
const departments = ref<DeptApi.DeptVO[]>([])
const deptTree = computed(() => handleTree(departments.value))
const contractTypes = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])
const policies = ref<PermissionApi.PermissionPolicyVO[]>([])
const publishedPolicies = computed(() =>
  policies.value.filter((item) => item.status === 'PUBLISHED')
)
const selectedUserId = ref<number>()
const selectedUser = computed(() => users.value.find((item) => item.id === selectedUserId.value))
const scopes = ref<PermissionApi.UserScopeVO[]>([])

const dialogVisible = ref(false)
const editingScopeId = ref<string>()
const scopeFormRef = ref<FormInstance>()
const effectiveRange = ref<string[]>([])
const scopeForm = reactive<PermissionApi.UserScopeSaveReqVO>({
  userId: '',
  roleCode: 'clm_business',
  policyVersionId: '',
  orgIds: [],
  contractTypeIds: [],
  status: 'ACTIVE'
})
const rules: FormRules = {
  roleCode: [{ required: true, message: '请选择产品角色', trigger: 'change' }],
  policyVersionId: [{ required: true, message: '请选择已发布政策', trigger: 'change' }]
}

const currentPolicy = computed(() =>
  publishedPolicies.value.find((item) => String(item.id) === String(scopeForm.policyVersionId))
)
const currentScopeLimit = computed<ScopeLimit>(() => {
  if (!currentPolicy.value) return { orgScope: 'NONE', typeScope: 'NONE' }
  try {
    const limits = JSON.parse(currentPolicy.value.scopeLimitsJson) as Record<string, ScopeLimit>
    return limits[scopeForm.roleCode] || { orgScope: 'NONE', typeScope: 'NONE' }
  } catch {
    return { orgScope: 'NONE', typeScope: 'NONE' }
  }
})
const scopeValidationHint = computed(() => {
  if (scopeForm.roleCode === 'clm_system_admin') {
    return '系统管理员默认不读取合同数据，组织和合同分类范围必须保持为空。'
  }
  if (scopeForm.status === 'ACTIVE') {
    return '有效授权必须同时显式选择至少 1 个组织和 1 个合同分类；不会默认扩张为全量范围。'
  }
  return '停用授权可保留范围配置，重新启用前仍会校验显式范围。'
})

watch(currentScopeLimit, (limit) => {
  if (limit.orgScope === 'NONE') scopeForm.orgIds = []
  if (limit.typeScope === 'NONE') scopeForm.contractTypeIds = []
})
watch(
  () => scopeForm.roleCode,
  (role) => {
    if (role === 'clm_system_admin') {
      scopeForm.orgIds = []
      scopeForm.contractTypeIds = []
    }
  }
)

const roleText = (role: PermissionApi.ClmProductRole) => {
  return roleOptions.find((item) => item.value === role)?.label || role
}

const policyVersion = (id: string | number) => {
  return policies.value.find((item) => String(item.id) === String(id))?.versionNo || '-'
}

const deptName = (id?: string | number) => {
  return departments.value.find((item) => String(item.id) === String(id))?.name
}

const parseIds = (json?: string) => {
  try {
    const value = JSON.parse(json || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

const orgScopeText = (json?: string) => {
  const ids = parseIds(json)
  if (!ids.length) return '不读取合同组织范围'
  const names = ids.map((id) => deptName(id) || `组织 ${id}`)
  return names.slice(0, 3).join('、') + (names.length > 3 ? ` 等 ${names.length} 个` : '')
}

const typeScopeText = (json?: string) => {
  const ids = parseIds(json)
  if (!ids.length) return '不读取合同分类范围'
  const names = ids.map(
    (id) => contractTypes.value.find((item) => String(item.id) === String(id))?.name || `类型 ${id}`
  )
  return names.slice(0, 3).join('、') + (names.length > 3 ? ` 等 ${names.length} 个` : '')
}

const validityText = (from?: string, to?: string) => {
  if (!from && !to) return '长期有效'
  return `${formatNullableDate(from, 'YYYY-MM-DD', '立即')} 至 ${formatNullableDate(to, 'YYYY-MM-DD', '长期')}`
}

const scopeLimitText = (level: ScopeLevel) => {
  return { NONE: '不可分配', ASSIGNED: '仅指定范围', ALL: '可从全部范围选择' }[level]
}

const loadScopes = async () => {
  scopes.value = []
  if (!selectedUserId.value) return
  loading.value = true
  loadError.value = false
  try {
    scopes.value = (await PermissionApi.getUserScopeList(selectedUserId.value)) || []
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const openScopeDialog = async (row?: PermissionApi.UserScopeVO) => {
  if (!selectedUserId.value) return
  editingScopeId.value = row?.id
  effectiveRange.value = [row?.effectiveFrom, row?.effectiveTo].filter(Boolean) as string[]
  Object.assign(scopeForm, {
    userId: selectedUserId.value,
    roleCode: row?.roleCode || 'clm_business',
    policyVersionId: row?.policyVersionId || publishedPolicies.value[0]?.id || '',
    orgIds: parseIds(row?.orgScopeJson),
    contractTypeIds: parseIds(row?.typeScopeJson),
    status: row?.status || 'ACTIVE'
  })
  dialogVisible.value = true
  await nextTick()
  scopeFormRef.value?.clearValidate()
}

const saveScope = async () => {
  if (!(await scopeFormRef.value?.validate())) return
  if (scopeForm.roleCode === 'clm_system_admin') {
    if (scopeForm.orgIds.length || scopeForm.contractTypeIds.length) {
      message.warning('系统管理员不能分配合同组织或合同分类范围')
      return
    }
  } else if (
    scopeForm.status === 'ACTIVE' &&
    (!scopeForm.orgIds.length || !scopeForm.contractTypeIds.length)
  ) {
    message.warning('有效业务授权必须同时选择至少 1 个组织和 1 个合同分类')
    return
  }
  saving.value = true
  try {
    await PermissionApi.saveUserScope({
      ...scopeForm,
      effectiveFrom: effectiveRange.value?.[0],
      effectiveTo: effectiveRange.value?.[1]
    })
    message.success('产品角色与数据范围已保存')
    dialogVisible.value = false
    await loadScopes()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: PermissionApi.UserScopeVO) => {
  await message.confirm(
    `移除“${roleText(row.roleCode)}”后，该用户会立即失去对应产品权限，确认继续？`
  )
  await PermissionApi.deleteUserScope(row.id)
  message.success('产品角色已移除')
  await loadScopes()
}

onMounted(async () => {
  metadataLoading.value = true
  const [userResult, deptResult, typeResult, policyResult] = await Promise.allSettled([
    UserApi.getSimpleUserList(),
    DeptApi.getSimpleDeptList(),
    ContractTypeApi.getContractTypeSimpleList(),
    PermissionApi.getPermissionPolicyList()
  ])
  users.value = userResult.status === 'fulfilled' ? userResult.value || [] : []
  departments.value = deptResult.status === 'fulfilled' ? deptResult.value || [] : []
  contractTypes.value = typeResult.status === 'fulfilled' ? typeResult.value || [] : []
  policies.value = policyResult.status === 'fulfilled' ? policyResult.value || [] : []
  metadataLoading.value = false
})
</script>

<style scoped>
.system-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.user-picker,
.user-summary {
  display: flex;
  align-items: center;
}

.page-header,
.user-picker {
  justify-content: space-between;
  gap: 24px;
}

.page-header {
  align-items: flex-start;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-header p,
.field-tip,
.secondary-text,
.user-summary span {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.picker-label {
  margin-right: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.user-summary {
  gap: 10px;
}

.user-summary div {
  display: flex;
  flex-direction: column;
}
</style>
