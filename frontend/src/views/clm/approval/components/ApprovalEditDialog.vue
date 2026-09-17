<template>
  <Dialog v-model="visible" title="审批中编辑合同" width="720px">
    <el-alert
      class="mb-14px"
      type="warning"
      :closable="false"
      show-icon
      title="本次保存只调用 CLM 审批编辑接口；重大字段变更会取消原业务单并从头重新审批。"
    >
      <template #default>
        <div v-if="policy.reason">节点政策：{{ policy.reason }}</div>
        <div v-else>当前节点可编辑：{{ editableFieldText }}</div>
      </template>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
      <el-form-item label="合同名称" prop="name">
        <el-input v-model="form.name" :disabled="!fieldEditable('name')" maxlength="200" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="14">
          <el-form-item label="合同金额" prop="amount">
            <el-input-number
              v-model="form.amount"
              :disabled="!fieldEditable('amount')"
              :min="0"
              :precision="2"
              :controls="false"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label-width="0" prop="currency">
            <el-select
              v-model="form.currency"
              :disabled="!fieldEditable('currency')"
              class="w-full"
            >
              <el-option label="CNY" value="CNY" />
              <el-option label="USD" value="USD" />
              <el-option label="EUR" value="EUR" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker
              v-model="form.startDate"
              :disabled="!fieldEditable('startDate')"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结束日期" prop="endDate">
            <el-date-picker
              v-model="form.endDate"
              :disabled="!fieldEditable('endDate')"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="我方主体" prop="ourPartyId">
        <el-select
          v-model="form.ourPartyId"
          :disabled="!fieldEditable('parties')"
          filterable
          class="w-full"
          placeholder="请选择我方主体"
        >
          <el-option
            v-for="item in ourPartyList"
            :key="item.id"
            :label="item.name"
            :value="String(item.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="相对方" prop="counterpartyIds">
        <el-select
          v-model="form.counterpartyIds"
          :disabled="!fieldEditable('parties')"
          multiple
          filterable
          collapse-tags
          class="w-full"
          placeholder="请选择相对方"
        >
          <el-option
            v-for="item in counterpartyList"
            :key="item.id"
            :label="item.name"
            :value="String(item.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="合同说明" prop="description">
        <el-input
          v-model="form.description"
          :disabled="!fieldEditable('description')"
          type="textarea"
          :rows="2"
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>
      <el-divider content-position="left">扩展字段</el-divider>
      <ApprovalCustomFields
        ref="customFieldsRef"
        v-model="customData"
        :type-version-id="currentTask?.contract?.typeVersionId"
        :editable="fieldEditable('customData')"
      />
      <el-form-item label="变更说明" prop="changeReason">
        <el-input
          v-model="form.changeReason"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="说明本次审批中修改的原因"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存审批修订</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ApprovalApi from '@/api/clm/approval'
import * as ContractApi from '@/api/clm/contract'
import * as PartyApi from '@/api/clm/party'
import ApprovalCustomFields from './ApprovalCustomFields.vue'

defineOptions({ name: 'ClmApprovalEditDialog' })
const emit = defineEmits<{
  completed: [result: ApprovalApi.ApprovalEditRespVO]
}>()
const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const formRef = ref()
const customFieldsRef = ref<InstanceType<typeof ApprovalCustomFields>>()
const currentTask = ref<ApprovalApi.ApprovalTaskDetailVO>()
const ourPartyList = ref<PartyApi.PartySimpleVO[]>([])
const counterpartyList = ref<PartyApi.PartySimpleVO[]>([])
const customData = ref<Record<string, any>>({})

