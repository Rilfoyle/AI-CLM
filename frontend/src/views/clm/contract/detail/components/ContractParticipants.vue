<template>
  <div class="mb-15px">
    <el-button
      v-if="permissions.canManage"
      v-hasPermi="['clm:contract:manage-member']"
      type="primary"
      plain
      @click="openEdit"
    >
      <Icon icon="ep:user" class="mr-5px" /> 编辑参与人
    </el-button>
  </div>
  <el-table v-loading="loading" :data="list" border stripe>
    <el-table-column label="参与人" prop="principalName" min-width="160" />
    <el-table-column label="角色" align="center" width="130">
      <template #default="scope">
        <dict-tag :type="DICT_TYPE.CLM_PARTICIPANT_ROLE" :value="scope.row.roleCode" />
      </template>
    </el-table-column>
    <el-table-column label="查看" align="center" width="90">
      <template #default="scope">
        <el-tag :type="scope.row.canView ? 'success' : 'info'" size="small">
          {{ scope.row.canView ? '是' : '否' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="编辑" align="center" width="90">
      <template #default="scope">
        <el-tag :type="scope.row.canEdit ? 'success' : 'info'" size="small">
          {{ scope.row.canEdit ? '是' : '否' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="下载" align="center" width="90">
      <template #default="scope">
        <el-tag :type="scope.row.canDownload ? 'success' : 'info'" size="small">
          {{ scope.row.canDownload ? '是' : '否' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="管理" align="center" width="90">
      <template #default="scope">
        <el-tag :type="scope.row.canManage ? 'success' : 'info'" size="small">
          {{ scope.row.canManage ? '是' : '否' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column
      label="加入时间"
      align="center"
      prop="createTime"
      width="170"
      :formatter="dateFormatter"
    />
  </el-table>

  <!-- 编辑参与人弹窗 -->
  <Dialog v-model="dialogVisible" title="编辑参与人" width="900px">
    <el-alert
      class="mb-10px"
      type="info"
      :closable="false"
      show-icon
      title="负责人（OWNER）由合同负责人字段决定，此处只读；其余参与人可增删改。"
    />
    <el-table :data="editRows" border>
      <el-table-column label="用户" min-width="200">
        <template #default="scope">
          <span v-if="scope.row.roleCode === 'OWNER'">{{ scope.row.principalName }}</span>
          <el-select
            v-else
            v-model="scope.row.principalId"
            filterable
            placeholder="请选择用户"
            class="w-full"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.nickname"
              :value="user.id"
              :disabled="isUserTaken(user.id, scope.$index)"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="150">
        <template #default="scope">
          <dict-tag
            v-if="scope.row.roleCode === 'OWNER'"
            :type="DICT_TYPE.CLM_PARTICIPANT_ROLE"
            :value="scope.row.roleCode"
          />
          <el-select
            v-else
            v-model="scope.row.roleCode"
            class="w-full"
            @change="onRoleChange(scope.row)"
          >
            <el-option label="协作者" value="COLLABORATOR" />
            <el-option label="查看者" value="VIEWER" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="查看" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.canView" :disabled="scope.row.roleCode === 'OWNER'" />
        </template>
      </el-table-column>
      <el-table-column label="编辑" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.canEdit" :disabled="scope.row.roleCode === 'OWNER'" />
        </template>
      </el-table-column>
      <el-table-column label="下载" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.canDownload" :disabled="scope.row.roleCode === 'OWNER'" />
        </template>
      </el-table-column>
      <el-table-column label="管理" align="center" width="80">
        <template #default="scope">
          <el-switch v-model="scope.row.canManage" :disabled="scope.row.roleCode === 'OWNER'" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="80">
        <template #default="scope">
          <el-button
            v-if="scope.row.roleCode !== 'OWNER'"
            link
            type="danger"
            @click="removeRow(scope.$index)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button class="mt-10px" type="primary" plain @click="addRow">
      <Icon icon="ep:plus" class="mr-5px" /> 添加参与人
    </el-button>
    <template #footer>
      <el-button :disabled="saving" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DICT_TYPE } from '@/utils/dict'
import { dateFormatter } from '@/utils/formatTime'
import * as ContractApi from '@/api/clm/contract'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'ClmContractParticipants' })

const props = defineProps<{ contract: ContractApi.ContractVO }>()
const emit = defineEmits<{ refresh: [] }>()

const message = useMessage()

const loading = ref(false)
const saving = ref(false)
const list = ref<ContractApi.ParticipantVO[]>([])
const userList = ref<UserApi.UserVO[]>([])
const dialogVisible = ref(false)
const editRows = ref<ContractApi.ParticipantVO[]>([])

const permissions = computed<Partial<ContractApi.ContractPermissionsVO>>(
  () => props.contract.permissions || {}
)

/** 加载参与人列表 */
const getList = async () => {
  if (!props.contract.id) return
  loading.value = true
  try {
    list.value = (await ContractApi.getParticipantList(props.contract.id)) || []
  } finally {
    loading.value = false
  }
}

/** 打开编辑弹窗 */
const openEdit = async () => {
  if (userList.value.length === 0) {
    userList.value = await UserApi.getSimpleUserList()
  }
  editRows.value = list.value.map((item) => ({ ...item }))
  dialogVisible.value = true
}

/** 用户是否已被其他行选择 */
const isUserTaken = (userId: number, index: number) => {
  return editRows.value.some((row, i) => i !== index && row.principalId === userId)
}

/** 角色切换时给默认权限 */
const onRoleChange = (row: ContractApi.ParticipantVO) => {
  if (row.roleCode === 'VIEWER') {
    row.canView = true
    row.canEdit = false
    row.canDownload = true
    row.canManage = false
  } else if (row.roleCode === 'COLLABORATOR') {
    row.canView = true
    row.canEdit = true
    row.canDownload = true
    row.canManage = false
  }
}

/** 添加行 */
const addRow = () => {
  editRows.value.push({
    principalType: 'USER',
    principalId: undefined as unknown as number,
    roleCode: 'COLLABORATOR',
    canView: true,
    canEdit: true,
    canDownload: true,
    canManage: false
  })
}

/** 移除行 */
const removeRow = (index: number) => {
  editRows.value.splice(index, 1)
}

/** 保存 */
const submitForm = async () => {
  const participants = editRows.value.filter((row) => row.roleCode !== 'OWNER')
  if (participants.some((row) => !row.principalId)) {
    message.warning('请为每个参与人选择用户')
    return
  }
  saving.value = true
  try {
    await ContractApi.saveParticipants({
      contractId: props.contract.id!,
      participants: participants.map((row) => ({
        principalType: row.principalType || 'USER',
        principalId: row.principalId,
        roleCode: row.roleCode,
        canView: !!row.canView,
        canEdit: !!row.canEdit,
        canDownload: !!row.canDownload,
        canManage: !!row.canManage
      }))
    })
    message.success('保存成功')
    dialogVisible.value = false
    await getList()
    emit('refresh')
  } finally {
    saving.value = false
  }
}

watch(
  () => [props.contract.id, props.contract.updateTime, props.contract.ownerUserId],
  () => {
    getList()
  },
  { immediate: true }
)
</script>
