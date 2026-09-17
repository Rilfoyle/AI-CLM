<template>
  <el-drawer v-model="visible" title="重复参与方识别与合并" size="860px" destroy-on-close>
    <el-alert
      class="mb-14px"
      type="warning"
      :closable="false"
      show-icon
      title="合并只改写草稿或协同中的活动引用；审批中、已审批合同及所有历史修订快照保持不变。"
    />
    <el-alert
      v-if="lastResult"
      class="mb-14px"
      type="success"
      :closable="false"
      show-icon
      :title="mergeResultText(lastResult)"
    />

    <div class="duplicate-toolbar">
      <span>按统一信用代码优先、规范化名称辅助识别；每次明确选择保留主体。</span>
      <el-button :loading="loading" @click="loadGroups">
        <Icon icon="ep:refresh" class="mr-5px" />重新识别
      </el-button>
    </div>

    <div v-loading="loading" class="duplicate-groups">
      <el-card v-for="group in groups" :key="groupKey(group)" shadow="never">
        <template #header>
          <div class="group-header">
            <div>
              <strong>{{ matchTypeText(group.matchType) }}</strong>
              <span>{{ group.matchValue }}</span>
            </div>
            <el-tag type="warning">{{ group.parties.length }} 条候选</el-tag>
          </div>
        </template>
        <el-table :data="group.parties" border size="small">
          <el-table-column label="保留" width="64" align="center">
            <template #default="scope">
              <el-radio
                v-model="targetIds[groupKey(group)]"
                :value="scope.row.id"
                :aria-label="`保留 ${scope.row.name}`"
              />
            </template>
          </el-table-column>
          <el-table-column label="名称" prop="name" min-width="180">
            <template #default="scope">
              <div class="party-name">
                <strong>{{ scope.row.name }}</strong>
                <el-tag
                  v-if="scope.row.id === group.recommendedTargetPartyId"
                  size="small"
                  type="success"
                >
                  建议保留
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="归属" width="90">
            <template #default="scope">{{ scope.row.internalFlag ? '我方' : '相对方' }}</template>
          </el-table-column>
          <el-table-column label="简称" prop="shortName" width="110" />
          <el-table-column
            label="统一信用代码"
            prop="unifiedCreditCode"
            min-width="190"
            show-overflow-tooltip
          />
          <el-table-column label="状态" width="70" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.status === 0 ? 'success' : 'info'" size="small">
                {{ scope.row.status === 0 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.id !== targetIds[groupKey(group)]"
                v-hasPermi="['clm:party:update']"
                link
                type="danger"
                :loading="mergingSourceId === scope.row.id"
                @click="confirmMerge(group, scope.row)"
              >
                合并到保留项
              </el-button>
              <span v-else class="retained-label">保留主体</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
      <el-empty v-if="!loading && !groups.length" description="当前筛选范围未识别到重复参与方" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import * as PartyApi from '@/api/clm/party'

defineOptions({ name: 'ClmPartyDuplicateDrawer' })
const emit = defineEmits<{ merged: [] }>()
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const mergingSourceId = ref<number>()
const groups = ref<PartyApi.PartyDuplicateGroupVO[]>([])
const targetIds = reactive<Record<string, number>>({})
const filters = reactive<{ internalFlag?: boolean; partyType?: number }>({})
const lastResult = ref<PartyApi.PartyMergeRespVO>()

const groupKey = (group: PartyApi.PartyDuplicateGroupVO) => `${group.matchType}:${group.matchValue}`
const matchTypeText = (type: PartyApi.PartyDuplicateGroupVO['matchType']) =>
  type === 'UNIFIED_CREDIT_CODE' ? '统一信用代码重复' : '名称规范化重复'
const mergeResultText = (result: PartyApi.PartyMergeRespVO) =>
  `${result.idempotent ? '该合并此前已完成；' : '合并完成；'}改写 ${result.rewrittenContractCount} 份活动合同、${result.rewrittenLinkCount} 条关联，去重 ${result.deduplicatedLinkCount} 条关联，保护 ${result.protectedContractCount} 份审批中或已审批合同。`

const loadGroups = async () => {
  loading.value = true
  try {
    groups.value = (await PartyApi.getPartyDuplicateCandidates({ ...filters })) || []
    groups.value.forEach((group) => {
      targetIds[groupKey(group)] = group.recommendedTargetPartyId
    })
  } finally {
    loading.value = false
  }
}

const open = async (nextFilters?: { internalFlag?: boolean; partyType?: number }) => {
  Object.assign(filters, nextFilters || {})
  lastResult.value = undefined
  visible.value = true
  await loadGroups()
}

const confirmMerge = async (group: PartyApi.PartyDuplicateGroupVO, source: PartyApi.PartyVO) => {
  const targetId = targetIds[groupKey(group)]
  const target = group.parties.find((item) => item.id === targetId)
  if (!source.id || !target?.id || source.id === target.id) return
  await message.confirm(
    `确认将“${source.name}”（#${source.id}）合并到“${target.name}”（#${target.id}）？源主体会停用；审批中、已审批引用及历史修订快照不会被改写。`
  )
  mergingSourceId.value = source.id
  try {
    lastResult.value = await PartyApi.mergeParty({
      sourcePartyId: source.id,
      targetPartyId: target.id
    })
    message.success('参与方合并完成')
    emit('merged')
    await loadGroups()
  } finally {
    mergingSourceId.value = undefined
  }
}

defineExpose({ open })
</script>

<style scoped>
.duplicate-toolbar,
.group-header,
.group-header > div,
.party-name {
  display: flex;
  align-items: center;
}

.duplicate-toolbar,
.group-header {
  justify-content: space-between;
  gap: 16px;
}

.duplicate-toolbar {
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.duplicate-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 180px;
}

.group-header > div,
.party-name {
  gap: 8px;
}

.group-header span,
.retained-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
