<template>
  <div class="ai-panel">
    <el-alert
      title="本地确定性审阅"
      description="只检查当前修订的合同字段、参与方和重大承诺；合同正文与数据未发送到外部模型。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="ai-toolbar">
      <div>
        <strong>当前修订 {{ currentRevisionLabel }}</strong>
        <span>审阅结果与精确修订绑定，新修订产生后旧结果会标记过期。</span>
      </div>
      <el-button
        type="primary"
        :loading="running"
        :disabled="!revisionId"
        @click="runReview"
        v-hasPermi="['clm:ai-review:run']"
      >
        运行风险审阅
      </el-button>
    </div>

    <el-alert
      v-if="loadError"
      title="AI 审阅记录加载失败"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #default><el-link type="primary" @click="loadRuns">重试</el-link></template>
    </el-alert>

    <el-empty
      v-if="!loading && !loadError && !runs.length"
      description="当前合同尚未运行本地审阅"
    />

    <el-collapse v-else v-model="activeRunIds" v-loading="loading">
      <el-collapse-item v-for="item in runs" :key="item.run.id" :name="item.run.id">
        <template #title>
          <div class="run-title">
            <span>修订 #{{ item.run.revisionId }}</span>
            <el-tag v-if="item.stale" type="info" size="small">已过期</el-tag>
            <el-tag v-else type="success" size="small">当前修订</el-tag>
            <span>{{ item.findings.length }} 项发现</span>
            <span>{{ formatNullableDate(item.run.finishedTime) }}</span>
          </div>
        </template>
        <el-alert
          v-if="item.stale"
          title="该结果对应旧修订，仅供追溯；请在当前修订重新运行审阅。"
          type="warning"
          :closable="false"
          show-icon
          class="mb-12px"
        />
        <el-empty
          v-if="!item.findings.length"
          description="本次审阅未发现规则问题"
          :image-size="60"
        />
        <div v-else class="finding-list">
          <article v-for="finding in item.findings" :key="finding.id" class="finding-card">
            <div class="finding-header">
              <div>
                <el-tag :type="severityType(finding.severity)" size="small">
                  {{ severityText(finding.severity) }}
                </el-tag>
                <strong>{{ finding.title }}</strong>
              </div>
              <el-tag v-if="finding.resolution" type="info" size="small">
                {{ finding.resolution === 'ACCEPTED' ? '已接受' : '已忽略' }}
              </el-tag>
            </div>
            <p>{{ finding.detail }}</p>
            <div
              v-if="editable && !item.stale && !finding.resolution"
              class="finding-actions"
              v-hasPermi="['clm:ai-review:update']"
            >
              <el-button link type="primary" @click="resolveFinding(finding, 'ACCEPTED')">
                接受提示
              </el-button>
              <el-button link type="info" @click="resolveFinding(finding, 'IGNORED')">
                忽略
              </el-button>
            </div>
          </article>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import * as AiReviewApi from '@/api/clm/aiReview'

const props = defineProps<{
  contractId?: string | number
  revisionId?: string | number
  revisionNo?: number
  editable?: boolean
}>()

const message = useMessage()
const loading = ref(false)
const loadError = ref(false)
const running = ref(false)
const runs = ref<AiReviewApi.AiReviewRunResultVO[]>([])
const activeRunIds = ref<string[]>([])
const currentRevisionLabel = computed(() =>
  props.revisionNo ? `R${props.revisionNo}` : props.revisionId ? `#${props.revisionId}` : '未就绪'
)

const severityText = (severity: string) => {
  return { HIGH: '高', MEDIUM: '中', LOW: '低' }[severity] || severity
}

const severityType = (severity: string) => {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

const loadRuns = async () => {
  if (!props.contractId) return
  loading.value = true
  loadError.value = false
  try {
    runs.value = (await AiReviewApi.getAiReviewList(props.contractId)) || []
    activeRunIds.value = runs.value.slice(0, 1).map((item) => item.run.id)
  } catch {
    runs.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const runReview = async () => {
  if (!props.contractId || !props.revisionId) return
  running.value = true
  try {
    await AiReviewApi.runAiReview({
      contractId: props.contractId,
      revisionId: props.revisionId,
      runType: 'RISK_REVIEW'
    })
    message.success('本地确定性审阅已完成，合同数据未外发')
    await loadRuns()
  } finally {
    running.value = false
  }
}

const resolveFinding = async (
  finding: AiReviewApi.AiReviewFindingVO,
  resolution: AiReviewApi.AiFindingResolution
) => {
  await AiReviewApi.resolveAiFinding(finding.id, resolution)
  message.success(resolution === 'ACCEPTED' ? '已接受该提示' : '已忽略该提示')
  await loadRuns()
}

watch(
  () => [props.contractId, props.revisionId],
  () => loadRuns()
)
onMounted(loadRuns)
</script>

<style scoped>
.ai-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ai-toolbar,
.finding-header,
.run-title {
  display: flex;
  align-items: center;
}

.ai-toolbar,
.finding-header {
  justify-content: space-between;
  gap: 12px;
}

.ai-toolbar > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-toolbar span,
.run-title,
.finding-card p {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.run-title {
  flex-wrap: wrap;
  gap: 8px;
}

.finding-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.finding-card {
  padding: 12px 14px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.finding-header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.finding-card p {
  margin: 8px 0 0;
}

.finding-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}
</style>
