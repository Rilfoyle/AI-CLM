<template>
  <div v-loading="loading" class="process-editor-page">
    <ContentWrap class="!mb-12px">
      <div class="editor-header">
        <div class="header-title">
          <el-button text circle aria-label="返回流程定义列表" @click="handleBack">
            <Icon icon="ep:arrow-left" :size="20" />
          </el-button>
          <div>
            <div class="title-line">
              <h2>{{ isCreate ? '新建流程定义' : model.name || '编辑流程图' }}</h2>
              <el-tag v-if="model.processDefinition?.version" type="success">
                已发布 V{{ model.processDefinition.version }}
              </el-tag>
              <el-tag v-else type="warning">未发布</el-tag>
            </div>
            <p>拖拽式配置审批人和分支条件；保存草稿不会影响正在运行的合同审批。</p>
          </div>
        </div>
        <div class="header-actions">
          <el-button @click="router.push('/clm/base-settings/page-layout')">
            页面布局配置
          </el-button>
          <el-button
            :loading="saving"
            @click="handleSave(false)"
            v-hasPermi="['clm:governance:process:update']"
          >
            保存草稿
          </el-button>
          <el-button
            type="primary"
            :loading="publishing"
            @click="handleSave(true)"
            v-hasPermi="['clm:governance:process:publish']"
          >
            保存并发布
          </el-button>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap class="!mb-12px">
      <el-form ref="formRef" :model="model" :rules="formRules" label-width="92px">
        <el-row :gutter="20">
          <el-col :xs="24" :md="8">
            <el-form-item label="流程名称" prop="name">
              <el-input
                v-model="model.name"
                maxlength="64"
                show-word-limit
                placeholder="例如：标准采购合同审批"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="流程编码" prop="key">
              <el-input
                v-model="model.key"
                :disabled="!isCreate"
                maxlength="64"
                placeholder="clm_contract_purchase"
                @blur="normalizeKey"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="流程说明" prop="description">
              <el-input
                v-model="model.description"
                maxlength="200"
                placeholder="适用范围和审批规则说明"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-alert type="info" :closable="false" show-icon>
        <template #title>
          条件分支可使用合同基础字段，以及所有启用且已发布合同分类中均存在并必填的
          {{ customFieldCount }}
          个通用扩展字段。合同使用哪套流程定义，由“业务单据流程配置”统一决定。
        </template>
      </el-alert>
      <el-alert
        v-if="publishIntent"
        class="mt-12px"
        type="warning"
        :closable="false"
        show-icon
        title="当前操作目标是发布：请完成节点配置后点击右上角“保存并发布”。"
      />
    </ContentWrap>

    <ContentWrap :body-style="{ padding: '0' }" class="designer-wrap">
      <div v-if="designerReady" class="designer-canvas">
        <SimpleProcessDesigner
          :model-name="model.name"
          :model-form-type="BpmModelFormType.CUSTOM"
          :start-user-ids="model.startUserIds"
          :start-dept-ids="model.startDeptIds"
          :external-form-fields="conditionFormFields"
          :allowed-node-types="allowedNodeTypes"
          :allowed-candidate-strategies="allowedCandidateStrategies"
          :allowed-approve-types="allowedApproveTypes"
          :allow-condition-expression="false"
          :allow-task-listeners="false"
          :allow-skip-expression="false"
          :allow-json-import="false"
          :allow-sign-setting="false"
          :allow-field-permission-setting="false"
          enable-custom-form-rules
          @success="handleDesignerChange"
        />
      </div>
      <el-skeleton v-else :rows="8" animated class="p-24px" />
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { SimpleProcessDesigner } from '@/components/SimpleProcessDesignerV2/src/'
import {
  ApproveType,
  CandidateStrategy,
  ConditionType,
  DEFAULT_CONDITION_GROUP_VALUE,
  NodeType,
  type SimpleFlowNode
} from '@/components/SimpleProcessDesignerV2/src/consts'
import { BpmModelFormType, BpmModelType } from '@/utils/constants'
import { getCurrentUserId } from '@/utils/auth'
import * as ContractTypeApi from '@/api/clm/contractType'
import * as ProcessApi from '@/api/clm/governance/process'
import { cloneDeep } from 'lodash-es'

defineOptions({ name: 'ClmGovernanceProcessEditor' })

const CONTRACT_PROCESS_PREFIX = 'clm_contract_'
const CONTRACT_CATEGORY = 'contract'
const CONTRACT_CREATE_PATH = '/clm/contract/create'
const CONTRACT_VIEW_PATH = '/clm/contract/bpm/index.vue'

