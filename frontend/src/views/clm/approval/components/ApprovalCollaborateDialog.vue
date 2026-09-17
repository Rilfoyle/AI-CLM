<template>
  <Dialog v-model="visible" :title="title" width="560px">
    <el-alert class="mb-14px" type="info" :closable="false" show-icon :title="description" />
    <el-form label-width="90px">
      <el-form-item
        :label="action === 'TRANSFER' ? '新审批人' : action === 'COPY' ? '抄送人' : '加审人'"
      >
        <el-select
          v-if="action === 'TRANSFER'"
          v-model="assigneeUserId"
          filterable
          class="w-full"
          placeholder="请选择新审批人"
        >
          <el-option
            v-for="item in users"
            :key="item.id"
            :label="`${item.nickname}（${item.deptName || '未分配部门'}）`"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-else
          v-model="userIds"
          multiple
          filterable
          collapse-tags
          class="w-full"
          :placeholder="action === 'COPY' ? '请选择抄送人' : '请选择加审人'"
        >
          <el-option
            v-for="item in users"
            :key="item.id"
            :label="`${item.nickname}（${item.deptName || '未分配部门'}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="action === 'ADD_SIGN'" label="加审位置">
        <el-radio-group v-model="signType">
          <el-radio value="before">审批前加审</el-radio>
          <el-radio value="after">审批后加审</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="原因">
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          :placeholder="action === 'COPY' ? '可选，说明抄送原因' : '请填写操作原因'"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!valid" :loading="submitting" @click="submit">
        确认{{ title }}
      </el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ApprovalApi from '@/api/clm/approval'
import * as UserApi from '@/api/system/user'

type CollaborationAction = 'TRANSFER' | 'COPY' | 'ADD_SIGN'
defineOptions({ name: 'ClmApprovalCollaborateDialog' })
const emit = defineEmits<{ completed: [action: CollaborationAction] }>()
const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const action = ref<CollaborationAction>('TRANSFER')
const taskId = ref('')
const users = ref<UserApi.UserVO[]>([])
const assigneeUserId = ref<number>()
const userIds = ref<number[]>([])
const signType = ref<'before' | 'after'>('before')
const reason = ref('')

const title = computed(() => ({ TRANSFER: '转交', COPY: '抄送', ADD_SIGN: '加审' })[action.value])
const description = computed(
  () =>
    ({
      TRANSFER: '转交后当前任务由新审批人继续处理，你将不能再处理该任务。',
      COPY: '抄送只增加知会记录，不改变当前审批人和审批顺序。',
      ADD_SIGN: '加审会改变当前运行中的审批任务，请确认加审位置。'
    })[action.value]
)
const valid = computed(() => {
  const hasUsers = action.value === 'TRANSFER' ? !!assigneeUserId.value : userIds.value.length > 0
  return hasUsers && (action.value === 'COPY' || !!reason.value.trim())
})

const loadUsers = async () => {
  try {
    users.value = (await UserApi.getSimpleUserList()) || []
  } catch {
    users.value = []
  }
}

const open = async (id: string, nextAction: CollaborationAction) => {
  taskId.value = id
  action.value = nextAction
  assigneeUserId.value = undefined
  userIds.value = []
  signType.value = 'before'
  reason.value = ''
  visible.value = true
  if (!users.value.length) await loadUsers()
}

const submit = async () => {
  if (!valid.value) return
  submitting.value = true
  try {
    if (action.value === 'TRANSFER') {
      await ApprovalApi.transferApprovalTask({
        id: taskId.value,
        assigneeUserId: assigneeUserId.value!,
        reason: reason.value.trim()
      })
    } else if (action.value === 'COPY') {
      await ApprovalApi.copyApprovalTask({
        id: taskId.value,
        copyUserIds: userIds.value,
        reason: reason.value.trim() || undefined
      })
    } else {
      await ApprovalApi.addSignApprovalTask({
        id: taskId.value,
        userIds: userIds.value,
        type: signType.value,
        reason: reason.value.trim()
      })
    }
    visible.value = false
    message.success(`${title.value}成功`)
    emit('completed', action.value)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
