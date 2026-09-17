<template>
  <Dialog v-model="visible" title="提交合同审批" width="600px">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="提交会冻结当前修订并首次分配永久合同编号；退回或撤回后重提仍沿用该编号。"
      class="mb-14px"
    />

    <el-descriptions :column="1" border class="mb-14px">
      <el-descriptions-item label="合同名称">{{ contract.title || '-' }}</el-descriptions-item>
      <el-descriptions-item label="当前修订">
        <el-tag v-if="revisionId" type="success"
          >R{{ contract.currentRevisionNo || revisionId }}</el-tag
        >
        <el-tag v-else type="danger">尚无可提交修订</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="合同正文">
        <span v-if="hasDocument">已就绪</span>
        <el-tag v-else type="danger">尚未上传正文</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="合同编号">{{
        contract.contractNo || '提交成功后分配'
      }}</el-descriptions-item>
    </el-descriptions>

    <el-alert
      v-if="!canSubmit"
      class="mb-14px"
      type="warning"
      :closable="false"
      show-icon
      title="请先完成合同正文和当前修订后再提交"
    />

    <el-form label-width="80px">
      <el-form-item label="提交说明">
        <el-input
          v-model="remark"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="可选，填写本次提交需要审批人关注的事项"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit"
        >确认提交</el-button
      >
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmSubmitApprovalDialog' })
const emit = defineEmits<{ success: [result: ContractApi.ContractSubmitRespVO] }>()
const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const contract = ref<ContractApi.ContractVO>({} as ContractApi.ContractVO)
const revisionId = ref('')
const remark = ref('')
const hasDocument = computed(
  () => !!contract.value.currentDocumentVersionId || !!contract.value.currentDocumentVersion
)
const canSubmit = computed(() => !!contract.value.id && !!revisionId.value && hasDocument.value)

const open = (data: ContractApi.ContractVO, currentRevisionId: string) => {
  contract.value = data
  revisionId.value = currentRevisionId
  remark.value = ''
  visible.value = true
}

const requestId = () =>
  typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`

const submit = async () => {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const result = await ContractApi.submitContract({
      id: contract.value.id!,
      baseRevisionId: revisionId.value,
      submitRequestId: requestId(),
      remark: remark.value.trim() || undefined
    })
    message.success(`合同已提交审批${result.contractNo ? `，编号 ${result.contractNo}` : ''}`)
    visible.value = false
    emit('success', result)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
