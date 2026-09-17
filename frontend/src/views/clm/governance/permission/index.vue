<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>数据权限规则</h2>
          <p
            >配置产品角色可分配的数据范围上限；具体用户授权在“系统管理 →
            产品角色与数据范围”中执行。</p
          >
        </div>
        <el-button
          type="primary"
          @click="openPolicyEditor()"
          v-hasPermi="['clm:permission-policy:update']"
        >
          <Icon icon="ep:plus" class="mr-5px" />新建规则版本
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        title="数据权限规则服务暂时不可用"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-alert
        class="mb-16px"
        type="info"
        :closable="false"
        show-icon
        title="第一期的数据权限规则版本同时保存固定产品角色能力和审批中编辑预设；固定能力只读，数据范围与编辑预设可配置。"
      />
      <el-table v-loading="loading" :data="list" empty-text="暂无数据权限规则版本">
        <el-table-column label="版本" width="90" align="center">
          <template #default="scope">V{{ scope.row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{
              statusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色能力" min-width="240">
          <template #default="scope">{{ capabilitySummary(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="数据范围上限" min-width="220">
          <template #default="scope">{{ scopeSummary(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="审批中编辑" min-width="220">
          <template #default="scope">
            <el-tag :type="editPolicy(scope.row).enabled ? 'warning' : 'info'">
              {{ editPolicy(scope.row).enabled ? '按字段开放' : '默认禁止' }}
            </el-tag>
            <span v-if="editPolicy(scope.row).enabled" class="ml-8px">
              {{ editPolicy(scope.row).editableFields.length }} 个字段
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="版本说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="发布时间" width="170">
          <template #default="scope">{{ formatNullableDate(scope.row.publishedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openPolicyViewer(scope.row)">查看</el-button>
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              link
              type="primary"
              @click="openPolicyEditor(scope.row)"
              v-hasPermi="['clm:permission-policy:update']"
            >
              编辑
            </el-button>
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              link
              type="success"
              @click="handlePublish(scope.row)"
              v-hasPermi="['clm:permission-policy:publish']"
            >
              发布
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="820px"
      destroy-on-close
      class="policy-drawer"
    >
      <el-form label-position="top">
        <section class="policy-section">
          <div class="section-title">
            <div>
              <h3>产品角色能力（只读）</h3>
              <p>产品角色及其能力集合由首版产品语义锁定，此处只读展示，不能增删组合。</p>
            </div>
          </div>
          <el-table :data="roleRows" border>
            <el-table-column prop="label" label="产品角色" width="130" />
            <el-table-column label="允许能力" min-width="420">
              <template #default="scope">
                <el-checkbox-group v-model="policyForm.roleCapabilities[scope.row.code]" disabled>
                  <el-checkbox
                    v-for="capability in scope.row.capabilities"
                    :key="capability.value"
                    :value="capability.value"
                  >
                    {{ capability.label }}
                  </el-checkbox>
                </el-checkbox-group>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="policy-section">
          <div class="section-title">
            <div>
              <h3>数据范围上限（可配置）</h3>
              <p>系统管理员只能在这些上限内给具体用户分配组织和合同分类范围。</p>
            </div>
          </div>
          <el-table :data="roleRows" border>
            <el-table-column prop="label" label="产品角色" width="130" />
            <el-table-column label="组织范围上限">
              <template #default="scope">
                <el-select
                  v-model="policyForm.scopeLimits[scope.row.code].orgScope"
                  :disabled="readonly"
                  class="!w-100%"
                >
                  <el-option label="不允许分配" value="NONE" />
                  <el-option label="仅指定组织" value="ASSIGNED" />
                  <el-option label="全组织" value="ALL" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="合同分类范围上限">
              <template #default="scope">
                <el-select
                  v-model="policyForm.scopeLimits[scope.row.code].typeScope"
                  :disabled="readonly"
                  class="!w-100%"
                >
                  <el-option label="不允许分配" value="NONE" />
                  <el-option label="仅指定类型" value="ASSIGNED" />
                  <el-option label="全类型" value="ALL" />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <section class="policy-section">
          <div class="section-title">
            <div>
              <h3>审批中编辑规则（本期合并配置）</h3>
              <p>审批中每次编辑都生成新修订；重大字段变化会取消旧业务单并完整重走审批。</p>
            </div>
            <el-switch
              v-model="policyForm.nodeEditPolicy.enabled"
              :disabled="readonly"
              active-text="允许当前审批人编辑"
            />
          </div>
          <el-alert
            type="warning"
            :closable="false"
            title="默认应保持关闭；只有已验收的节点编辑场景才开放有限字段。"
            show-icon
            class="mb-14px"
          />
          <el-form-item label="可编辑字段">
            <el-checkbox-group
              v-model="policyForm.nodeEditPolicy.editableFields"
              :disabled="readonly || !policyForm.nodeEditPolicy.enabled"
            >
              <el-checkbox
                v-for="field in editableFieldOptions"
                :key="field.value"
                :value="field.value"
              >
                {{ field.label }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <el-form-item label="重大变化字段">
            <el-checkbox-group
              v-model="policyForm.nodeEditPolicy.majorFields"
              :disabled="readonly || !policyForm.nodeEditPolicy.enabled"
            >
              <el-checkbox
                v-for="field in selectedEditableOptions"
                :key="field.value"
                :value="field.value"
              >
                {{ field.label }}
              </el-checkbox>
            </el-checkbox-group>
            <div class="field-tip">重大字段产生新修订后自动取消旧审批并按当前路由重提。</div>
          </el-form-item>
        </section>

        <section class="policy-section">
          <el-form-item label="版本说明">
            <el-input
              v-model="policyForm.remark"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              :disabled="readonly"
            />
          </el-form-item>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button v-if="!readonly" type="primary" :loading="saving" @click="savePolicy">
          保存草稿
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import * as PermissionApi from '@/api/clm/governance/permission'

defineOptions({ name: 'ClmGovernancePermission' })

type ScopeLimit = { orgScope: 'NONE' | 'ASSIGNED' | 'ALL'; typeScope: 'NONE' | 'ASSIGNED' | 'ALL' }
type NodePolicy = { enabled: boolean; editableFields: string[]; majorFields: string[] }

const roleRows = [
  {
    code: 'clm_business' as const,
    label: '业务经办人',
    capabilities: [
      { label: '起草合同', value: 'CREATE_DRAFT' },
      { label: '编辑经办合同', value: 'EDIT_OWN' },
      { label: '发起法务协同', value: 'START_COLLABORATION' },
      { label: '提交审批', value: 'SUBMIT_APPROVAL' }
    ]
  },
  {
    code: 'clm_legal' as const,
    label: '法务',
    capabilities: [
      { label: '处理法务协同', value: 'HANDLE_COLLABORATION' },
      { label: '要求修改', value: 'REQUEST_CHANGE' },
      { label: '完成协同', value: 'COMPLETE_COLLABORATION' },
      { label: '查看授权合同', value: 'VIEW_AUTHORIZED' }
    ]
  },
  {
    code: 'clm_contract_admin' as const,
    label: '合同管理员',
    capabilities: [
      { label: '发布治理版本', value: 'PUBLISH_GOVERNANCE' },
      { label: '处理配置异常', value: 'RESOLVE_GOVERNANCE' },
      { label: '审批异常确认', value: 'CONFIRM_RECONCILIATION' },
      { label: '查看授权台账', value: 'VIEW_AUTHORIZED' }
    ]
  },
  {
    code: 'clm_system_admin' as const,
    label: '系统管理员',
    capabilities: [
      { label: '分配产品角色', value: 'ASSIGN_USER_SCOPE' },
      { label: '运行集成检查', value: 'RUN_INTEGRATION' },
      { label: '技术对账重放', value: 'REPLAY_RECONCILIATION' }
    ]
  }
]

const editableFieldOptions = [
  { label: '合同名称', value: 'name' },
  { label: '合同金额', value: 'amount' },
  { label: '币种', value: 'currency' },
  { label: '开始日期', value: 'startDate' },
  { label: '结束日期', value: 'endDate' },
  { label: '合同说明', value: 'description' },
  { label: '扩展字段', value: 'customData' },
  { label: '合同参与方', value: 'parties' },
  { label: '合同正文', value: 'document' }
]

const defaultRoleCapabilities = () => ({
  clm_business: ['CREATE_DRAFT', 'EDIT_OWN', 'START_COLLABORATION', 'SUBMIT_APPROVAL'],
  clm_legal: [
    'HANDLE_COLLABORATION',
    'REQUEST_CHANGE',
    'COMPLETE_COLLABORATION',
    'VIEW_AUTHORIZED'
  ],
  clm_contract_admin: [
    'PUBLISH_GOVERNANCE',
    'RESOLVE_GOVERNANCE',
    'CONFIRM_RECONCILIATION',
    'VIEW_AUTHORIZED'
  ],
  clm_system_admin: ['ASSIGN_USER_SCOPE', 'RUN_INTEGRATION', 'REPLAY_RECONCILIATION']
})

const defaultScopeLimits = (): Record<PermissionApi.ClmProductRole, ScopeLimit> => ({
  clm_business: { orgScope: 'ASSIGNED', typeScope: 'ASSIGNED' },
  clm_legal: { orgScope: 'ASSIGNED', typeScope: 'ASSIGNED' },
  clm_contract_admin: { orgScope: 'ALL', typeScope: 'ALL' },
  clm_system_admin: { orgScope: 'NONE', typeScope: 'NONE' }
})

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<PermissionApi.PermissionPolicyVO[]>([])
const drawerVisible = ref(false)
const readonly = ref(false)
const saving = ref(false)
const editingId = ref<string | number>()
const policyForm = reactive<{
  roleCapabilities: Record<PermissionApi.ClmProductRole, string[]>
  scopeLimits: Record<PermissionApi.ClmProductRole, ScopeLimit>
  nodeEditPolicy: NodePolicy
  remark: string
}>({
  roleCapabilities: defaultRoleCapabilities(),
  scopeLimits: defaultScopeLimits(),
  nodeEditPolicy: { enabled: false, editableFields: [], majorFields: [] },
  remark: ''
})
const drawerTitle = computed(() =>
  readonly.value
    ? '查看数据权限规则'
    : editingId.value
      ? '编辑数据权限规则草稿'
      : '新建数据权限规则'
)
const selectedEditableOptions = computed(() =>
  editableFieldOptions.filter((field) =>
    policyForm.nodeEditPolicy.editableFields.includes(field.value)
  )
)

const safeParse = <T,>(value: string | undefined, fallback: T): T => {
  if (!value) return fallback
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

const statusText = (status: PermissionApi.PermissionPolicyStatus) => {
  return { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已失效' }[status]
}

const statusTagType = (status: PermissionApi.PermissionPolicyStatus) => {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'
}

const editPolicy = (row: PermissionApi.PermissionPolicyVO): NodePolicy => {
  const parsed = safeParse<{ default?: NodePolicy }>(row.nodeEditPolicyJson, {})
  return parsed.default || { enabled: false, editableFields: [], majorFields: [] }
}

const capabilitySummary = (_row: PermissionApi.PermissionPolicyVO) => {
  const defaults = defaultRoleCapabilities()
  const count = Object.values(defaults).reduce((sum, items) => sum + items.length, 0)
  return `${Object.keys(defaults).length} 个固定角色，${count} 项固定能力`
}

const scopeSummary = (row: PermissionApi.PermissionPolicyVO) => {
  const parsed = safeParse<Record<string, ScopeLimit>>(row.scopeLimitsJson, {})
  const allCount = Object.values(parsed).filter(
    (item) => item?.orgScope === 'ALL' || item?.typeScope === 'ALL'
  ).length
  return allCount ? `${allCount} 个角色允许全量上限` : '全部限制为指定范围'
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    list.value = (await PermissionApi.getPermissionPolicyList()) || []
  } catch {
    list.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const fillPolicyForm = (row?: PermissionApi.PermissionPolicyVO) => {
  editingId.value = row?.status === 'DRAFT' ? row.id : undefined
  const scopeLimits = safeParse<Partial<Record<PermissionApi.ClmProductRole, ScopeLimit>>>(
    row?.scopeLimitsJson,
    {}
  )
  const nodeRoot = safeParse<{ default?: NodePolicy }>(row?.nodeEditPolicyJson, {})
  const capabilityDefaults = defaultRoleCapabilities()
  const scopeDefaults = defaultScopeLimits()
  roleRows.forEach(({ code }) => {
    policyForm.roleCapabilities[code] = [...capabilityDefaults[code]]
    policyForm.scopeLimits[code] = { ...(scopeLimits[code] || scopeDefaults[code]) }
  })
  policyForm.nodeEditPolicy = {
    enabled: Boolean(nodeRoot.default?.enabled),
    editableFields: [...(nodeRoot.default?.editableFields || [])],
    majorFields: [...(nodeRoot.default?.majorFields || [])]
  }
  policyForm.remark = row?.remark || ''
}

const openPolicyEditor = (row?: PermissionApi.PermissionPolicyVO) => {
  readonly.value = false
  fillPolicyForm(row)
  drawerVisible.value = true
}

const openPolicyViewer = (row: PermissionApi.PermissionPolicyVO) => {
  readonly.value = true
  fillPolicyForm(row)
  drawerVisible.value = true
}

const savePolicy = async () => {
  policyForm.nodeEditPolicy.majorFields = policyForm.nodeEditPolicy.majorFields.filter((field) =>
    policyForm.nodeEditPolicy.editableFields.includes(field)
  )
  saving.value = true
  try {
    await PermissionApi.savePermissionPolicy({
      id: editingId.value,
      roleCapabilitiesJson: JSON.stringify(defaultRoleCapabilities()),
      scopeLimitsJson: JSON.stringify(policyForm.scopeLimits),
      nodeEditPolicyJson: JSON.stringify({ default: policyForm.nodeEditPolicy, nodes: {} }),
      remark: policyForm.remark.trim()
    })
    message.success('数据权限规则草稿已保存')
    drawerVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const handlePublish = async (row: PermissionApi.PermissionPolicyVO) => {
  await message.confirm(
    `发布数据权限规则 V${row.versionNo}？新规则将成为用户授权上限，并决定审批节点编辑能力。`
  )
  await PermissionApi.publishPermissionPolicy(row.id)
  message.success('数据权限规则已发布')
  await getList()
}

onMounted(getList)
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.section-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.page-header h2,
.section-title h3 {
  margin: 0 0 6px;
}

.page-header h2 {
  font-size: 22px;
}

.section-title h3 {
  font-size: 16px;
}

.page-header p,
.section-title p,
.field-tip {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.policy-section + .policy-section {
  padding-top: 24px;
  margin-top: 28px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-title {
  margin-bottom: 14px;
}
</style>
