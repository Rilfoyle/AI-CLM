<template>
  <ContentWrap>
    <!-- 页头 -->
    <div class="flex flex-wrap items-start justify-between gap-8px">
      <div>
        <div class="text-20px font-bold text-[var(--el-text-color-primary)]">融合起草中心</div>
        <div class="mt-8px text-14px text-[var(--el-text-color-secondary)]">
          按场景选择起草方式，快速创建合同草稿
        </div>
      </div>
      <div class="flex items-center gap-16px">
        <el-link type="primary" :underline="false" @click="goMyDrafts">
          <Icon icon="ep:edit-pen" class="mr-4px" /> 我的草稿
        </el-link>
        <el-link type="primary" :underline="false" @click="goLedger">
          <Icon icon="ep:tickets" class="mr-4px" /> 前往合同台账
        </el-link>
      </div>
    </div>

    <!-- 分组一：常规快速起草 -->
    <el-divider content-position="left">常规快速起草</el-divider>
    <el-row :gutter="16">
      <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24" class="mb-16px">
        <el-card shadow="hover" class="draft-card" @click="openTemplateDialog">
          <div class="flex items-center gap-16px">
            <div class="draft-card-icon bg-[var(--el-color-primary-light-9)]">
              <Icon icon="ep:document-copy" :size="32" class="text-[var(--el-color-primary)]" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-16px font-bold text-[var(--el-text-color-primary)]">
                选择标准模板起草
              </div>
              <div class="mt-6px text-13px text-[var(--el-text-color-secondary)]">
                从已配置范本的合同类型中选择，自动以类型范本生成合同正文 v1
              </div>
            </div>
            <Icon
              icon="ep:arrow-right"
              :size="18"
              class="text-[var(--el-text-color-placeholder)]"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24" class="mb-16px">
        <el-card shadow="hover" class="draft-card" @click="goUploadCreate">
          <div class="flex items-center gap-16px">
            <div class="draft-card-icon bg-[var(--el-color-success-light-9)]">
              <Icon icon="ep:upload-filled" :size="32" class="text-[var(--el-color-success)]" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-16px font-bold text-[var(--el-text-color-primary)]">
                已有文件上传起草
              </div>
              <div class="mt-6px text-13px text-[var(--el-text-color-secondary)]">
                已线下拟好合同文件？填写要素后上传文件作为合同正文
              </div>
            </div>
            <Icon
              icon="ep:arrow-right"
              :size="18"
              class="text-[var(--el-text-color-placeholder)]"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分组二：基于已有合同起草 -->
    <el-divider content-position="left">基于已有合同起草</el-divider>
    <el-row :gutter="16">
      <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24" class="mb-16px">
        <el-card shadow="hover" class="draft-card" @click="openContractDialog('COPY')">
          <div class="flex items-center gap-16px">
            <div class="draft-card-icon bg-[var(--el-color-warning-light-9)]">
              <Icon icon="ep:copy-document" :size="32" class="text-[var(--el-color-warning)]" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-16px font-bold text-[var(--el-text-color-primary)]"
                >复制已有合同</div
              >
              <div class="mt-6px text-13px text-[var(--el-text-color-secondary)]">
                选择一份已有合同，复制其要素与正文，快速生成新草稿
              </div>
            </div>
            <Icon
              icon="ep:arrow-right"
              :size="18"
              class="text-[var(--el-text-color-placeholder)]"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xl="12" :lg="12" :md="12" :sm="24" :xs="24" class="mb-16px">
        <el-card shadow="hover" class="draft-card" @click="openContractDialog('RENEWAL')">
          <div class="flex items-center gap-16px">
            <div class="draft-card-icon bg-[var(--el-color-danger-light-9)]">
              <Icon icon="ep:refresh-right" :size="32" class="text-[var(--el-color-danger)]" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="text-16px font-bold text-[var(--el-text-color-primary)]">合同续签</div>
              <div class="mt-6px text-13px text-[var(--el-text-color-secondary)]">
                针对已签订合同发起续签，沿用原要素并建立续签关联
              </div>
            </div>
            <Icon
              icon="ep:arrow-right"
              :size="18"
              class="text-[var(--el-text-color-placeholder)]"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <!-- 模板选择弹窗 -->
  <el-dialog v-model="templateDialogVisible" title="选择标准模板" width="720px">
    <div v-loading="templateLoading">
      <el-row v-if="templateTypeList.length > 0" :gutter="12">
        <el-col v-for="item in templateTypeList" :key="item.id" :span="12" class="mb-12px">
          <el-card shadow="hover" class="draft-card" @click="handleSelectTemplate(item)">
            <div class="flex items-start gap-12px">
              <Icon icon="ep:document" :size="24" class="mt-2px text-[var(--el-color-primary)]" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-14px font-bold text-[var(--el-text-color-primary)]">
                  {{ item.name }}
                </div>
                <div
                  class="mt-4px line-clamp-2 text-12px text-[var(--el-text-color-secondary)]"
                  :title="item.description || ''"
                >
                  {{ item.description || '暂无描述' }}
                </div>
                <div class="mt-6px">
                  <el-link
                    type="primary"
                    :underline="false"
                    class="!text-12px"
                    @click.stop="handleDownloadTemplate(item)"
                  >
                    <Icon icon="ep:download" class="mr-2px" /> 预览下载
                  </el-link>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-else description="暂无已配置范本的合同类型" :image-size="80" />
    </div>
  </el-dialog>

  <!-- 选择合同弹窗（复制 / 续签） -->
  <el-dialog
    v-model="contractDialogVisible"
    :title="relationType === 'RENEWAL' ? '选择要续签的合同' : '选择要复制的合同'"
    width="860px"
  >
    <el-form :inline="true" class="-mb-6px" @submit.prevent>
      <el-form-item label="关键词">
        <el-input
          v-model="contractQuery.title"
          placeholder="请输入合同标题"
          clearable
          class="!w-240px"
          @keyup.enter="searchContracts"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="searchContracts"
          ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
        >
      </el-form-item>
    </el-form>
    <el-table
      v-loading="contractLoading"
      :data="contractList"
      highlight-current-row
      max-height="360"
      @row-click="handleSelectContract"
    >
      <el-table-column label="合同编号" prop="contractNo" width="170" />
      <el-table-column label="标题" prop="title" min-width="200" :show-overflow-tooltip="true" />
      <el-table-column label="类型" prop="typeName" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="生命周期" align="center" width="100">
        <template #default="scope">
          <dict-tag :type="DICT_TYPE.CLM_LIFECYCLE_STATUS" :value="scope.row.lifecycleStatus" />
        </template>
      </el-table-column>
      <el-table-column label="负责人" prop="ownerUserName" align="center" width="100" />
    </el-table>
    <Pagination
      :total="contractTotal"
      v-model:page="contractQuery.pageNo"
      v-model:limit="contractQuery.pageSize"
      layout="total, prev, pager, next"
      @pagination="getContractList"
    />
  </el-dialog>

  <!-- 确认弹窗（可改标题） -->
  <el-dialog
    v-model="confirmDialogVisible"
    :title="relationType === 'RENEWAL' ? '确认续签' : '确认复制'"
    width="520px"
  >
    <el-form label-width="90px">
      <el-form-item label="源合同">
        <span class="text-14px text-[var(--el-text-color-primary)]">
          {{ selectedContract?.title }}
        </span>
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
          {{ selectedContract?.contractNo }}
        </span>
      </el-form-item>
      <el-form-item label="新合同标题">
        <el-input v-model="newTitle" placeholder="请输入新合同标题" maxlength="200" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="confirmDialogVisible = false">取 消</el-button>
      <el-button type="primary" :loading="copyLoading" @click="handleCopyConfirm">确 定</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import * as ContractApi from '@/api/clm/contract'
