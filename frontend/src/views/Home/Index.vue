<template>
  <div>
    <!-- 问候栏 -->
    <el-card shadow="never">
      <div class="flex flex-wrap items-center justify-between gap-8px">
        <div>
          <div class="text-20px font-bold text-[var(--el-text-color-primary)]">
            你好，{{ nickname }}
          </div>
          <div class="mt-8px text-14px text-[var(--el-text-color-secondary)]">TuriX 合同工作台</div>
        </div>
        <el-button type="primary" v-hasPermi="['clm:contract:create']" @click="handleCreate">
          <Icon icon="ep:plus" class="mr-5px" /> 新建合同
        </el-button>
      </div>
    </el-card>

    <!-- 任务中心桶 -->
    <el-row :gutter="8" class="mt-8px">
      <el-col
        v-for="card in statCards"
        :key="card.label"
        :xl="4"
        :lg="4"
        :md="8"
        :sm="12"
        :xs="12"
        class="mb-8px"
      >
        <el-card shadow="hover" class="cursor-pointer" @click="goCard(card)">
          <el-skeleton :loading="statsLoading" animated :rows="2">
            <div class="flex items-center justify-between">
              <div>
                <div class="text-14px text-[var(--el-text-color-secondary)]">{{ card.label }}</div>
                <CountTo
                  :start-val="0"
                  :end-val="card.value"
                  :duration="1200"
                  class="mt-8px block text-24px font-bold text-[var(--el-text-color-primary)]"
                />
              </div>
              <Icon :icon="card.icon" :size="32" :style="{ color: card.color }" />
            </div>
          </el-skeleton>
        </el-card>
      </el-col>
    </el-row>

    <!-- 三个面板 -->
    <el-row :gutter="8">
      <!-- 我的待办 -->
      <el-col :xl="8" :lg="8" :md="24" :sm="24" :xs="24" class="mb-8px !flex">
        <el-card shadow="never" class="w-full !flex !flex-col" body-class="flex-1">
          <template #header>
            <span class="font-500">我的待办</span>
          </template>
          <el-skeleton :loading="todoLoading" animated>
            <template v-if="todoList.length > 0">
              <div
                v-for="item in todoList"
                :key="item.id"
                class="cursor-pointer rounded-4px px-8px py-10px hover:bg-[var(--el-fill-color-light)]"
                @click="openTask(item)"
              >
                <div class="flex items-center justify-between gap-8px">
                  <span
                    class="min-w-0 flex-1 truncate text-14px text-[var(--el-text-color-primary)]"
                  >
                    {{ item.processInstance?.name || '-' }}
                  </span>
                  <span class="shrink-0 text-12px text-[var(--el-text-color-secondary)]">
                    {{ formatDate(item.createTime) }}
                  </span>
                </div>
                <div class="mt-4px">
                  <el-tag size="small" type="info">{{ item.name }}</el-tag>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无待办" :image-size="60" />
          </el-skeleton>
          <template #footer>
            <div class="text-center">
              <el-link type="primary" :underline="false" @click="goPath('/approval/todo')">
                查看全部
              </el-link>
            </div>
          </template>
        </el-card>
      </el-col>

      <!-- 我负责的合同 -->
      <el-col :xl="8" :lg="8" :md="24" :sm="24" :xs="24" class="mb-8px !flex">
        <el-card shadow="never" class="w-full !flex !flex-col" body-class="flex-1">
          <template #header>
            <span class="font-500">我负责的合同</span>
          </template>
          <el-skeleton :loading="contractLoading" animated>
            <template v-if="myContractList.length > 0">
              <div
                v-for="item in myContractList"
                :key="item.id"
                class="flex items-center justify-between gap-8px rounded-4px px-8px py-10px hover:bg-[var(--el-fill-color-light)]"
              >
                <div class="min-w-0 flex-1">
                  <div class="truncate">
                    <el-link type="primary" :underline="false" @click="openContract(item)">
                      {{ item.title }}
                    </el-link>
                  </div>
                  <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]">
                    {{ item.contractNo }}
                  </div>
                </div>
                <dict-tag
                  :type="DICT_TYPE.CLM_APPROVAL_STATUS"
                  :value="item.approvalStatus ?? ''"
                  class="shrink-0"
                />
              </div>
            </template>
            <el-empty v-else description="暂无合同" :image-size="60" />
          </el-skeleton>
          <template #footer>
            <div class="text-center">
              <el-link type="primary" :underline="false" @click="goPath('/clm/contract')">
                查看全部
              </el-link>
            </div>
          </template>
        </el-card>
      </el-col>

      <!-- 最近审批记录 -->
      <el-col :xl="8" :lg="8" :md="24" :sm="24" :xs="24" class="mb-8px !flex">
        <el-card shadow="never" class="w-full !flex !flex-col" body-class="flex-1">
          <template #header>
            <span class="font-500">最近审批记录</span>
          </template>
          <el-skeleton :loading="doneLoading" animated>
            <template v-if="doneList.length > 0">
              <div
                v-for="item in doneList"
                :key="item.id"
                class="cursor-pointer rounded-4px px-8px py-10px hover:bg-[var(--el-fill-color-light)]"
                @click="openTask(item)"
              >
                <div class="flex items-center justify-between gap-8px">
                  <span
                    class="min-w-0 flex-1 truncate text-14px text-[var(--el-text-color-primary)]"
                  >
                    {{ item.processInstance?.name || '-' }}
                  </span>
                  <dict-tag
                    :type="DICT_TYPE.BPM_TASK_STATUS"
                    :value="item.status"
                    class="shrink-0"
                  />
                </div>
                <div
                  class="mt-4px flex items-center justify-between gap-8px text-12px text-[var(--el-text-color-secondary)]"
                >
                  <span class="min-w-0 flex-1 truncate" :title="item.reason || ''">
                    {{ item.reason || '-' }}
                  </span>
                  <span class="shrink-0">{{ formatDate(item.createTime) }}</span>
                </div>
              </div>
            </template>
            <el-empty v-else description="暂无审批记录" :image-size="60" />
          </el-skeleton>
          <template #footer>
            <div class="text-center">
              <el-link type="primary" :underline="false" @click="goPath('/approval/done')">
                查看全部
              </el-link>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { formatDate } from '@/utils/formatTime'
