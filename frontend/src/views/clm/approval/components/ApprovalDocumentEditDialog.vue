<template>
  <Dialog v-model="visible" title="上传修订正文" width="560px">
    <el-alert
      class="mb-14px"
      type="warning"
      :closable="false"
      show-icon
      title="正文会保存为新的不可变修订；若正文被节点政策认定为重大字段，原审批将取消并完整重走。"
    />
    <el-form label-width="90px">
      <el-form-item label="修订文件" required>
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="false"
          :limit="1"
          :accept="acceptedTypes"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <Icon icon="ep:upload-filled" :size="38" />
          <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 doc、docx、pdf，单次上传一个文件</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-form-item label="变更说明" required>
        <el-input
          v-model="changeReason"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="说明正文修改内容和原因"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :disabled="!file || !changeReason.trim()"
        :loading="submitting"
        @click="submit"
      >
        上传并保存修订
      </el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { UploadFile, UploadInstance } from 'element-plus'
import * as ApprovalApi from '@/api/clm/approval'

defineOptions({ name: 'ClmApprovalDocumentEditDialog' })
const emit = defineEmits<{
  completed: [result: ApprovalApi.ApprovalEditRespVO]
}>()
const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const uploadRef = ref<UploadInstance>()
const task = ref<ApprovalApi.ApprovalTaskDetailVO>()
const file = ref<File>()
const changeReason = ref('')
const acceptedTypes = '.doc,.docx,.pdf'
const allowedExtensions = ['doc', 'docx', 'pdf']

const handleFileChange = (uploadFile: UploadFile) => {
  const raw = uploadFile.raw
  const extension = uploadFile.name.split('.').pop()?.toLowerCase() || ''
  if (!raw || !allowedExtensions.includes(extension)) {
    file.value = undefined
    uploadRef.value?.clearFiles()
    message.warning('仅支持 doc、docx、pdf 文件')
    return
  }
  file.value = raw
}
const handleFileRemove = () => {
  file.value = undefined
}

const open = (nextTask: ApprovalApi.ApprovalTaskDetailVO) => {
  task.value = nextTask
  file.value = undefined
  changeReason.value = ''
  uploadRef.value?.clearFiles()
  visible.value = true
}

const submit = async () => {
  if (!task.value || !file.value || !changeReason.value.trim()) return
  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('taskId', task.value.taskId)
    formData.append(
      'requestId',
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}-${Math.random().toString(16).slice(2)}`
    )
    formData.append('contractId', task.value.contractId)
    formData.append('baseRevisionId', task.value.currentRevisionId)
    formData.append('changeReason', changeReason.value.trim())
    formData.append('file', file.value)
    const result = await ApprovalApi.editApprovalTaskDocument(formData)
    visible.value = false
    if (result.majorChange) {
      message.warning(`正文重大变更已保存为 R${result.revisionNo}，原审批已取消并完整重走`)
    } else {
      message.success(`修订正文已保存为 R${result.revisionNo}`)
    }
    emit('completed', result)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