const router = useRouter()
const route = useRoute()
const message = useMessage()

const modelId = computed(() => String(route.query.id || ''))
const isCreate = computed(() => !modelId.value)
const publishIntent = computed(() => route.query.publish === '1')
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const designerReady = ref(false)
const formRef = ref<FormInstance>()
const processData = ref<SimpleFlowNode>()

provide('processData', processData)

const createDefaultModel = (): ProcessApi.ContractProcessModelVO => ({
  key: CONTRACT_PROCESS_PREFIX,
  name: '',
  description: '',
  category: CONTRACT_CATEGORY,
  type: BpmModelType.SIMPLE,
  formType: BpmModelFormType.CUSTOM,
  formCustomCreatePath: CONTRACT_CREATE_PATH,
  formCustomViewPath: CONTRACT_VIEW_PATH,
  visible: true,
  startUserIds: [],
  startDeptIds: [],
  managerUserIds: [getCurrentUserId()].filter(Boolean),
  allowCancelRunningProcess: true,
  allowWithdrawTask: false
})

const model = ref<ProcessApi.ContractProcessModelVO>(createDefaultModel())
const formRules: FormRules = {
  name: [{ required: true, message: '请输入流程名称', trigger: 'blur' }],
  key: [
    { required: true, message: '请输入流程编码', trigger: 'blur' },
    {
      pattern: /^clm_contract_[a-z0-9][a-z0-9_]*$/,
      message: '流程编码须以 clm_contract_ 开头，仅使用小写字母、数字和下划线',
      trigger: 'blur'
    }
  ]
}

// 合同一期仅开放审批所需节点，避免把触发器、子流程等通用 BPM 能力暴露给业务管理员。
const allowedNodeTypes = [
  NodeType.USER_TASK_NODE,
  NodeType.COPY_TASK_NODE,
  NodeType.CONDITION_BRANCH_NODE,
  NodeType.PARALLEL_BRANCH_NODE,
  NodeType.INCLUSIVE_BRANCH_NODE
]
const allowedCandidateStrategies = [
  CandidateStrategy.ROLE,
  CandidateStrategy.DEPT_MEMBER,
  CandidateStrategy.DEPT_LEADER,
  CandidateStrategy.POST,
  CandidateStrategy.MULTI_LEVEL_DEPT_LEADER,
  CandidateStrategy.USER,
  CandidateStrategy.START_USER,
  CandidateStrategy.START_USER_DEPT_LEADER,
  CandidateStrategy.START_USER_MULTI_LEVEL_DEPT_LEADER,
  CandidateStrategy.USER_GROUP
]
const allowedApproveTypes = [ApproveType.USER]
const allowedCandidateStrategySet = new Set<number>(allowedCandidateStrategies)

/**
 * 合同审批不接受通用 BPM 的表达式、HTTP 回调等高级配置。
 * 历史模型加载和提交前都执行清理，避免“界面隐藏但旧值仍被再次提交”。
 */
const sanitizeContractProcessNode = (source: SimpleFlowNode): SimpleFlowNode => {
  const root = cloneDeep(source)
  const walk = (node?: SimpleFlowNode) => {
    if (!node) return

    delete node.taskCreateListener
    delete node.taskAssignListener
    delete node.taskCompleteListener
    delete node.skipExpression
    delete node.signEnable
    delete node.fieldsPermission

    if (node.type === NodeType.USER_TASK_NODE || node.type === NodeType.COPY_TASK_NODE) {
      if (!node.candidateStrategy || !allowedCandidateStrategySet.has(node.candidateStrategy)) {
        delete node.candidateStrategy
        delete node.candidateParam
        node.showText = ''
      }
    }

    if (node.type === NodeType.USER_TASK_NODE) {
      if (node.approveType && node.approveType !== ApproveType.USER) node.showText = ''
      node.approveType = ApproveType.USER
    }

    if (node.type === NodeType.CONDITION_NODE && node.conditionSetting) {
      const conditionSetting = node.conditionSetting
      if (conditionSetting.conditionType === ConditionType.EXPRESSION) {
        conditionSetting.conditionType = ConditionType.RULE
        if (!conditionSetting.defaultFlow) {
          conditionSetting.conditionGroups = cloneDeep(DEFAULT_CONDITION_GROUP_VALUE)
          node.showText = ''
        }
      }
      delete conditionSetting.conditionExpression
    }

    node.conditionNodes?.forEach(walk)
    walk(node.childNode)
  }
  walk(root)
  return root
}

