<template>
  <!-- 搜索 -->
  <ContentWrap>
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="类型编码" prop="code">
        <el-input
          v-model="queryParams.code"
          placeholder="请输入类型编码"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="类型名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入类型名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
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
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['clm:contract-type:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="编码" align="center" prop="code" :show-overflow-tooltip="true" />
      <el-table-column label="名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column
        label="描述"
        align="center"
        prop="description"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="当前发布版本" align="center" prop="currentVersionNo" width="120">
        <template #default="scope">
          <el-tag v-if="scope.row.currentVersionNo" type="success">
            V{{ scope.row.currentVersionNo }}
          </el-tag>
          <el-tag v-else type="info">未发布</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="范本" align="center" width="80">
        <template #default="scope">
          <el-tag v-if="scope.row.templateFileName" type="success">有</el-tag>
          <el-tag v-else type="info">无</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sort" width="80" />
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        width="180"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" width="260">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['clm:contract-type:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="primary"
            @click="openVersionDrawer(scope.row)"
            v-hasPermi="['clm:contract-type:query']"
          >
            版本/设计
          </el-button>
          <el-button
            link
            type="primary"
            @click="openTemplateDialog(scope.row)"
            v-hasPermi="['clm:contract-type:update']"
          >
            范本
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['clm:contract-type:delete']"
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
  <ContractTypeForm ref="formRef" @success="getList" />
  <!-- 版本抽屉：版本列表 / 发布 / 新建草稿 / 设计表单 -->
  <VersionDrawer ref="versionDrawerRef" @success="getList" />

  <!-- 范本弹窗：上传 / 替换 / 下载 -->
  <el-dialog v-model="templateDialogVisible" title="类型范本" width="520px">
    <el-form label-width="90px">
      <el-form-item label="合同类型">
        <span>{{ templateRow?.name }}</span>
      </el-form-item>
      <el-form-item label="当前范本">
        <template v-if="templateRow?.templateFileName">
          <span class="mr-8px">{{ templateRow.templateFileName }}</span>
          <el-link type="primary" :underline="false" @click="handleDownloadTemplate">
            <Icon icon="ep:download" class="mr-2px" /> 下载
          </el-link>
        </template>
        <el-tag v-else type="info">暂无范本</el-tag>
      </el-form-item>
      <el-form-item :label="templateRow?.templateFileName ? '替换范本' : '上传范本'">
        <el-upload
          ref="templateUploadRef"
          :auto-upload="false"
          :limit="1"
          accept=".docx,.doc,.pdf"
          :on-change="handleTemplateFileChange"
          :on-remove="() => (templateFile = undefined)"
        >
          <el-button> <Icon icon="ep:upload" class="mr-5px" /> 选择文件 </el-button>
          <template #tip>
            <div class="text-12px text-[var(--el-text-color-secondary)]">
              支持 .docx / .doc / .pdf 格式
            </div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="templateDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="templateUploading" @click="handleUploadTemplate">
        上 传
      </el-button>
    </template>
  </el-dialog>
</template>
<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ContractTypeApi from '@/api/clm/contractType'
import ContractTypeForm from './ContractTypeForm.vue'
import VersionDrawer from './VersionDrawer.vue'

defineOptions({ name: 'ClmContractType' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

const loading = ref(true) // 列表的加载中
const total = ref(0) // 列表的总页数
const list = ref<ContractTypeApi.ContractTypeVO[]>([]) // 列表的数据
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  code: undefined,
  name: undefined,
  status: undefined
})
const queryFormRef = ref() // 搜索的表单

/** 查询列表 */
const getList = async () => {
  loading.value = true
  try {
    const data = await ContractTypeApi.getContractTypePage(queryParams)
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

/** 打开版本抽屉 */
const versionDrawerRef = ref()
const openVersionDrawer = (row: ContractTypeApi.ContractTypeVO) => {
  versionDrawerRef.value.open(row)
}

/** 范本弹窗 */
const templateDialogVisible = ref(false)
const templateRow = ref<ContractTypeApi.ContractTypeVO>()
const templateFile = ref<File>()
const templateUploadRef = ref()
const templateUploading = ref(false)

const openTemplateDialog = (row: ContractTypeApi.ContractTypeVO) => {
  templateRow.value = row
  templateFile.value = undefined
  templateDialogVisible.value = true
  nextTick(() => {
    templateUploadRef.value?.clearFiles()
  })
}

/** 选择范本文件 */
const handleTemplateFileChange = (uploadFile: { raw?: File }) => {
  templateFile.value = uploadFile.raw
}

/** 上传 / 替换范本 */
const handleUploadTemplate = async () => {
  if (!templateRow.value?.id) {
    return
  }
  if (!templateFile.value) {
    message.warning('请先选择范本文件')
    return
  }
  templateUploading.value = true
  try {
    const formData = new FormData()
    formData.append('typeId', String(templateRow.value.id))
    formData.append('file', templateFile.value)
    await ContractTypeApi.uploadTypeTemplate(formData)
    message.success('范本上传成功')
    templateDialogVisible.value = false
    await getList()
  } finally {
    templateUploading.value = false
  }
}

/** 下载范本 */
const handleDownloadTemplate = async () => {
  if (!templateRow.value?.id) {
    return
  }
  await ContractTypeApi.downloadTypeTemplate(
    templateRow.value.id,
    templateRow.value.templateFileName || `${templateRow.value.name}-范本.docx`
  )
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    // 删除的二次确认
    await message.delConfirm()
    // 发起删除
    await ContractTypeApi.deleteContractType(id)
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
