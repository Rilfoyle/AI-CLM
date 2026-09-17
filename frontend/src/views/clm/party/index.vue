<template>
  <ContentWrap>
    <div class="flex flex-wrap items-start justify-between gap-12px">
      <div>
        <div class="text-20px font-bold">相对方信息</div>
        <div class="mt-5px text-13px text-[var(--el-text-color-secondary)]">
          统一管理相对方，并保留合同起草所需的我方主体；起草时只引用启用记录并冻结参与方快照。
        </div>
      </div>
      <div class="flex flex-wrap gap-8px">
        <el-button v-hasPermi="['clm:party:query']" @click="openDuplicateDrawer">
          <Icon icon="ep:connection" class="mr-5px" />重复识别
        </el-button>
        <el-button
          v-hasPermi="['clm:party-import:query']"
          @click="router.push('/clm/basic-data/import')"
        >
          <Icon icon="ep:upload" class="mr-5px" />相对方导入
        </el-button>
        <el-button v-hasPermi="['clm:party:create']" type="primary" @click="openForm('create')">
          <Icon icon="ep:plus" class="mr-5px" />新增参与方
        </el-button>
      </div>
    </div>
  </ContentWrap>

  <!-- 搜索 -->
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
    >
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入相对方或我方主体名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="类型" prop="partyType">
        <el-select
          v-model="queryParams.partyType"
          placeholder="请选择类型"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.CLM_PARTY_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="主体归属" prop="internalFlag">
        <el-select
          v-model="queryParams.internalFlag"
          placeholder="请选择主体归属"
          clearable
          class="!w-240px"
        >
          <el-option label="我方主体" :value="true" />
          <el-option label="相对方" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-240px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" prop="partyType" width="90">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.CLM_PARTY_TYPE" :value="scope.row.partyType" />
        </template>
      </el-table-column>
      <el-table-column label="主体归属" align="center" prop="internalFlag" width="100">
        <template #default="scope">
          <el-tag v-if="scope.row.internalFlag" type="primary">我方主体</el-tag>
          <el-tag v-else type="warning">相对方</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="统一社会信用代码"
        align="center"
        prop="unifiedCreditCode"
        width="180"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="我方简称" align="center" prop="shortName" width="120">
        <template #default="scope">{{
          scope.row.internalFlag ? scope.row.shortName || '-' : '-'
        }}</template>
      </el-table-column>
      <el-table-column label="联系人" align="center" prop="contactName" width="100" />
      <el-table-column label="联系电话" align="center" prop="contactPhone" width="130" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="140">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['clm:party:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['clm:party:delete']"
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

  <!-- 表单弹窗：添加/修改 -->
  <PartyForm ref="formRef" @success="getList" />
  <PartyDuplicateDrawer ref="duplicateDrawerRef" @merged="getList" />
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as PartyApi from '@/api/clm/party'
import PartyForm from './PartyForm.vue'
import PartyDuplicateDrawer from './components/PartyDuplicateDrawer.vue'

defineOptions({ name: 'ClmParty' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化
const router = useRouter()

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref<PartyApi.PartyVO[]>([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  partyType: undefined,
  internalFlag: undefined,
  status: undefined
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await PartyApi.getPartyPage(queryParams)
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
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}
const duplicateDrawerRef = ref<InstanceType<typeof PartyDuplicateDrawer>>()
const openDuplicateDrawer = () =>
  duplicateDrawerRef.value?.open({
    internalFlag: queryParams.internalFlag,
    partyType: queryParams.partyType
  })

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await PartyApi.deleteParty(id)
    message.success(t('common.delSuccess'))
    // 刷新列表
    await getList()
  } catch {}
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
