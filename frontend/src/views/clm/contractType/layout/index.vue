<template>
  <div class="layout-page">
    <ContentWrap>
      <div class="page-header">
        <div>
          <h2>页面布局配置</h2>
          <p>按合同分类进入现有表单设计器，维护合同起草和审批时使用的字段布局。</p>
        </div>
        <el-button @click="router.push('/clm/base-settings/contract-type')">
          <Icon icon="ep:collection-tag" class="mr-5px" />合同分类
        </el-button>
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-alert
        class="mb-16px"
        type="info"
        :closable="false"
        show-icon
        title="页面布局沿用合同分类的版本机制，不创建第二套表单。已有草稿可直接编辑；没有草稿时请先在合同分类中创建草稿版本。"
      />
      <el-form :inline="true" class="-mb-15px" @submit.prevent>
        <el-form-item label="合同分类">
          <el-input
            v-model="query.name"
            class="!w-260px"
            clearable
            placeholder="分类名称"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">
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
        title="页面布局列表加载失败"
      >
        <template #default><el-link type="primary" @click="getList">重新加载</el-link></template>
      </el-alert>
      <el-table v-loading="loading" :data="list" empty-text="暂无合同分类">
        <el-table-column prop="code" label="分类编码" min-width="160" />
        <el-table-column prop="name" label="分类名称" min-width="200" />
        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        <el-table-column label="当前发布版本" width="130" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.currentVersionNo" type="success">
              V{{ scope.row.currentVersionNo }}
            </el-tag>
            <el-tag v-else type="info">未发布</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="布局草稿" width="110" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.draftVersionId" type="warning">待编辑</el-tag>
            <el-tag v-else type="info">无草稿</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.draftVersionId"
              v-hasPermi="['clm:contract-type:update']"
              link
              type="primary"
              @click="openLayout(scope.row.draftVersionId)"
            >
              编辑页面布局
            </el-button>
            <el-button
              v-if="scope.row.currentVersionId"
              v-hasPermi="['clm:contract-type:query']"
              link
              type="primary"
              @click="openLayout(scope.row.currentVersionId, true)"
            >
              预览
            </el-button>
            <el-button link type="primary" @click="router.push('/clm/base-settings/contract-type')">
              管理版本
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-if="total > 0"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="getList"
      />
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmContractTypeLayout' })

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const list = ref<ContractTypeApi.ContractTypeVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, name: '' })

const getList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const data = await ContractTypeApi.getContractTypePage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      name: query.name.trim() || undefined
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch {
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  query.pageNo = 1
  getList()
}

const resetQuery = () => {
  query.pageNo = 1
  query.name = ''
  getList()
}

const openLayout = (id: number, readonly = false) => {
  router.push({
    name: 'ClmContractTypeEditor',
    query: { id, readonly: readonly ? '1' : undefined }
  })
}

onMounted(getList)
</script>

<style scoped>
.layout-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
}

.page-header p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
