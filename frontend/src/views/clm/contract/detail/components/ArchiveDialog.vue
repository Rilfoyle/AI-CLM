<template>
  <Dialog v-model="dialogVisible" title="上传盖章件归档" width="560px">
    <el-alert
      class="mb-15px"
      type="info"
      :closable="false"
      show-icon
      title="归档后将以盖章扫描件生成冻结的正文定稿版本，合同进入「已签订」状态。"
    />
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="盖章扫描件" prop="fileName">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".pdf,.docx,.jpg,.jpeg,.png,.zip"
          :on-change="handleFileChange"
        >
          <el-button type="primary" plain>
            <Icon icon="ep:upload" class="mr-5px" /> 选择文件
          </el-button>
        </el-upload>
        <div v-if="file" class="mt-5px text-sm text-gray-500">
          <Icon icon="ep:document" class="mr-5px" />{{ file.name }} （{{
            formatFileSize(file.size)
          }}）
        </div>
        <div v-else class="mt-5px text-xs text-gray-400">
          支持 pdf/docx/jpg/png/zip，单文件不超过 50MB
        </div>
      </el-form-item>
      <el-form-item label="签订日期" prop="signDate">
        <el-date-picker
          v-model="formData.signDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="可选"
          class="w-full!"
        />
      </el-form-item>
      <el-form-item label="生效日期" prop="effectiveDate">
        <el-date-picker
          v-model="formData.effectiveDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="可选"
          class="w-full!"
        />
      </el-form-item>
      <el-form-item label="到期日期" prop="expiryDate">
        <el-date-picker
          v-model="formData.expiryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="可选"
          class="w-full!"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submitForm">确认归档</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormInstance, UploadFile as ElUploadFile } from 'element-plus'
import { formatFileSize } from '@/utils/file'
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmArchiveDialog' })

const emit = defineEmits<{ success: [] }>()
const message = useMessage()

const MAX_SIZE = 50 * 1024 * 1024

const dialogVisible = ref(false)
const submitting = ref(false)
const contractId = ref<number>()
const file = ref<File | null>(null)
const formRef = ref<FormInstance>()
const formData = reactive({
  fileName: '', // 仅用于必填校验展示
  signDate: '',
  effectiveDate: '',
  expiryDate: ''
})
const formRules = {
  fileName: [{ required: true, message: '请选择盖章扫描件', trigger: 'change' }]
}

/** 打开弹窗 */
const open = (contract: ContractApi.ContractVO) => {
  contractId.value = contract.id
  file.value = null
  formData.fileName = ''
  formData.signDate = contract.signDate || ''
  formData.effectiveDate = contract.effectiveDate || ''
  formData.expiryDate = contract.expiryDate || ''
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
defineExpose({ open })

/** 选择文件 */
const handleFileChange = (uploadFile: ElUploadFile) => {
  if (!uploadFile.raw) return
  if (uploadFile.raw.size > MAX_SIZE) {
    message.error('文件大小不能超过 50MB')
    return
  }
  file.value = uploadFile.raw
  formData.fileName = uploadFile.raw.name
  formRef.value?.clearValidate('fileName')
}

/** 提交归档 */
const submitForm = async () => {
  await formRef.value?.validate()
  if (!file.value || !contractId.value) return
  const data = new FormData()
  data.append('contractId', String(contractId.value))
  data.append('file', file.value)
  if (formData.signDate) data.append('signDate', formData.signDate)
  if (formData.effectiveDate) data.append('effectiveDate', formData.effectiveDate)
  if (formData.expiryDate) data.append('expiryDate', formData.expiryDate)
  submitting.value = true
  try {
    const res = await ContractApi.archiveContract(data)
    if (res && res.code !== undefined && res.code !== 0 && res.code !== 200) {
      message.error(res.msg || '归档失败')
      return
    }
    message.success('归档成功，合同已签订')
    dialogVisible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
