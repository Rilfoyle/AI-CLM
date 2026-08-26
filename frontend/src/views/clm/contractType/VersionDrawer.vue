<template>
  <el-drawer v-model="drawerVisible" :title="drawerTitle" size="60%" destroy-on-close>
    <div class="mb-10px flex items-center justify-between">
      <div class="text-14px text-gray-500">
        当前发布版本：
        <el-tag v-if="contractType?.currentVersionNo" type="success">
          V{{ contractType.currentVersionNo }}
        </el-tag>
        <el-tag v-else type="info">未发布</el-tag>
      </div>
      <el-button
        type="primary"
        plain
        :disabled="hasDraft"
        :loading="draftLoading"
        @click="handleCreateDraft"
        v-hasPermi="['clm:contract-type:update']"
      >
        <Icon icon="ep:plus" class="mr-5px" /> 新建草稿版本
      </el-button>
    </div>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="版本号" align="center" prop="versionNo" width="90">
        <template #default="scope">V{{ scope.row.versionNo }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.CLM_TYPE_VERSION_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column
        label="流程标识"
        align="center"
        prop="processDefinitionKey"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="扩展字段数" align="center" width="100">
        <template #default="scope">{{ scope.row.formFields?.length || 0 }}</template>
      </el-table-column>
      <el-table-column
        label="发布时间"
        align="center"
        prop="publishedTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="说明" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="160">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="handleDesign(scope.row)"
            v-hasPermi="['clm:contract-type:query']"
          >
            {{ isDraft(scope.row) ? '设计表单' : '预览表单' }}
          </el-button>
          <el-button
            v-if="isDraft(scope.row)"
            link
            type="success"
            @click="handlePublish(scope.row)"
            v-hasPermi="['clm:contract-type:publish']"
          >
            发布
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>
<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmContractTypeVersionDrawer' })

/** 版本状态：0 草稿 / 1 已发布（与 ClmTypeVersionStatusEnum 一致） */
const VERSION_STATUS_DRAFT = 0

const message = useMessage() // 消息弹窗
const { push } = useRouter() // 路由

const drawerVisible = ref(false) // 抽屉是否展示
const loading = ref(false) // 版本列表加载中
const draftLoading = ref(false) // 新建草稿按钮加载中
const contractType = ref<ContractTypeApi.ContractTypeVO>() // 当前合同类型
const list = ref<ContractTypeApi.ContractTypeVersionVO[]>([]) // 版本列表

const drawerTitle = computed(() =>
  contractType.value
    ? `版本管理 - ${contractType.value.name}（${contractType.value.code}）`
    : '版本管理'
)
const hasDraft = computed(() => list.value.some((item) => isDraft(item)))

const isDraft = (row: ContractTypeApi.ContractTypeVersionVO) => row.status === VERSION_STATUS_DRAFT

/** 打开抽屉 */
const open = async (row: ContractTypeApi.ContractTypeVO) => {
  contractType.value = row
  drawerVisible.value = true
  await getList()
}
defineExpose({ open }) // 提供 open 方法，用于打开抽屉

const emit = defineEmits(['success']) // 发布 / 新建草稿成功后通知父组件刷新

/** 查询版本列表 */
const getList = async () => {
  if (!contractType.value?.id) return
  loading.value = true
  try {
    list.value = await ContractTypeApi.getContractTypeVersionList(contractType.value.id)
  } finally {
    loading.value = false
  }
}

/** 设计表单 / 预览表单：跳转设计器页 */
const handleDesign = (row: ContractTypeApi.ContractTypeVersionVO) => {
  const query: Record<string, any> = { id: row.id }
  if (!isDraft(row)) {
    query.readonly = 1
  }
  push({ name: 'ClmContractTypeEditor', query })
}

/** 发布版本 */
const handlePublish = async (row: ContractTypeApi.ContractTypeVersionVO) => {
  try {
    await message.confirm(
      `确认发布版本 V${row.versionNo} 吗？发布后该版本将成为当前版本，且不可再修改。`
    )
    await ContractTypeApi.publishContractTypeVersion(row.id!)
    message.success('发布成功')
    // 同步刷新类型信息，保证头部的"当前发布版本"正确
    contractType.value = await ContractTypeApi.getContractType(contractType.value!.id!)
    await getList()
    emit('success')
  } catch {}
}

/** 新建草稿版本 */
const handleCreateDraft = async () => {
  if (!contractType.value?.id) return
  draftLoading.value = true
  try {
    await ContractTypeApi.createContractTypeDraft(contractType.value.id)
    message.success('草稿版本创建成功')
    await getList()
    emit('success')
  } finally {
    draftLoading.value = false
  }
}
</script>
