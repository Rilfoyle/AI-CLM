<template>
  <div class="clm-online-edit">
    <div class="clm-online-edit__bar">
      <div class="flex items-center gap-10px overflow-hidden">
        <Icon icon="ep:document" />
        <span class="truncate font-bold">{{ version?.fileName || '文档' }}</span>
        <el-tag v-if="version" size="small" type="success">v{{ version.versionNo }}</el-tag>
        <el-tag size="small" :type="mode === 'edit' ? 'warning' : 'info'">
          {{ mode === 'edit' ? '在线编辑' : '只读预览' }}
        </el-tag>
        <el-tag v-if="version?.frozen" size="small" type="warning">已冻结</el-tag>
      </div>
      <div class="flex items-center gap-8px">
        <el-button v-if="errorState" size="small" @click="init">
          <Icon icon="ep:refresh" class="mr-5px" /> 重试
        </el-button>
        <el-button size="small" type="primary" plain @click="backToContract">
          <Icon icon="ep:back" class="mr-5px" /> 返回合同
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="clm-online-edit__body">
      <el-result
        v-if="errorState === 'disabled'"
        icon="warning"
        title="未配置 ONLYOFFICE Document Server"
        sub-title="后端未启用在线编辑，请在 application-clm-local.yaml 中配置 clm.onlyoffice 后重试"
      >
        <template #extra>
          <el-button type="primary" @click="backToContract">返回合同</el-button>
        </template>
      </el-result>
      <el-result
        v-else-if="errorState === 'unreachable'"
        icon="warning"
        title="无法连接 ONLYOFFICE Document Server"
        :sub-title="`Document Server（${documentServerUrl}）不可达，请确认服务已启动且浏览器可访问`"
      >
        <template #extra>
          <el-button type="primary" @click="init">重试</el-button>
          <el-button @click="backToContract">返回合同</el-button>
        </template>
      </el-result>
      <el-result
        v-else-if="errorState === 'failed'"
        icon="error"
        title="打开文档失败"
        :sub-title="errorMessage"
      >
        <template #extra>
          <el-button type="primary" @click="init">重试</el-button>
          <el-button @click="backToContract">返回合同</el-button>
        </template>
      </el-result>
      <div v-show="!errorState" class="clm-online-edit__editor">
        <div :id="placeholderId"></div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import * as ContractApi from '@/api/clm/contract'
import * as OnlineEditApi from '@/api/clm/onlineEdit'

defineOptions({ name: 'ClmContractOnlineEdit' })

const route = useRoute()
const { push } = useRouter()
const message = useMessage()

const placeholderId = 'clm-oo-editor'

const versionId = computed(() => String(route.query.versionId || ''))
const mode = computed<OnlineEditApi.OnlineEditMode>(() =>
  route.query.mode === 'edit' ? 'edit' : 'view'
)

const loading = ref(false)
const version = ref<ContractApi.DocumentVersionVO>()
const documentServerUrl = ref('')
const errorState = ref<'' | 'disabled' | 'unreachable' | 'failed'>('')
const errorMessage = ref('')

let editor: any = null

/** 销毁编辑器实例 */
const destroyEditor = () => {
  if (editor) {
    try {
      editor.destroyEditor()
    } catch {
      // ignore
    }
    editor = null
  }
}

/** 返回合同详情 */
const backToContract = () => {
  const contractId = version.value?.contractId || route.query.contractId
  if (contractId) {
    push({ name: 'ClmContractDetail', params: { id: String(contractId) } })
  } else {
    push({ name: 'ClmContract' })
  }
}

/** 初始化：拉配置 → 加载 api.js → 创建编辑器 */
const init = async () => {
  destroyEditor()
  errorState.value = ''
  errorMessage.value = ''
  if (!versionId.value) {
    errorState.value = 'failed'
    errorMessage.value = '缺少 versionId 参数'
    return
  }
  loading.value = true
  try {
    // 1. 版本信息（用于顶部条与返回）
    version.value = await ContractApi.getDocumentVersion(Number(versionId.value))
    // 2. 在线编辑配置
    const resp = await OnlineEditApi.getOnlineEditConfig(versionId.value, mode.value)
    documentServerUrl.value = resp?.documentServerUrl || ''
    if (!resp || !resp.enabled || !resp.documentServerUrl || !resp.config) {
      errorState.value = 'disabled'
      return
    }
    // 3. 注入 api.js（不可达则降级）
    let DocsAPI: any
    try {
      DocsAPI = await OnlineEditApi.loadDocsApi(resp.documentServerUrl)
    } catch (e) {
      console.warn('[ClmContractOnlineEdit] load Document Server api.js failed', e)
      errorState.value = 'unreachable'
      return
    }
    // 4. 创建编辑器
    await nextTick()
    editor = new DocsAPI.DocEditor(placeholderId, {
      ...resp.config,
      token: resp.token,
      width: '100%',
      height: '100%',
      events: {
        onDocumentReady: () => {
          loading.value = false
        },
        onError: (event: any) => {
          const msg = event?.data?.errorDescription || event?.data || '编辑器发生错误'
          message.error(String(msg))
        },
        onRequestClose: () => {
          backToContract()
        }
      }
    })
  } catch (e: any) {
    errorState.value = 'failed'
    errorMessage.value = e?.msg || e?.message || '请求失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [route.query.versionId, route.query.mode],
  () => {
    if (route.name === 'ClmContractOnlineEdit') {
      init()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  destroyEditor()
})
</script>

<style lang="scss" scoped>
.clm-online-edit {
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

  &__body {
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
