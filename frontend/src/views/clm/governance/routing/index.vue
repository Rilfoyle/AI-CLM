<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>业务单据流程配置</h2>
          <p>按合同分类、经办组织和金额等有限条件选择已发布流程定义；零命中或多命中会阻断提交。</p>
        </div>
        <el-button
          type="primary"
          @click="openRuleDialog()"
          v-hasPermi="['clm:governance:routing:update']"
        >
          <Icon icon="ep:plus" class="mr-5px" />新建配置
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
        <el-form-item label="规则编码" prop="ruleCode">
          <el-input
            v-model="queryParams.ruleCode"
            placeholder="请输入编码"
            clearable
            class="!w-180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="规则名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入名称"
            clearable
            class="!w-220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="合同分类" prop="contractTypeId">
          <el-select
            v-model="queryParams.contractTypeId"
            placeholder="全部类型"
            clearable
            class="!w-200px"
          >
            <el-option
              v-for="item in contractTypes"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable class="!w-150px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已失效" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        title="业务单据流程配置服务暂时不可用"
        description="请确认后端已部署业务单据流程配置接口，然后重试。"
        type="warning"
        :closable="false"
        show-icon
        class="mb-16px"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无业务单据流程配置">
        <el-table-column prop="ruleCode" label="规则编码" min-width="140" />
        <el-table-column prop="name" label="规则名称" min-width="190" show-overflow-tooltip />
        <el-table-column label="适用范围" min-width="260">
          <template #default="scope">
            <div class="condition-summary">
              <span>{{ typeName(scope.row.contractTypeId) }}</span>
              <span>{{ deptName(scope.row.condition?.ownerDeptId) }}</span>
              <span>{{ amountRange(scope.row.condition) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="processDefinitionKey" label="流程定义" min-width="180">
          <template #default="scope">{{ processName(scope.row.processDefinitionKey) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="版本" width="75" align="center">
          <template #default="scope">V{{ scope.row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">{{
              statusText(scope.row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="openRuleDialog(scope.row)"
              v-hasPermi="['clm:governance:routing:update']"
            >
              {{ scope.row.status === 'DRAFT' ? '编辑草稿' : '新建版本' }}
            </el-button>
            <el-button
              v-if="scope.row.status === 'DRAFT'"
              link
              type="success"
              @click="handlePublish(scope.row)"
              v-hasPermi="['clm:governance:routing:publish']"
            >
              发布
            </el-button>
            <el-button
              v-if="scope.row.status === 'PUBLISHED'"
              link
              type="danger"
              @click="handleDisable(scope.row)"
              v-hasPermi="['clm:governance:routing:publish']"
            >
              停用
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:page="queryParams.pageNo"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </ContentWrap>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" destroy-on-close>
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="rules" label-width="110px">
        <div class="form-grid">
          <el-form-item label="规则编码" prop="ruleCode">
            <el-input v-model="ruleForm.ruleCode" maxlength="64" :disabled="Boolean(sourceRule)" />
          </el-form-item>
          <el-form-item label="规则名称" prop="name">
            <el-input v-model="ruleForm.name" maxlength="120" />
          </el-form-item>
          <el-form-item label="合同分类" prop="contractTypeId">
            <el-select
              v-model="ruleForm.contractTypeId"
              class="!w-100%"
              clearable
              placeholder="全部类型"
            >
              <el-option
                v-for="item in contractTypes"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级" prop="priority">
            <el-input-number v-model="ruleForm.priority" :min="1" :max="9999" class="!w-100%" />
          </el-form-item>
          <el-form-item label="经办组织">
            <el-tree-select
              v-model="ruleForm.condition.ownerDeptId"
              :data="deptTree"
              :props="{ label: 'name', children: 'children' }"
              value-key="id"
              check-strictly
              clearable
              placeholder="不限组织"
              class="!w-100%"
            />
          </el-form-item>
          <el-form-item label="币种">
            <el-select
              v-model="ruleForm.condition.currency"
              clearable
              placeholder="不限币种"
              class="!w-100%"
            >
              <el-option label="人民币 CNY" value="CNY" />
              <el-option label="美元 USD" value="USD" />
              <el-option label="欧元 EUR" value="EUR" />
            </el-select>
          </el-form-item>
          <el-form-item label="最低金额">
            <el-input-number
              v-model="ruleForm.condition.minAmount"
              :min="0"
              :precision="2"
              :controls="false"
              class="!w-100%"
              placeholder="不设下限"
            />
          </el-form-item>
          <el-form-item label="最高金额" prop="maxAmount">
            <el-input-number
              v-model="ruleForm.condition.maxAmount"
              :min="0"
              :precision="2"
              :controls="false"
              class="!w-100%"
              placeholder="不设上限"
            />
          </el-form-item>
        </div>
        <el-form-item label="流程定义" prop="processDefinitionKey">
          <el-select
            v-if="processDefinitions.length"
            v-model="ruleForm.processDefinitionKey"
            class="!w-100%"
            filterable
            placeholder="请选择已发布合同流程定义"
          >
            <el-option
              v-for="item in processDefinitions"
              :key="item.key"
              :label="item.name"
              :value="item.key"
            />
          </el-select>
          <el-input
            v-else
            v-model="ruleForm.processDefinitionKey"
            placeholder="请输入已发布合同流程定义标识"
          />
          <div class="field-tip">这里只选择已发布流程定义；流程图请在“流程定义”中维护。</div>
        </el-form-item>

        <el-form-item label="命中预检">
          <div class="precheck-box">
            <div>
              <strong>{{ precheckTitle }}</strong>
              <p>{{ precheckDescription }}</p>
            </div>
            <el-button :loading="prechecking" @click="handlePrecheck">校验当前条件</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="prechecking" @click="handlePrecheck">条件预检</el-button>
        <el-button type="primary" :loading="saving" @click="saveDraft">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { handleTree } from '@/utils/tree'
import * as RoutingApi from '@/api/clm/governance/routing'
import * as ContractTypeApi from '@/api/clm/contractType'
import * as DeptApi from '@/api/system/dept'
import * as DefinitionApi from '@/api/bpm/definition'

defineOptions({ name: 'ClmGovernanceRouting' })

interface ProcessDefinitionOption {
  key: string
  name: string
  version?: number
}

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const list = ref<RoutingApi.RoutingRuleVO[]>([])
const total = ref(0)
const contractTypes = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])
const deptList = ref<DeptApi.DeptVO[]>([])
const deptTree = computed(() => handleTree(deptList.value))
const processDefinitions = ref<ProcessDefinitionOption[]>([])
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<RoutingApi.RoutingRulePageReqVO>({ pageNo: 1, pageSize: 10 })

const dialogVisible = ref(false)
const saving = ref(false)
const prechecking = ref(false)
const precheckResult = ref<RoutingApi.RoutingPrecheckRespVO>()
const sourceRule = ref<RoutingApi.RoutingRuleVO>()
const ruleFormRef = ref<FormInstance>()
const ruleForm = reactive<RoutingApi.RoutingRuleDraftReqVO>({
  ruleCode: '',
  name: '',
  contractTypeId: undefined,
  priority: 100,
  condition: {},
  processDefinitionKey: ''
})
const rules: FormRules = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  priority: [{ required: true, message: '请输入优先级', trigger: 'change' }],
  processDefinitionKey: [{ required: true, message: '请选择流程定义', trigger: 'change' }]
}
const dialogTitle = computed(() => {
  if (!sourceRule.value) return '新建业务单据流程配置'
  return sourceRule.value.status === 'DRAFT'
    ? '编辑业务单据流程配置草稿'
    : '新建业务单据流程配置版本'
})
const precheckTitle = computed(() => {
  if (!precheckResult.value) return '尚未预检'
  return {
    UNIQUE: '唯一命中，可以继续',
    ZERO: '零命中，会阻断合同提交',
    MULTIPLE: '多命中，会阻断合同提交'
  }[precheckResult.value.result]
})
const precheckDescription = computed(() => {
  if (!precheckResult.value) return '使用当前类型、组织和金额边界检查已发布规则是否冲突。'
  return `命中 ${precheckResult.value.matchedCount} 个规则版本；发布时服务端会再次校验。`
})