const baseConditionFields = [
  { type: 'input', field: 'contractTitle', title: '合同名称', $required: true },
  { type: 'inputNumber', field: 'amount', title: '合同金额', $required: true },
  { type: 'inputNumber', field: 'ownerDeptId', title: '归属部门', $required: true },
  { type: 'input', field: 'contractTypeCode', title: '合同分类编码', $required: true },
  { type: 'input', field: 'contractNo', title: '合同编号', $required: true },
  { type: 'inputNumber', field: 'revisionId', title: '当前修订版本', $required: true }
]
const conditionFormFields = ref<string[]>(baseConditionFields.map((field) => JSON.stringify(field)))
const customFieldCount = ref(0)

type ConditionFieldRule = {
  type: string
  field: string
  title: string
  $required: true
}

const collectRequiredRuleFields = (rule: any, result: Map<string, ConditionFieldRule>) => {
  if (!rule || typeof rule !== 'object') return
  if (
    typeof rule.field === 'string' &&
    rule.field &&
    typeof rule.title === 'string' &&
    rule.title &&
    rule.$required
  ) {
    result.set(rule.field, {
      type: typeof rule.type === 'string' && rule.type ? rule.type : 'input',
      field: rule.field,
      title: rule.title,
      $required: true
    })
  }
  if (Array.isArray(rule.children)) {
    rule.children.forEach((child: any) => collectRequiredRuleFields(child, result))
  }
}

/** 仅暴露所有启用且已发布合同分类都具备的必填字段，保证流程定义可跨分类安全复用。 */
const loadConditionFields = async () => {
  const baseFieldNames = new Set(baseConditionFields.map((field) => field.field))
  conditionFormFields.value = baseConditionFields.map((field) => JSON.stringify(field))
  customFieldCount.value = 0
  try {
    const contractTypes = (await ContractTypeApi.getContractTypeSimpleList()) || []
    const publishedTypes = contractTypes.filter((item) => item.currentVersionId)
    if (publishedTypes.length === 0) return
    const versions = await Promise.allSettled(
      publishedTypes.map((item) => ContractTypeApi.getContractTypeVersion(item.currentVersionId))
    )
    if (versions.some((result) => result.status === 'rejected')) {
      message.warning('部分合同分类字段加载失败，本次仅开放合同基础字段配置条件')
      return
    }

    const requiredFieldsByType = versions.map((result) => {
      const requiredFields = new Map<string, ConditionFieldRule>()
      if (result.status !== 'fulfilled') return requiredFields
      ;(result.value.formFields || []).forEach((rawRule) => {
        try {
          collectRequiredRuleFields(JSON.parse(rawRule), requiredFields)
        } catch {
          // 单个历史字段规则损坏时按缺失处理，不能进入“所有类型通用”交集。
        }
      })
      return requiredFields
    })

    const [firstTypeFields, ...remainingTypeFields] = requiredFieldsByType
    const commonFields = [...firstTypeFields.entries()]
      .filter(
        ([field]) =>
          !baseFieldNames.has(field) &&
          remainingTypeFields.every((typeFields) => typeFields.has(field))
      )
      .map(([, rule]) => rule)

    conditionFormFields.value = [
      ...conditionFormFields.value,
      ...commonFields.map((field) => JSON.stringify(field))
    ]
    customFieldCount.value = commonFields.length
  } catch {
    message.warning('合同表单字段加载失败，仍可使用合同基础字段配置条件')
  }
}

const normalizeKey = () => {
  if (!isCreate.value) return
  const suffix = model.value.key
    .trim()
    .toLowerCase()
    .replace(/^clm_contract_/, '')
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/^_+|_+$/g, '')
  model.value.key = CONTRACT_PROCESS_PREFIX + suffix
}

const handleDesignerChange = (data: SimpleFlowNode) => {
  const sanitizedData = sanitizeContractProcessNode(data)
  processData.value = sanitizedData
  model.value.simpleModel = sanitizedData as unknown as Record<string, any>
}

const validateProcess = () => {
  if (!processData.value) throw new Error('请先配置审批流程')
  let approvalNodeCount = 0
  const unfinished: string[] = []
  const needShowText = new Set([
    NodeType.USER_TASK_NODE,
    NodeType.COPY_TASK_NODE,
    NodeType.CONDITION_NODE
  ])
  const walk = (node?: SimpleFlowNode) => {
    if (!node) return
    if (node.type === NodeType.USER_TASK_NODE) approvalNodeCount += 1
    if (needShowText.has(node.type) && !node.showText) unfinished.push(node.name || '未命名节点')
    node.conditionNodes?.forEach(walk)
    walk(node.childNode)
  }
  walk(processData.value)
  if (approvalNodeCount === 0) throw new Error('流程定义至少需要一个审批人节点')
  if (unfinished.length > 0) {
    throw new Error(`请完善节点：${[...new Set(unfinished)].join('、')}`)
  }
}