const form = reactive({
  name: '',
  amount: undefined as number | undefined,
  currency: 'CNY',
  startDate: '',
  endDate: '',
  ourPartyId: '',
  counterpartyIds: [] as string[],
  description: '',
  changeReason: ''
})
const rules = {
  name: [{ required: true, message: '合同名称不能为空', trigger: 'blur' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
  ourPartyId: [{ required: true, message: '请选择我方主体', trigger: 'change' }],
  counterpartyIds: [
    { required: true, type: 'array', min: 1, message: '至少选择一个相对方', trigger: 'change' }
  ],
  changeReason: [{ required: true, message: '请填写变更说明', trigger: 'blur' }]
}

const policy = computed(
  () =>
    currentTask.value?.nodeEditPolicy ||
    currentTask.value?.editPolicy || { canEdit: false, editableFields: [], reason: '节点不可编辑' }
)
const editableFields = computed(() => policy.value.editableFields || [])
const fieldLabels: Record<string, string> = {
  name: '合同名称',
  amount: '合同金额',
  currency: '币种',
  startDate: '开始日期',
  endDate: '结束日期',
  description: '合同说明',
  parties: '参与方',
  customData: '扩展字段'
}
const editableFieldText = computed(() =>
  editableFields.value.includes('*')
    ? '全部结构化字段'
    : editableFields.value.map((field) => fieldLabels[field] || field).join('、') || '无'
)
const fieldEditable = (field: string) =>
  !!policy.value.canEdit &&
  (editableFields.value.includes('*') || editableFields.value.includes(field))

const extractParties = (task: ApprovalApi.ApprovalTaskDetailVO) => {
  const parties = (task.contract?.parties || task.parties || []) as Array<
    ContractApi.ContractPartyVO & Record<string, unknown>
  >
  return {
    ourPartyId: String(parties.find((item) => item.roleCode === 'OUR_SIDE')?.partyId || ''),
    counterpartyIds: parties
      .filter((item) => item.roleCode === 'COUNTERPARTY')
      .map((item) => String(item.partyId))
      .filter(Boolean)
  }
}

const loadParties = async () => {
  try {
    const [ours, counters] = await Promise.all([
      PartyApi.getPartySimpleList(true),
      PartyApi.getPartySimpleList(false)
    ])
    ourPartyList.value = ours || []
    counterpartyList.value = counters || []
  } catch {
    ourPartyList.value = []
    counterpartyList.value = []
  }
}

const open = async (task: ApprovalApi.ApprovalTaskDetailVO) => {
  currentTask.value = task
  customData.value = { ...(task.contract?.customData || {}) }
  const parties = extractParties(task)
  Object.assign(form, {
    name: task.contract?.title || '',
    amount: task.contract?.amount,
    currency: task.contract?.currency || 'CNY',
    startDate: task.contract?.startDate || task.contract?.effectiveDate || '',
    endDate: task.contract?.endDate || task.contract?.expiryDate || '',
    ourPartyId: parties.ourPartyId,
    counterpartyIds: parties.counterpartyIds,
    description: task.contract?.description || '',
    changeReason: ''
  })
  visible.value = true
  await loadParties()
  await nextTick()
  formRef.value?.clearValidate()
}

const submit = async () => {
  const task = currentTask.value
  if (!task || !(await formRef.value?.validate().catch(() => false))) return
  if (fieldEditable('customData')) {
    const customFieldsValid = await customFieldsRef.value?.validate()
    if (customFieldsValid !== true) {
      message.warning('请等待扩展字段加载完成并完善必填内容')
      return
    }
  }
  submitting.value = true
  try {
    const result = await ApprovalApi.editApprovalTask({
      taskId: task.taskId,
      requestId:
        typeof crypto !== 'undefined' && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      contractId: task.contractId,
      baseRevisionId: task.currentRevisionId,
      name: form.name.trim(),
      amount: form.amount,
      currency: form.currency,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      ourPartyId: form.ourPartyId,
      counterpartyIds: form.counterpartyIds,
      description: form.description.trim() || undefined,
      customData: fieldEditable('customData') ? { ...customData.value } : task.contract?.customData,
      changeReason: form.changeReason.trim()
    })
    visible.value = false
    if (result.majorChange) {
      message.warning(`重大变更已保存为 R${result.revisionNo}，原审批已取消并完整重走`)
    } else {
      message.success(`审批修订已保存为 R${result.revisionNo}`)
    }
    emit('completed', result)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