import * as TaskApi from '@/api/bpm/task'
import * as ContractApi from '@/api/clm/contract'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'Home' })

const router = useRouter()
const userStore = useUserStore()
const nickname = computed(() => userStore.getUser.nickname)

// ========== 任务中心桶 ==========

interface StatCard {
  label: string
  value: number
  icon: string
  color: string
  path: string
  query?: Record<string, string>
}

const statsLoading = ref(true)
const stats = reactive({ todo: 0, draft: 0, approving: 0, rejected: 0, toArchive: 0, signed: 0 })

const statCards = computed<StatCard[]>(() => [
  {
    label: '待办审批',
    value: stats.todo,
    icon: 'ep:bell',
    color: 'var(--el-color-danger)',
    path: '/approval/todo'
  },
  {
    label: '草稿',
    value: stats.draft,
    icon: 'ep:edit-pen',
    color: 'var(--el-color-info)',
    path: '/clm/contract',
    query: { tab: 'draft' }
  },
  {
    label: '审批中',
    value: stats.approving,
    icon: 'ep:clock',
    color: 'var(--el-color-warning)',
    path: '/clm/contract',
    query: { tab: 'approving' }
  },
  {
    label: '已驳回',
    value: stats.rejected,
    icon: 'ep:warning',
    color: 'var(--el-color-danger)',
    path: '/clm/contract',
    query: { tab: 'rejected' }
  },
  {
    label: '待归档',
    value: stats.toArchive,
    icon: 'ep:box',
    color: 'var(--el-color-primary)',
    path: '/clm/contract',
    query: { tab: 'approved' }
  },
  {
    label: '已签订',
    value: stats.signed,
    icon: 'ep:circle-check',
    color: 'var(--el-color-success)',
    path: '/clm/contract',
    query: { tab: 'signed' }
  }
])