const buildPayload = (): ProcessApi.ContractProcessSaveVO => {
  const editableModel = { ...model.value }
  delete editableModel.modelVersion
  delete editableModel.processDefinition
  return {
    ...editableModel,
    id: model.value.id || modelId.value || undefined,
    key: model.value.key,
    name: model.value.name.trim(),
    description: model.value.description?.trim(),
    category: CONTRACT_CATEGORY,
    type: BpmModelType.SIMPLE,
    formType: BpmModelFormType.CUSTOM,
    formId: undefined,
    formCustomCreatePath: CONTRACT_CREATE_PATH,
    formCustomViewPath: CONTRACT_VIEW_PATH,
    visible: true,
    managerUserIds: Array.from(
      new Set([...(model.value.managerUserIds || []), getCurrentUserId()].filter(Boolean))
    ),
    allowCancelRunningProcess: true,
    allowWithdrawTask: false,
    expectedModelVersion: model.value.modelVersion,
    simpleModel: sanitizeContractProcessNode(processData.value!) as unknown as Record<string, any>
  }
}

const applyRemoteModel = (data: ProcessApi.ContractProcessModelVO) => {
  const simpleModel =
    typeof data.simpleModel === 'string' ? JSON.parse(data.simpleModel) : data.simpleModel
  model.value = {
    ...createDefaultModel(),
    ...data,
    managerUserIds: data.managerUserIds || [getCurrentUserId()],
    simpleModel
  }
  processData.value = sanitizeContractProcessNode(simpleModel as unknown as SimpleFlowNode)
}

/** 保存成功后重新读取模型版本，避免下一次保存误判或覆盖其他管理员的修改。 */
const reloadModel = async (id: string) => {
  applyRemoteModel(await ProcessApi.getContractProcess(id))
}

const persistModel = async () => {
  const payload = buildPayload()
  if (payload.id) {
    await ProcessApi.updateContractProcess(payload)
    await reloadModel(String(payload.id))
    return String(payload.id)
  }
  const id = await ProcessApi.createContractProcess(payload)
  await router.replace({ name: 'ClmGovernanceProcessEditor', query: { ...route.query, id } })
  await reloadModel(String(id))
  return String(id)
}

const handleSave = async (publish: boolean) => {
  if (saving.value || publishing.value) return
  try {
    await formRef.value?.validate()
    validateProcess()
    if (publish) {
      await message.confirm(
        model.value.processDefinition?.version
          ? '发布后将生成新的流程版本。正在运行的合同仍沿用原版本，确认发布吗？'
          : '发布后业务单据流程配置即可引用该定义，确认发布吗？'
      )
    }
    if (publish) publishing.value = true
    else saving.value = true
    const id = await persistModel()
    if (publish) {
      await ProcessApi.deployContractProcess(id)
      await reloadModel(id)
      message.success('流程定义已发布，新提交的合同将按业务单据流程配置使用新版本')
      handleBack()
    } else {
      message.success('流程定义草稿已保存')
    }
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    if (error?.message) message.warning(error.message)
  } finally {
    saving.value = false
    publishing.value = false
  }
}

const handleBack = () => router.push('/clm/approval-management/workflow-settings/process')

const init = async () => {
  loading.value = true
  designerReady.value = false
  try {
    await loadConditionFields()
    if (modelId.value) {
      applyRemoteModel(await ProcessApi.getContractProcess(modelId.value))
    } else {
      model.value = createDefaultModel()
      processData.value = undefined
    }
    designerReady.value = true
  } catch (error: any) {
    message.error(error?.message || '流程定义加载失败')
    handleBack()
  } finally {
    loading.value = false
  }
}

onMounted(init)
</script>

<style scoped>
.process-editor-page {
  min-height: calc(100vh - var(--top-tool-height) - var(--tags-view-height) - 32px);
}

.editor-header,
.header-title,
.title-line,
.header-actions {
  display: flex;
  align-items: center;
}

.editor-header {
  justify-content: space-between;
  gap: 20px;
}

.header-title {
  min-width: 0;
  gap: 10px;
}

.title-line {
  flex-wrap: wrap;
  gap: 10px;
}

.title-line h2 {
  margin: 0;
  font-size: 20px;
}

.header-title p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.header-actions {
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.designer-wrap {
  min-height: 620px;
}

.designer-canvas {
  min-height: 620px;
  padding: 20px 16px 48px;
  overflow: auto;
  background: var(--el-fill-color-lighter);
}

@media (width <= 900px) {
  .editor-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