import * as ContractTypeApi from '@/api/clm/contractType'

defineOptions({ name: 'ClmContractDraftCenter' })

const message = useMessage() // 消息弹窗
const { push } = useRouter() // 路由

// ========== 顶部链接 ==========

/** 我的草稿：跳台账草稿 Tab */
const goMyDrafts = () => {
  push({ path: '/clm/contract', query: { approvalStatus: 0 } })
}

/** 前往合同台账 */
const goLedger = () => {
  push({ path: '/clm/contract' })
}

// ========== 模板起草 ==========

const templateDialogVisible = ref(false)
const templateLoading = ref(false)
const templateTypeList = ref<ContractTypeApi.ContractTypeSimpleVO[]>([])

/** 打开模板选择弹窗 */
const openTemplateDialog = async () => {
  templateDialogVisible.value = true
  templateLoading.value = true
  try {
    const list = await ContractTypeApi.getContractTypeSimpleList()
    templateTypeList.value = (list || []).filter((item) => item.hasTemplate)
  } finally {
    templateLoading.value = false
  }
}

/** 选中模板类型 → 新建页（预选类型 + 用范本生成正文） */
const handleSelectTemplate = (item: ContractTypeApi.ContractTypeSimpleVO) => {
  templateDialogVisible.value = false
  push({ name: 'ClmContractCreate', query: { typeId: item.id, useTemplate: 1 } })
}

