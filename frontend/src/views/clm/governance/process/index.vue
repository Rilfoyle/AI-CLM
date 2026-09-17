<template>
  <div class="governance-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>流程定义</h2>
          <p>
            配置合同审批节点、审批人和条件分支。发布会生成新版本，运行中的审批仍使用启动时版本。
          </p>
        </div>
        <div class="header-actions">
          <el-button @click="router.push('/clm/base-settings/page-layout')">
            <Icon icon="ep:document" class="mr-5px" />页面布局配置
          </el-button>
          <el-button @click="router.push('/clm/approval-management/workflow-settings/routing')">
            <Icon icon="ep:share" class="mr-5px" />业务单据流程配置
          </el-button>
          <el-button
            type="primary"
            @click="openEditor()"
            v-hasPermi="['clm:governance:process:update']"
          >
            <Icon icon="ep:plus" class="mr-5px" />新建流程定义
          </el-button>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form :inline="true" class="-mb-15px" @submit.prevent>
        <el-form-item label="流程名称">
          <el-input
            v-model="queryName"
            placeholder="搜索流程定义"
            clearable
            class="!w-260px"
            @keyup.enter="getList"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="getList">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        v-if="loadError"
        class="mb-16px"
        type="warning"
        :closable="false"
        show-icon
        title="流程定义服务暂时不可用"
        description="请确认后端和 SQL 12 已更新后重试。"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>

      <el-table v-loading="loading" :data="list" empty-text="暂无流程定义">
        <el-table-column label="流程编码" prop="key" min-width="210">
          <template #default="scope"
            ><code>{{ scope.row.key }}</code></template
          >
        </el-table-column>
        <el-table-column label="流程名称" min-width="220">
          <template #default="scope">
            <div class="process-name">{{ scope.row.name }}</div>
            <div class="process-description">{{ scope.row.description || '未填写流程说明' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发布状态" width="130" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.deployed ? 'success' : 'warning'">
              {{ scope.row.deployed ? `已发布 V${scope.row.deployedVersion || 1}` : '未发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="scope">{{ formatNullableDate(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="openEditor(scope.row.id)"
              v-hasPermi="['clm:governance:process:update']"
            >
              编辑流程图
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && !loadError && list.length === 0"
        description="还没有流程定义，可以新建并发布后在业务单据流程配置中使用"
        :image-size="86"
      />
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import { formatNullableDate } from '@/utils/formatTime'
import * as ProcessApi from '@/api/clm/governance/process'

defineOptions({ name: 'ClmGovernanceProcess' })

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const queryName = ref('')
const list = ref<ProcessApi.ContractProcessSummaryVO[]>([])

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    list.value =
      (await ProcessApi.getContractProcessList(queryName.value.trim() || undefined)) || []
  } catch {
    list.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  queryName.value = ''
  getList()
}

const openEditor = (id?: string) => {
  router.push({
    name: 'ClmGovernanceProcessEditor',
    query: { id }
  })
}

onMounted(getList)
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.header-actions {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-header p,
.process-description {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.process-name {
  margin-bottom: 3px;
  font-weight: 600;
}

code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: var(--el-color-primary);
}

@media (width <= 900px) {
  .page-header {
    flex-direction: column;
  }

  .header-actions {
    justify-content: flex-start;
  }
}
</style>
