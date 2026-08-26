<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
    >
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入合同标题"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="合同编号" prop="contractNo">
        <el-input
          v-model="queryParams.contractNo"
          placeholder="请输入合同编号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="合同类型" prop="typeId">
        <el-select
          v-model="queryParams.typeId"
          placeholder="请选择合同类型"
          clearable
          class="!w-240px"
        >
          <el-option v-for="item in typeList" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="审批状态" prop="approvalStatus">
        <el-select
          v-model="queryParams.approvalStatus"
          placeholder="请选择审批状态"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.CLM_APPROVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="生命周期" prop="lifecycleStatus">
        <el-select
          v-model="queryParams.lifecycleStatus"
          placeholder="请选择生命周期"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.CLM_LIFECYCLE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="handleCreate" v-hasPermi="['clm:contract:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新建
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <!-- 状态 Tab -->
    <el-tabs v-model="activeTab" class="-mt-10px" @tab-change="handleTabChange">
      <el-tab-pane v-for="tab in statusTabs" :key="tab.key" :label="tab.label" :name="tab.key" />
    </el-tabs>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="合同编号" align="center" prop="contractNo" width="170" />
      <el-table-column label="标题" align="left" prop="title" min-width="200">
        <template #default="scope">
          <el-link type="primary" :underline="false" @click="handleDetail(scope.row.id)">
            {{ scope.row.title }}
          </el-link>
          <div
            v-if="scope.row.relationType"
            class="text-12px text-[var(--el-text-color-secondary)] leading-18px"
          >
            {{ scope.row.relationType === 'RENEWAL' ? '续签' : '复制' }}自
            {{ scope.row.sourceContractNo || '-' }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="类型" align="center" prop="typeName" width="140" />
      <el-table-column label="金额" align="right" prop="amount" width="160">
        <template #default="scope">
          {{ formatAmount(scope.row.currency, scope.row.amount) }}
        </template>
      </el-table-column>
      <el-table-column label="负责人" align="center" prop="ownerUserName" width="120" />
      <el-table-column label="审批状态" align="center" prop="approvalStatus" width="110">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.CLM_APPROVAL_STATUS" :value="scope.row.approvalStatus" />
        </template>
      </el-table-column>
      <el-table-column label="生命周期" align="center" prop="lifecycleStatus" width="110">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.CLM_LIFECYCLE_STATUS" :value="scope.row.lifecycleStatus" />
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="140" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleDetail(scope.row.id)">详情</el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['clm:contract:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ContractApi from '@/api/clm/contract'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmContract' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化
const { push } = useRouter() // 路由
const route = useRoute() // 当前路由

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref<ContractApi.ContractVO[]>([]) // 列表的数据
const typeList = ref<ContractTypeApi.ContractTypeSimpleVO[]>([]) // 合同类型选项
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  title: undefined as string | undefined,
  contractNo: undefined as string | undefined,
  typeId: undefined as number | undefined,
  approvalStatus: undefined as number | undefined,
  lifecycleStatus: undefined as number | undefined
})
const queryFormRef = ref() // 搜索的表单

// ========== 状态 Tab ==========

type StatusTabKey = 'all' | 'draft' | 'approving' | 'approved' | 'signed' | 'rejected'

const statusTabs: { key: StatusTabKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'draft', label: '草稿' },
  { key: 'approving', label: '审批中' },
  { key: 'approved', label: '审批通过' },
  { key: 'signed', label: '已签订' },
  { key: 'rejected', label: '已驳回' }
]

/** 各 Tab 对应的状态过滤条件 */
const tabFilters: Record<
  StatusTabKey,
  { approvalStatus: number | undefined; lifecycleStatus: number | undefined }
> = {
  all: { approvalStatus: undefined, lifecycleStatus: undefined },
  draft: { approvalStatus: 0, lifecycleStatus: 1 },
  approving: { approvalStatus: 1, lifecycleStatus: undefined },
  approved: { approvalStatus: 2, lifecycleStatus: 2 },
  signed: { approvalStatus: undefined, lifecycleStatus: 3 },
  rejected: { approvalStatus: 3, lifecycleStatus: undefined }
}

const activeTab = ref<StatusTabKey>('all')

/** Tab 切换：设置状态条件并查询 */
const handleTabChange = (key: string | number) => {
  const filter = tabFilters[key as StatusTabKey]
  if (!filter) {
    return
  }
  queryParams.approvalStatus = filter.approvalStatus
  queryParams.lifecycleStatus = filter.lifecycleStatus
  queryParams.pageNo = 1
  getList()
}

/** 从路由 query 解析目标 Tab（工作台 / 起草中心跳转） */
const applyRouteQuery = (): boolean => {
  const q = route.query
  let tab: StatusTabKey | undefined
  if (typeof q.tab === 'string' && statusTabs.some((item) => item.key === q.tab)) {
    tab = q.tab as StatusTabKey
  } else if (q.approvalStatus !== undefined || q.lifecycleStatus !== undefined) {
    const approval = q.approvalStatus !== undefined ? Number(q.approvalStatus) : undefined
    const lifecycle = q.lifecycleStatus !== undefined ? Number(q.lifecycleStatus) : undefined
    if (approval === 0) {
      tab = 'draft'
    } else if (approval === 1) {
      tab = 'approving'
    } else if (approval === 2) {
      tab = 'approved'
    } else if (approval === 3) {
      tab = 'rejected'
    } else if (lifecycle === 3) {
      tab = 'signed'
    }
  }
  if (!tab) {
    return false
  }
  activeTab.value = tab
  const filter = tabFilters[tab]
  queryParams.approvalStatus = filter.approvalStatus
  queryParams.lifecycleStatus = filter.lifecycleStatus
  queryParams.pageNo = 1
  return true
}

/** 金额格式化 */
const formatAmount = (currency?: string, amount?: number) => {
  if (amount === undefined || amount === null) {
    return '-'
  }
  const num = Number(amount).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
  return `${currency || 'CNY'} ${num}`
}

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await ContractApi.getContractPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  activeTab.value = 'all'
  handleQuery()
}

/** 新建：跳起草中心 */
const handleCreate = () => {
  push({ name: 'ClmContractDraftCenter' })
}

/** 详情 */
const handleDetail = (id: number) => {
  push({ name: 'ClmContractDetail', params: { id } })
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await ContractApi.deleteContract(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 激活时刷新（从新建 / 详情页 / 工作台返回） */
onActivated(() => {
  applyRouteQuery()
  getList()
})

/** 初始化 **/
onMounted(async () => {
  typeList.value = await ContractTypeApi.getContractTypeSimpleList()
  applyRouteQuery()
  await getList()
})
</script>
