<template>
  <el-table v-if="opinions.length" :data="opinions" size="small" border>
    <el-table-column label="节点" prop="nodeName" width="120" />
    <el-table-column label="处理人" prop="userName" width="100" />
    <el-table-column label="动作" width="90"
      ><template #default="scope">{{ actionText(scope.row.action) }}</template></el-table-column
    >
    <el-table-column label="意见" prop="reason" min-width="160"
      ><template #default="scope">{{ scope.row.reason || '-' }}</template></el-table-column
    >
    <el-table-column label="决定修订" prop="revisionId" width="115" />
    <el-table-column label="时间" width="150"
      ><template #default="scope">{{ formatTime(scope.row.createTime) }}</template></el-table-column
    >
  </el-table>
  <el-empty v-else description="暂无审批意见" :image-size="56" />
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import type { ApprovalOpinionVO } from '@/api/clm/approval'

defineOptions({ name: 'ClmApprovalOpinionList' })
defineProps<{ opinions: ApprovalOpinionVO[] }>()
const actionText = (action?: string) =>
  (
    ({ APPROVE: '同意', REJECT: '拒绝', RETURN: '退回', START: '提交', CANCEL: '取消' }) as Record<
      string,
      string
    >
  )[action || ''] ||
  action ||
  '-'
const formatTime = (value?: string) => (value ? formatDate(new Date(value), 'MM-DD HH:mm') : '-')
</script>
