<template>
  <div>
    <div class="mb-10px flex items-center justify-between gap-8px">
      <div class="text-12px text-[var(--el-text-color-secondary)]">
        修订同时冻结正文、关键字段、参与方和重大承诺，历史修订不可覆盖。
      </div>
      <el-button :disabled="selection.length !== 2" @click="handleCompare">
        <Icon icon="ep:sort" class="mr-5px" /> 对比所选修订
      </el-button>
    </div>

    <el-alert
      v-if="error"
      class="mb-10px"
      type="warning"
      :closable="false"
      show-icon
      title="修订记录暂时无法加载"
    >
      <template #default
        ><el-link type="primary" :underline="false" @click="getList">重试</el-link></template
      >
    </el-alert>

    <el-table v-loading="loading" :data="list" size="small" @selection-change="selection = $event">
      <el-table-column type="selection" width="42" />
      <el-table-column label="修订" width="88">
        <template #default="scope">
          R{{ scope.row.revisionNo }}
          <el-tag
            v-if="String(scope.row.id) === String(currentRevisionId)"
            size="small"
            type="success"
            class="ml-3px"
            >当前</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="变更说明" prop="changeReason" min-width="150">
        <template #default="scope">{{ scope.row.changeReason || '初始修订' }}</template>
      </el-table-column>
      <el-table-column label="修改人" prop="creatorName" width="100" />
      <el-table-column label="时间" prop="createTime" width="155">
        <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !error && list.length === 0"
      description="暂无修订记录"
      :image-size="60"
    />

    <Dialog v-model="compareVisible" title="合同修订差异" width="760px">
      <div v-loading="compareLoading">
        <el-alert
          v-if="compareError"
          type="warning"
          :closable="false"
          show-icon
          title="修订差异加载失败"
        />
        <template v-else-if="compareResult">
          <el-alert
            v-if="compareResult.summary"
            class="mb-12px"
            type="info"
            :closable="false"
            :title="compareResult.summary"
          />
          <div v-for="section in compareSections" :key="section.label" class="mb-16px">
            <div class="mb-6px font-500">{{ section.label }}</div>
            <el-table v-if="section.rows.length" :data="section.rows" size="small" border>
              <el-table-column label="项目" min-width="130">
                <template #default="scope">{{
                  scope.row.fieldLabel || scope.row.field || '-'
                }}</template>
              </el-table-column>
              <el-table-column label="变更前" min-width="220">
                <template #default="scope">{{ displayValue(scope.row.before) }}</template>
              </el-table-column>
              <el-table-column label="变更后" min-width="220">
                <template #default="scope">{{ displayValue(scope.row.after) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="无变化" :image-size="42" />
          </div>
        </template>
      </div>
      <template #footer><el-button @click="compareVisible = false">关闭</el-button></template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import * as RevisionApi from '@/api/clm/revision'

defineOptions({ name: 'ClmContractRevisionPanel' })

const props = defineProps<{ contractId: string | number; currentRevisionId?: string }>()
const emit = defineEmits<{ loaded: [revisions: RevisionApi.ContractRevisionVO[]] }>()

const loading = ref(false)
const error = ref(false)
const list = ref<RevisionApi.ContractRevisionVO[]>([])
const selection = ref<RevisionApi.ContractRevisionVO[]>([])
const compareVisible = ref(false)
const compareLoading = ref(false)
const compareError = ref(false)
const compareResult = ref<RevisionApi.RevisionCompareVO>()

const getList = async () => {
  if (!props.contractId) return
  loading.value = true
  error.value = false
  try {
    const data = await RevisionApi.getContractRevisionList(props.contractId)
    list.value = [...(data || [])].sort((a, b) => b.revisionNo - a.revisionNo)
    emit('loaded', list.value)
  } catch {
    list.value = []
    error.value = true
  } finally {
    loading.value = false
  }
}

const compareSections = computed(() => [
  { label: '关键字段', rows: compareResult.value?.fieldChanges || [] },
  { label: '参与方', rows: compareResult.value?.partyChanges || [] },
  { label: '重大承诺', rows: compareResult.value?.commitmentChanges || [] },
  { label: '合同正文', rows: compareResult.value?.documentChanges || [] }
])

const handleCompare = async () => {
  if (selection.value.length !== 2) return
  const revisions = [...selection.value].sort((a, b) => a.revisionNo - b.revisionNo)
  compareVisible.value = true
  compareLoading.value = true
  compareError.value = false
  compareResult.value = undefined
  try {
    compareResult.value = await RevisionApi.compareContractRevisions(
      revisions[0].id,
      revisions[1].id
    )
  } catch {
    compareError.value = true
  } finally {
    compareLoading.value = false
  }
}

const displayValue = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '-'
  if (Array.isArray(value)) return value.join('、')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
const formatTime = (value?: string) =>
  value ? formatDate(new Date(value), 'YYYY-MM-DD HH:mm') : '-'

watch(() => props.contractId, getList, { immediate: true })
defineExpose({ reload: getList })
</script>