/** 待办审批数 */
const getTodoCount = async () => {
  try {
    const data = await TaskApi.getTaskTodoPage({ pageNo: 1, pageSize: 1 })
    stats.todo = data.total ?? 0
  } catch {
    stats.todo = 0
  }
}

/** 我负责的合同数（按审批状态 / 生命周期过滤） */
const getContractCount = async (
  key: 'draft' | 'approving' | 'rejected' | 'toArchive' | 'signed',
  params: { approvalStatus?: number; lifecycleStatus?: number }
) => {
  try {
    const data = await ContractApi.getContractPage({
      pageNo: 1,
      pageSize: 1,
      ownerUserId: userStore.getUser.id,
      ...params
    })
    stats[key] = data.total ?? 0
  } catch {
    stats[key] = 0
  }
}

// ========== 三个面板 ==========

const todoLoading = ref(true)
const todoList = ref<any[]>([])
const contractLoading = ref(true)
const myContractList = ref<ContractApi.ContractVO[]>([])
const doneLoading = ref(true)
const doneList = ref<any[]>([])

/** 我的待办 top 5 */
const getTodoList = async () => {
  try {
    const data = await TaskApi.getTaskTodoPage({ pageNo: 1, pageSize: 5 })
    todoList.value = data.list ?? []
  } catch {
    todoList.value = []
  } finally {
    todoLoading.value = false
  }
}

/** 我负责的合同 top 6 */
const getMyContractList = async () => {
  try {
    const data = await ContractApi.getContractPage({
      pageNo: 1,
      pageSize: 6,
      ownerUserId: userStore.getUser.id
    })
    myContractList.value = data.list ?? []
  } catch {
    myContractList.value = []
  } finally {
    contractLoading.value = false
  }
}

/** 最近审批记录 top 5 */
const getDoneList = async () => {
  try {
    const data = await TaskApi.getTaskDonePage({ pageNo: 1, pageSize: 5 })
    doneList.value = data.list ?? []
  } catch {
    doneList.value = []
  } finally {
    doneLoading.value = false
  }
}

// ========== 跳转 ==========

const goPath = (path: string) => {
  router.push(path)
}

/** 任务桶跳转（带台账 Tab 参数） */
const goCard = (card: { path: string; query?: Record<string, string> }) => {
  router.push({ path: card.path, query: card.query })
}

/** 新建合同 */
const handleCreate = () => {
  router.push({ name: 'ClmContractCreate' })
}

/** 办理 / 查看流程任务 */
const openTask = (row: any) => {
  router.push({
    name: 'BpmProcessInstanceDetail',
    query: { id: row.processInstance?.id, taskId: row.id }
  })
}

/** 合同详情 */
const openContract = (row: ContractApi.ContractVO) => {
  if (!row.id) {
    return
  }
  router.push({ name: 'ClmContractDetail', params: { id: row.id } })
}

/** 初始化 */
onMounted(async () => {
  const countsReady = Promise.all([
    getTodoCount(),
    getContractCount('draft', { approvalStatus: 0, lifecycleStatus: 1 }),
    getContractCount('approving', { approvalStatus: 1 }),
    getContractCount('rejected', { approvalStatus: 3 }),
    getContractCount('toArchive', { approvalStatus: 2, lifecycleStatus: 2 }),
    getContractCount('signed', { lifecycleStatus: 3 })
  ]).finally(() => {
    statsLoading.value = false
  })
  await Promise.all([countsReady, getTodoList(), getMyContractList(), getDoneList()])
})
</script>
