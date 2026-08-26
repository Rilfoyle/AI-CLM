<template>
  <el-table v-loading="loading" :data="list" border stripe>
    <el-table-column
      label="时间"
      align="center"
      prop="occurredAt"
      width="170"
      :formatter="dateFormatter"
    />
    <el-table-column label="动作" align="center" width="140">
      <template #default="scope">
        <dict-tag :type="DICT_TYPE.CLM_AUDIT_ACTION" :value="scope.row.action" />
      </template>
    </el-table-column>
    <el-table-column label="操作人" align="center" prop="actorName" width="120">
      <template #default="scope">{{ scope.row.actorName || '-' }}</template>
    </el-table-column>
    <el-table-column label="对象" align="center" width="160">
      <template #default="scope">
        {{ scope.row.aggregateType }} #{{ scope.row.aggregateId }}
      </template>
    </el-table-column>
    <el-table-column label="明细" min-width="300" show-overflow-tooltip>
      <template #default="scope">
        <span class="font-mono text-xs">{{ prettyDetail(scope.row.detailJson) }}</span>
      </template>
    </el-table-column>
  </el-table>
  <Pagination
    :total="total"
    v-model:page="queryParams.pageNo"
    v-model:limit="queryParams.pageSize"
    @pagination="getList"
  />
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ContractApi from '@/api/clm/contract'

defineOptions({ name: 'ClmContractAuditEvents' })

const props = defineProps<{ contract: ContractApi.ContractVO }>()
defineEmits<{ refresh: [] }>()

const loading = ref(false)
const total = ref(0)
const list = ref<ContractApi.AuditEventVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10
})

/** 明细 JSON 美化（单行展示，tooltip 中可见全文） */
const prettyDetail = (detailJson?: string) => {
  if (!detailJson) return '-'
  try {
    const obj = JSON.parse(detailJson)
    return Object.entries(obj)
      .map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : value}`)
      .join('；')
  } catch {
    return detailJson
  }
}

/** 加载审计事件 */
const getList = async () => {
  if (!props.contract.id) return
  loading.value = true
  try {
    const data = await ContractApi.getAuditEventPage({
      ...queryParams,
      contractId: props.contract.id
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.contract.id, props.contract.updateTime, props.contract.approvalStatus],
  () => {
    queryParams.pageNo = 1
    getList()
  },
  { immediate: true }
)
</script>