/** 下载范本预览 */
const handleDownloadTemplate = async (item: ContractTypeApi.ContractTypeSimpleVO) => {
  await ContractTypeApi.downloadTypeTemplate(item.id, `${item.name}-范本.docx`)
}

/** 已有文件上传起草 */
const goUploadCreate = () => {
  push({ name: 'ClmContractCreate', query: { upload: 1 } })
}

// ========== 复制 / 续签 ==========

const relationType = ref<'COPY' | 'RENEWAL'>('COPY')
const contractDialogVisible = ref(false)
const contractLoading = ref(false)
const contractList = ref<ContractApi.ContractVO[]>([])
const contractTotal = ref(0)
const contractQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  title: undefined as string | undefined,
  lifecycleStatus: undefined as number | undefined
})

/** 打开选择合同弹窗 */
const openContractDialog = (type: 'COPY' | 'RENEWAL') => {
  relationType.value = type
  contractQuery.pageNo = 1
  contractQuery.title = undefined
  // 续签默认只看已签订合同
  contractQuery.lifecycleStatus = type === 'RENEWAL' ? 3 : undefined
  contractDialogVisible.value = true
  getContractList()
}

/** 查询合同列表 */
const getContractList = async () => {
  contractLoading.value = true
  try {
    const data = await ContractApi.getContractPage(contractQuery)
    contractList.value = data.list
    contractTotal.value = data.total
  } finally {
    contractLoading.value = false
  }
}

/** 搜索 */
const searchContracts = () => {
  contractQuery.pageNo = 1
  getContractList()
}

// ========== 确认弹窗 ==========

const confirmDialogVisible = ref(false)
const selectedContract = ref<ContractApi.ContractVO>()
const newTitle = ref('')
const copyLoading = ref(false)

/** 行点选合同 */
const handleSelectContract = (row: ContractApi.ContractVO) => {
  selectedContract.value = row
  newTitle.value = `${row.title}（${relationType.value === 'RENEWAL' ? '续签' : '复制'}）`
  confirmDialogVisible.value = true
}

/** 确认复制 / 续签 */
const handleCopyConfirm = async () => {
  if (!selectedContract.value?.id) {
    return
  }
  if (!newTitle.value.trim()) {
    message.warning('请输入新合同标题')
    return
  }
  copyLoading.value = true
  try {
    const id = await ContractApi.copyContract({
      sourceContractId: selectedContract.value.id,
      relationType: relationType.value,
      title: newTitle.value.trim()
    })
    message.success(relationType.value === 'RENEWAL' ? '续签草稿已创建' : '复制成功')
    confirmDialogVisible.value = false
    contractDialogVisible.value = false
    await push({ name: 'ClmContractDetail', params: { id } })
  } finally {
    copyLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.draft-card {
  cursor: pointer;
  transition:
    box-shadow 0.2s,
    transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }
}

.draft-card-icon {
  display: flex;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.line-clamp-2 {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
