<template>
  <div>
    <div class="mb-10px flex flex-wrap items-center justify-between gap-8px">
      <div class="text-12px text-[var(--el-text-color-secondary)]">
        提交前必须维护承诺，或明确确认本合同无重大承诺。
      </div>
      <div v-if="editable" class="flex gap-8px">
        <el-button :disabled="!currentRevisionId" @click="confirmNone">确认无重大承诺</el-button>
        <el-button type="primary" :disabled="!currentRevisionId" @click="openCreate">
          <Icon icon="ep:plus" class="mr-5px" /> 新增承诺
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="error"
      class="mb-10px"
      type="warning"
      :closable="false"
      show-icon
      title="重大承诺暂时无法加载"
    >
      <template #default
        ><el-link type="primary" :underline="false" @click="getList">重试</el-link></template
      >
    </el-alert>

    <el-table v-loading="loading" :data="list" size="small">
      <el-table-column label="类别" prop="category" width="105" />
      <el-table-column label="承诺内容" prop="content" min-width="180" show-overflow-tooltip />
      <el-table-column label="责任人" prop="ownerUserName" width="100" />
      <el-table-column label="期限" prop="dueDate" width="110" />
      <el-table-column label="风险" width="82">
        <template #default="scope"
          ><el-tag size="small" :type="riskType(scope.row.riskLevel)">{{
            riskText(scope.row.riskLevel)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column v-if="editable" label="操作" align="center" width="108">
        <template #default="scope">
          <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
          <el-button link type="danger" @click="remove(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !error && list.length === 0"
      description="尚未声明重大承诺"
      :image-size="60"
    />

    <Dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑重大承诺' : '新增重大承诺'"
      width="560px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="85px">
        <el-form-item label="类别" prop="category"
          ><el-input v-model="form.category" maxlength="50" placeholder="例如：付款、交付、保密"
        /></el-form-item>
        <el-form-item label="承诺内容" prop="content"
          ><el-input
            v-model="form.content"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
        /></el-form-item>
        <el-form-item label="责任人" prop="ownerUserId">
          <el-select
            v-model="form.ownerUserId"
            filterable
            class="w-full"
            placeholder="请选择责任人"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.nickname"
              :value="String(user.id)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期限" prop="dueDate"
          ><el-date-picker
            v-model="form.dueDate"
            type="date"
            value-format="YYYY-MM-DD"
            class="!w-full"
            placeholder="请选择期限"
        /></el-form-item>
        <el-form-item label="风险等级" prop="riskLevel">
          <el-select v-model="form.riskLevel" class="w-full"
            ><el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option
              label="高"
              value="HIGH"
          /></el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as CommitmentApi from '@/api/clm/commitment'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ClmContractCommitmentPanel' })

const props = defineProps<{
  contractId: string | number
  currentRevisionId?: string
  editable: boolean
}>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const error = ref(false)
const list = ref<CommitmentApi.ContractCommitmentVO[]>([])
const userList = ref<UserApi.UserVO[]>([])
const dialogVisible = ref(false)
const formRef = ref()
const emptyForm = () => ({
  id: undefined as string | undefined,
  category: '',
  content: '',
  ownerUserId: '',
  dueDate: '',
  riskLevel: 'MEDIUM'
})
const form = reactive(emptyForm())
const rules = {
  category: [{ required: true, message: '请输入承诺类别', trigger: 'blur' }],
  content: [{ required: true, message: '请输入承诺内容', trigger: 'blur' }],
  ownerUserId: [{ required: true, message: '请选择责任人', trigger: 'change' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }]
}

const getList = async () => {
  if (!props.contractId) return
  loading.value = true
  error.value = false
  try {
    list.value = (await CommitmentApi.getContractCommitments(props.contractId)) || []
  } catch {
    list.value = []
    error.value = true
  } finally {
    loading.value = false
  }
}

const loadUsers = async () => {
  if (userList.value.length) return
  try {
    userList.value = (await UserApi.getSimpleUserList()) || []
  } catch {
    userList.value = []
  }
}
const openCreate = async () => {
  Object.assign(form, emptyForm())
  await loadUsers()
  dialogVisible.value = true
}
const openEdit = async (row: CommitmentApi.ContractCommitmentVO) => {
  Object.assign(form, { ...row })
  await loadUsers()
  dialogVisible.value = true
}

const submit = async () => {
  if (!(await formRef.value?.validate().catch(() => false)) || !props.currentRevisionId) return
  saving.value = true
  try {
    const payload: CommitmentApi.ContractCommitmentVO = {
      ...form,
      contractId: String(props.contractId),
      baseRevisionId: props.currentRevisionId
    }
    if (form.id) await CommitmentApi.updateContractCommitment(payload)
    else await CommitmentApi.createContractCommitment(payload)
    message.success('重大承诺已保存，并形成新修订')
    dialogVisible.value = false
    await getList()
    emit('changed')
  } finally {
    saving.value = false
  }
}

const remove = async (row: CommitmentApi.ContractCommitmentVO) => {
  if (!row.id || !props.currentRevisionId) return
  try {
    await message.delConfirm()
    await CommitmentApi.deleteContractCommitment(row.id, props.currentRevisionId)
    message.success('重大承诺已删除，并形成新修订')
    await getList()
    emit('changed')
  } catch {}
}

const confirmNone = async () => {
  if (!props.currentRevisionId) return
  try {
    await message.confirm('确认当前修订不存在需要声明的重大承诺？')
    await CommitmentApi.confirmNoContractCommitment(props.contractId, props.currentRevisionId)
    message.success('已确认无重大承诺，并形成新修订')
    await getList()
    emit('changed')
  } catch {}
}

const riskText = (risk?: string) =>
  ({ LOW: '低', MEDIUM: '中', HIGH: '高' })[risk || ''] || risk || '-'
const riskType = (risk?: string): 'success' | 'warning' | 'danger' | 'info' =>
  (({ LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' })[risk || ''] as
    | 'success'
    | 'warning'
    | 'danger') || 'info'

watch(() => props.contractId, getList, { immediate: true })
defineExpose({ reload: getList })
</script>
