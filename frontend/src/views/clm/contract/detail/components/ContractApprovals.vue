<template>
  <el-table v-loading="loading" :data="list" border stripe>
    <el-table-column label="编号" align="center" prop="id" width="80" />
    <el-table-column
      label="流程 Key"
      align="center"
      prop="processDefinitionKey"
      min-width="160"
      show-overflow-tooltip
    />
    <el-table-column
      label="流程实例"
      align="center"
      prop="processInstanceId"
      min-width="200"
      show-overflow-tooltip
    >
      <template #default="scope">{{ scope.row.processInstanceId || '-' }}</template>
    </el-table-column>
    <el-table-column label="绑定版本" align="center" width="100">
      <template #default="scope">
        <span v-if="scope.row.documentVersionNo">v{{ scope.row.documentVersionNo }}</span>
        <span v-else>-</span>
      </template>
    </el-table-column>
    <el-table-column label="SHA-256" align="center" width="140">
      <template #default="scope">
        <span class="font-mono" :title="scope.row.checksumSha256">
          {{ shortSha(scope.row.checksumSha256) }}
        </span>
      </template>
    </el-table-column>
    <el-table-column label="状态" align="center" width="100">
      <template #default="scope">
        <dict-tag :type="DICT_TYPE.CLM_WORKFLOW_BINDING_STATUS" :value="scope.row.status" />
      </template>
    </el-table-column>
    <el-table-column label="结果说明" prop="resultReason" min-width="160" show-overflow-tooltip>
      <template #default="scope">{{ scope.row.resultReason || '-' }}</template>
    </el-table-column>
    <el-table-column label="提交人" align="center" prop="creatorName" width="110" />
    <el-table-column
      label="提交时间"
      align="center"
      prop="createTime"
      width="170"
      :formatter="dateFormatter"
    />
    <el-table-column
      label="结束时间"
      align="center"
      prop="finishedTime"
      width="170"
      :formatter="dateFormatter"
    />
    <el-table-column label="操作" align="center" width="100" fixed="right">
      <template #default="scope">
        <el-button
          v-if="scope.row.processInstanceId"
          link
          type="primary"
          @click="handleViewProcess(scope.row)"
        >
          查看流程
        </el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ContractApi from '@/api/clm/contract'
import * as WorkflowBindingApi from '@/api/clm/workflowBinding'

defineOptions({ name: 'ClmContractApprovals' })

const props = defineProps<{ contract: ContractApi.ContractVO }>()
defineEmits<{ refresh: [] }>()

const { push } = useRouter()

const loading = ref(false)
const list = ref<WorkflowBindingApi.WorkflowBindingVO[]>([])

/** SHA-256 缩略 */
const shortSha = (sha?: string) => (sha ? sha.substring(0, 12) + '…' : '-')

/** 加载流程绑定列表 */
const getList = async () => {
  if (!props.contract.id) return
  loading.value = true
  try {
    list.value = (await WorkflowBindingApi.getWorkflowBindingList(props.contract.id)) || []
  } finally {
    loading.value = false
  }
}

/** 查看流程 */
const handleViewProcess = (row: WorkflowBindingApi.WorkflowBindingVO) => {
  push({ name: 'BpmProcessInstanceDetail', query: { id: row.processInstanceId } })
}

watch(
  () => [props.contract.id, props.contract.updateTime, props.contract.approvalStatus],
  () => {
    getList()
  },
  { immediate: true }
)
</script>
