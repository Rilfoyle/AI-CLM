<template>
  <div>
    <el-alert
      v-if="error"
      class="mb-10px"
      type="warning"
      :closable="false"
      show-icon
      title="业务时间线暂时无法加载"
    >
      <template #default
        ><el-link type="primary" :underline="false" @click="getList">重试</el-link></template
      >
    </el-alert>
    <div v-loading="loading">
      <el-timeline v-if="list.length">
        <el-timeline-item
          v-for="item in list"
          :key="item.id"
          :timestamp="formatTime(item.occurredAt)"
          placement="top"
        >
          <div class="rounded-4px border border-[var(--el-border-color-lighter)] p-10px">
            <div class="flex items-center justify-between gap-8px">
              <span class="font-500">{{ actionText(item.action) }}</span>
              <span class="text-12px text-[var(--el-text-color-secondary)]">{{
                item.actorName || '系统'
              }}</span>
            </div>
            <div
              v-if="businessDetail(item.detailJson)"
              class="mt-5px text-12px text-[var(--el-text-color-secondary)]"
            >
              {{ businessDetail(item.detailJson) }}
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else-if="!error" description="暂无业务事件" :image-size="60" />
    </div>
    <Pagination
      v-if="total > 0"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmContractAuditEvents' })
const props = defineProps<{ contract: ContractApi.ContractVO }>()
const loading = ref(false)
const error = ref(false)
const total = ref(0)
const list = ref<ContractApi.AuditEventVO[]>([])
const queryParams = reactive({ pageNo: 1, pageSize: 10 })

const actionText = (action?: string) => {
  const labels: Record<string, string> = {
    CREATE: '创建合同草稿',
    UPDATE: '更新合同信息',
    DOCUMENT_UPLOAD: '上传合同文件',
    REVISION_CREATE: '形成新修订',
    COLLABORATION_START: '发起法务协同',
    COLLABORATION_COMPLETE: '完成法务协同',
    COLLABORATION_REQUEST_CHANGE: '法务要求修改',
    SUBMIT: '提交合同审批',
    APPROVE: '审批同意',
    REJECT: '审批拒绝',
    RETURN: '审批退回',
    WITHDRAW: '发起人撤回',
    DELETE: '删除草稿',
    RESTORE: '恢复草稿'
  }
  return (action && labels[action]) || action || '合同事件'
}

const businessDetail = (json?: string) => {
  if (!json) return ''
  try {
    const data = JSON.parse(json) as Record<string, unknown>
    const fields = [
      ['revisionNo', '修订'],
      ['status', '状态'],
      ['reason', '原因'],
      ['comment', '说明'],
      ['message', '说明'],
      ['contractNo', '合同编号']
    ] as const
    return fields
      .filter(([key]) => data[key] !== undefined && data[key] !== '')
      .map(([key, label]) => `${label}：${String(data[key])}`)
      .join('；')
  } catch {
    return ''
  }
}

const getList = async () => {
  if (!props.contract.id) return
  loading.value = true
  error.value = false
  try {
    const data = await ContractApi.getAuditEventPage({
      ...queryParams,
      contractId: props.contract.id
    })
    list.value = data?.list || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
    error.value = true
  } finally {
    loading.value = false
  }
}
const formatTime = (value: Date) => (value ? formatDate(value, 'YYYY-MM-DD HH:mm') : '-')

watch(
  () => [props.contract.id, props.contract.updateTime, props.contract.approvalStatus],
  () => {
    queryParams.pageNo = 1
    getList()
  },
  { immediate: true }
)
</script>