const typeName = (id?: string | number) => {
  if (!id) return '全部类型'
  return contractTypes.value.find((item) => String(item.id) === String(id))?.name || `类型 ${id}`
}

const deptName = (id?: string | number) => {
  if (!id) return '全部组织'
  return deptList.value.find((item) => String(item.id) === String(id))?.name || `组织 ${id}`
}

const processName = (key: string) => {
  return processDefinitions.value.find((item) => item.key === key)?.name || key
}

const amountRange = (condition?: RoutingApi.RoutingConditionVO) => {
  const currency = condition?.currency || '任意币种'
  if (condition?.minAmount != null && condition?.maxAmount != null) {
    return `${currency} ${condition.minAmount}–${condition.maxAmount}`
  }
  if (condition?.minAmount != null) return `${currency} ≥ ${condition.minAmount}`
  if (condition?.maxAmount != null) return `${currency} ≤ ${condition.maxAmount}`
  return `${currency} 全金额`
}

const statusText = (status: RoutingApi.RoutingRuleStatus) => {
  return { DRAFT: '草稿', PUBLISHED: '已发布', INACTIVE: '已失效' }[status]
}

const statusTagType = (status: RoutingApi.RoutingRuleStatus) => {
  return status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'
}

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await RoutingApi.getRoutingRulePage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } catch {
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const openRuleDialog = async (row?: RoutingApi.RoutingRuleVO) => {
  sourceRule.value = row
  precheckResult.value = undefined
  Object.assign(ruleForm, {
    id: row?.status === 'DRAFT' ? row.id : undefined,
    ruleCode: row?.ruleCode || '',
    name: row?.name || '',
    contractTypeId: row?.contractTypeId,
    priority: row?.priority || 100,
    condition: { ...(row?.condition || {}) },
    processDefinitionKey: row?.processDefinitionKey || ''
  })
  dialogVisible.value = true
  await nextTick()
  ruleFormRef.value?.clearValidate()
}

const handlePrecheck = async () => {
  if (!ruleForm.contractTypeId) {
    message.warning('预检需要选择一个合同分类作为样本')
    return
  }
  prechecking.value = true
  try {
    precheckResult.value = await RoutingApi.precheckRoutingRule({
      candidateVersionId: ruleForm.id,
      contractTypeId: ruleForm.contractTypeId,
      ownerDeptId: ruleForm.condition.ownerDeptId,
      amount: ruleForm.condition.minAmount ?? ruleForm.condition.maxAmount,
      currency: ruleForm.condition.currency
    })
  } finally {
    prechecking.value = false
  }
}

const saveDraft = async () => {
  if (!(await ruleFormRef.value?.validate())) return
  if (
    ruleForm.condition.minAmount != null &&
    ruleForm.condition.maxAmount != null &&
    ruleForm.condition.minAmount > ruleForm.condition.maxAmount
  ) {
    message.warning('最低金额不能大于最高金额')
    return
  }
  saving.value = true
  try {
    await RoutingApi.saveRoutingRuleDraft({
      ...ruleForm,
      condition: { ...ruleForm.condition }
    })
    message.success('业务单据流程配置草稿已保存')
    dialogVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const handlePublish = async (row: RoutingApi.RoutingRuleVO) => {
  await message.confirm(
    `发布配置“${row.name}”V${row.versionNo}？服务端会校验零命中、多命中和流程定义有效性。`
  )
  await RoutingApi.publishRoutingRule(row.id)
  message.success('业务单据流程配置已发布')
  await getList()
}

const handleDisable = async (row: RoutingApi.RoutingRuleVO) => {
  await message.confirm(`停用配置“${row.name}”后，新提交将不再命中该版本。确认继续？`)
  await RoutingApi.disableRoutingRule(row.id)
  message.success('业务单据流程配置已停用')
  await getList()
}

onMounted(async () => {
  const [typesResult, deptsResult, definitionsResult] = await Promise.allSettled([
    ContractTypeApi.getContractTypeSimpleList(),
    DeptApi.getSimpleDeptList(),
    DefinitionApi.getSimpleProcessDefinitionList()
  ])
  contractTypes.value = typesResult.status === 'fulfilled' ? typesResult.value || [] : []
  deptList.value = deptsResult.status === 'fulfilled' ? deptsResult.value || [] : []
  processDefinitions.value =
    definitionsResult.status === 'fulfilled'
      ? (definitionsResult.value || []).map((item: any) => ({
          key: item.key,
          name: item.name || item.key,
          version: item.version
        }))
      : []
  await getList()
})
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-header p,
.field-tip,
.precheck-box p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.condition-summary {
  display: flex;
  font-size: 13px;
  color: var(--el-text-color-regular);
  flex-wrap: wrap;
  gap: 4px 10px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.precheck-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

@media (width <= 900px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
