<template>
  <Dialog v-model="visible" title="发起法务协同" width="540px">
    <el-alert
      class="mb-12px"
      type="info"
      :closable="false"
      show-icon
      title="协同将绑定当前修订；合同形成新修订后，旧协同结论会自动过期。"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="法务处理人" prop="legalUserId">
        <el-select
          v-model="form.legalUserId"
          filterable
          class="w-full"
          placeholder="请选择法务处理人"
        >
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="user.nickname"
            :value="String(user.id)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="协同说明" prop="reason"
        ><el-input
          v-model="form.reason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="请说明需要法务关注的事项"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">发起协同</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as CollaborationApi from '@/api/clm/collaboration'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ClmCollaborationStartDialog' })
const emit = defineEmits<{ success: [caseId: string] }>()
const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const formRef = ref()
const users = ref<UserApi.UserVO[]>([])
const form = reactive({ contractId: '', revisionId: '', legalUserId: '', reason: '' })
const rules = {
  legalUserId: [{ required: true, message: '请选择法务处理人', trigger: 'change' }],
  reason: [{ required: true, message: '请填写协同说明', trigger: 'blur' }]
}

const open = async (contractId: string, revisionId: string) => {
  Object.assign(form, { contractId, revisionId, legalUserId: '', reason: '' })
  visible.value = true
  if (!users.value.length) {
    try {
      users.value = (await UserApi.getSimpleUserList()) || []
    } catch {
      users.value = []
    }
  }
}

const submit = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  submitting.value = true
  try {
    const caseId = await CollaborationApi.startCollaboration({ ...form })
    message.success('法务协同已发起')
    visible.value = false
    emit('success', caseId)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
