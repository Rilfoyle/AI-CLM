<template>
  <div v-loading="loading" class="py-20px">
    <el-alert
      v-if="error"
      type="warning"
      :closable="false"
      show-icon
      title="无法定位对应的合同审批上下文"
    >
      <template #default>
        <el-button
          class="mt-8px"
          type="primary"
          @click="router.push('/clm/approval-management/approval')"
          >进入合同审批中心</el-button
        >
      </template>
    </el-alert>
  </div>
</template>

<script lang="ts" setup>
import * as WorkflowBindingApi from '@/api/clm/workflowBinding'

defineOptions({ name: 'ClmContractBpmView' })
const props = defineProps<{ id?: number | string }>()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref(false)

const redirectToApproval = async () => {
  const taskId = String(route.query.taskId || '')
  if (taskId) {
    await router.replace({ name: 'ClmApprovalTask', params: { taskId } })
    return
  }
  const routeId = Array.isArray(route.query.id) ? route.query.id[0] : route.query.id
  const bindingId = props.id || routeId
  if (!bindingId) {
    error.value = true
    return
  }
  loading.value = true
  try {
    const detail = await WorkflowBindingApi.getWorkflowBinding(bindingId)
    const contractId = detail?.binding?.contractId || detail?.contract?.id
    if (!contractId) throw new Error('contract id missing')
    await router.replace({ name: 'ClmApprovalHistory', params: { contractId } })
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

watch(() => [props.id, route.query.taskId], redirectToApproval, { immediate: true })
</script>
