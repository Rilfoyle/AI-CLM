<template>
  <div class="clm-compare">
    <div class="clm-compare__bar">
      <div class="flex items-center gap-10px overflow-hidden">
        <Icon icon="ep:copy-document" />
        <span class="font-bold">版本并排查看（非内容级比较）</span>
        <template v-if="left.version && right.version">
          <el-tag size="small" type="success">v{{ left.version.versionNo }}</el-tag>
          <span class="text-gray-500">↔</span>
          <el-tag size="small" type="success">v{{ right.version.versionNo }}</el-tag>
        </template>
        <el-tag size="small" type="info">只读</el-tag>
      </div>
      <div class="flex items-center gap-8px">
        <el-button v-if="hasError" size="small" @click="init">
          <Icon icon="ep:refresh" class="mr-5px" /> 重试
        </el-button>
        <el-button size="small" type="primary" plain @click="backToContract">
          <Icon icon="ep:back" class="mr-5px" /> 返回合同
        </el-button>
      </div>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="clm-compare__tip"
      title="两侧为各自版本的只读视图，仅供人工对照，不做逐字内容级比较"
    />

    <div class="clm-compare__panels">
      <div v-for="side in sides" :key="side.key" class="clm-compare__panel">
        <div class="clm-compare__panel-head">
          <div class="flex items-center gap-8px overflow-hidden">
            <el-tag v-if="side.state.version" size="small" type="success">
              v{{ side.state.version.versionNo }}
            </el-tag>
            <span class="truncate font-bold">{{ side.state.version?.fileName || '-' }}</span>
          </div>
          <div class="text-xs text-gray-500 flex items-center gap-10px">
            <span class="font-mono">
              SHA-256 {{ shortSha(side.state.version?.checksumSha256) }}
            </span>
            <span>{{ formatDate(side.state.version?.createTime) || '-' }}</span>
            <span v-if="side.state.version?.creatorName">{{ side.state.version.creatorName }}</span>
          </div>
        </div>
        <div v-loading="side.state.loading" class="clm-compare__panel-body">
          <el-result
            v-if="side.state.errorState === 'disabled'"
            icon="warning"
            title="未配置 ONLYOFFICE Document Server"
            sub-title="后端未启用在线编辑"
          />
          <el-result
            v-else-if="side.state.errorState === 'unreachable'"
            icon="warning"
            title="无法连接 ONLYOFFICE Document Server"
            :sub-title="`Document Server（${side.state.documentServerUrl}）不可达`"
          />
          <el-result
            v-else-if="side.state.errorState === 'failed'"
            icon="error"
            title="打开文档失败"
            :sub-title="side.state.errorMessage"
          />
          <div v-show="!side.state.errorState" class="clm-compare__editor">
            <div :id="side.placeholderId"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import * as ContractApi from '@/api/clm/contract'
import * as OnlineEditApi from '@/api/clm/onlineEdit'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'ClmContractCompare' })

const route = useRoute()
const { push } = useRouter()
const message = useMessage()

interface SideState {
  loading: boolean
  version?: ContractApi.DocumentVersionVO
  documentServerUrl: string
  errorState: '' | 'disabled' | 'unreachable' | 'failed'
  errorMessage: string
}

const createSideState = (): SideState => ({
  loading: false,
  version: undefined,
  documentServerUrl: '',
  errorState: '',
  errorMessage: ''
})

const left = reactive<SideState>(createSideState())
const right = reactive<SideState>(createSideState())

const sides = [
  { key: 'left' as const, placeholderId: 'clm-oo-compare-left', state: left },
  { key: 'right' as const, placeholderId: 'clm-oo-compare-right', state: right }
]

const editors: Record<'left' | 'right', any> = { left: null, right: null }

const hasError = computed(() => !!left.errorState || !!right.errorState)

/** SHA-256 缩略 */
const shortSha = (sha?: string) => (sha ? sha.substring(0, 12) + '…' : '-')

/** 销毁全部编辑器实例 */
const destroyEditors = () => {
  for (const key of ['left', 'right'] as const) {
    if (editors[key]) {
      try {
        editors[key].destroyEditor()
      } catch {
        // ignore
      }
      editors[key] = null
    }
  }
}

/** 返回合同详情 */
const backToContract = () => {
  const contractId = left.version?.contractId || right.version?.contractId || route.query.contractId
  if (contractId) {
    push({ name: 'ClmContractDetail', params: { id: String(contractId) } })
  } else {
    push({ name: 'ClmContract' })
  }
}

/** 打开单侧只读编辑器 */
const openSide = async (side: (typeof sides)[number], versionId: string) => {
  const state = side.state
  state.errorState = ''
  state.errorMessage = ''
  if (!versionId) {
    state.errorState = 'failed'
    state.errorMessage = `缺少 ${side.key} 参数`
    return
  }
  state.loading = true
  try {
    state.version = await ContractApi.getDocumentVersion(Number(versionId))
    const resp = await OnlineEditApi.getOnlineEditConfig(versionId, 'view')
    state.documentServerUrl = resp?.documentServerUrl || ''
    if (!resp || !resp.enabled || !resp.documentServerUrl || !resp.config) {
      state.errorState = 'disabled'
      return
    }
    let DocsAPI: any
    try {
      DocsAPI = await OnlineEditApi.loadDocsApi(resp.documentServerUrl)
    } catch (e) {
      console.warn('[ClmContractCompare] load Document Server api.js failed', e)
      state.errorState = 'unreachable'
      return
    }
    await nextTick()
    editors[side.key] = new DocsAPI.DocEditor(side.placeholderId, {
      ...resp.config,
      token: resp.token,
      width: '100%',
      height: '100%',
      events: {
        onDocumentReady: () => {
          state.loading = false
        },
        onError: (event: any) => {
          const msg = event?.data?.errorDescription || event?.data || '编辑器发生错误'
          message.error(`v${state.version?.versionNo ?? '?'}: ${String(msg)}`)
        },
        onRequestClose: () => {
          backToContract()
        }
      }
    })
  } catch (e: any) {
    state.errorState = 'failed'
    state.errorMessage = e?.msg || e?.message || '请求失败'
  } finally {
    state.loading = false
  }
}

/** 初始化两侧 */
const init = async () => {
  destroyEditors()
  const leftId = String(route.query.left || '')
  const rightId = String(route.query.right || '')
  await Promise.all([openSide(sides[0], leftId), openSide(sides[1], rightId)])
}

watch(
  () => [route.query.left, route.query.right],
  () => {
    if (route.name === 'ClmContractCompare') {
      init()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  destroyEditors()
})
</script>

<style lang="scss" scoped>
.clm-compare {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--top-tool-height) - var(--tags-view-height) - 20px);
  min-height: 500px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;

  &__bar {
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__tip {
    flex: 0 0 auto;
    border-radius: 0;
  }

  &__panels {
    display: flex;
    flex: 1 1 auto;
    min-height: 0;
  }

  &__panel {
    display: flex;
    flex: 1 1 50%;
    flex-direction: column;
    min-width: 0;

    & + & {
      border-left: 1px solid var(--el-border-color-lighter);
    }
  }

  &__panel-head {
    display: flex;
    flex: 0 0 auto;
    flex-direction: column;
    gap: 4px;
    padding: 6px 10px;
    background: var(--el-fill-color-lighter);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  &__panel-body {
    position: relative;
    flex: 1 1 auto;
    min-height: 0;
  }

  &__editor {
    width: 100%;
    height: 100%;

    > div {
      width: 100%;
      height: 100%;
    }
  }
}
</style>
