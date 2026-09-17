<script lang="ts" setup>
import { ElMessage, ElMessageBox } from 'element-plus'

import avatarImg from '@/assets/imgs/logo.png'
import { switchDemoRole, type DemoRole, type DemoRoleCode } from '@/api/login'
import type { TokenType } from '@/api/login/types'
import { deleteUserCache } from '@/hooks/web/useCache'
import { useDesign } from '@/hooks/web/useDesign'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { useUserStore } from '@/store/modules/user'
import { setToken } from '@/utils/auth'
import LockDialog from './components/LockDialog.vue'
import LockPage from './components/LockPage.vue'
import { useLockStore } from '@/store/modules/lock'

defineOptions({ name: 'UserInfo' })

const { t } = useI18n()

const { push, replace } = useRouter()

const userStore = useUserStore()

const tagsViewStore = useTagsViewStore()

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('user-info')

const avatar = computed(() => userStore.user.avatar || avatarImg)
const userName = computed(() => userStore.user.nickname ?? 'TuriX 用户')

const demoRoleOptions: Array<{
  role: DemoRole
  roleCode: DemoRoleCode
  label: string
  description: string
}> = [
  { role: 'business', roleCode: 'clm_business', label: '业务人员', description: '起草与提交' },
  { role: 'legal', roleCode: 'clm_legal', label: '法务人员', description: '协同与法务审核' },
  {
    role: 'contractadmin',
    roleCode: 'clm_contract_admin',
    label: '合同管理员',
    description: '合同复核与配置'
  },
  {
    role: 'systemadmin',
    roleCode: 'clm_system_admin',
    label: '系统管理员',
    description: '组织与系统设置'
  }
]
const demoRoleSwitchEnabled = import.meta.env.VITE_APP_DEMO_ROLE_SWITCH_ENABLE === 'true'
const currentDemoRole = computed(() =>
  demoRoleOptions.find((item) => userStore.roles.includes(item.roleCode))
)
const showDemoRoleSwitch = computed(() => demoRoleSwitchEnabled && Boolean(currentDemoRole.value))
const switchingRole = ref<DemoRole>()

const changeDemoRole = async (target: DemoRole) => {
  if (switchingRole.value || target === currentDemoRole.value?.role) return
  const roleCode = demoRoleOptions.find((item) => item.role === target)?.roleCode
  if (!roleCode) return
  switchingRole.value = target
  try {
    const token = await switchDemoRole(roleCode)
    setToken(token as TokenType)
    deleteUserCache()
    tagsViewStore.delAllViews()
    userStore.resetState()
    ElMessage.success('测试身份已切换，正在刷新工作台')
    // A hard navigation rebuilds both static and permission routes for the new token.
    // Calling resetRouter() first would temporarily remove the current static /index
    // child route and make Vue Router report a false "No match" warning.
    window.location.replace('/index')
  } catch {
    switchingRole.value = undefined
  }
}

// 锁定屏幕
const lockStore = useLockStore()
const getIsLock = computed(() => lockStore.getLockInfo?.isLock ?? false)
const dialogVisible = ref<boolean>(false)
const lockScreen = () => {
  dialogVisible.value = true
}

const loginOut = async () => {
  try {
    await ElMessageBox.confirm(t('common.loginOutMessage'), t('common.reminder'), {
      confirmButtonText: t('common.ok'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await userStore.loginOut()
    tagsViewStore.delAllViews()
    replace('/login?redirect=/index')
  } catch {}
}
const toProfile = async () => {
  push('/user/profile')
}
</script>

<template>
  <ElDropdown class="custom-hover" :class="prefixCls" trigger="click">
    <div class="flex items-center">
      <ElAvatar :src="avatar" alt="" class="w-[calc(var(--logo-height)-25px)] rounded-[50%]" />
      <span class="pl-[5px] text-14px text-[var(--top-header-text-color)] <lg:hidden">
        {{ userName }}
      </span>
    </div>
    <template #dropdown>
      <ElDropdownMenu>
        <template v-if="showDemoRoleSwitch">
          <ElDropdownItem disabled class="demo-role-current">
            <div class="demo-role-current__content">
              <span>当前身份</span>
              <strong>{{ currentDemoRole?.label }}</strong>
            </div>
          </ElDropdownItem>
          <ElDropdownItem
            v-for="item in demoRoleOptions"
            :key="item.role"
            :disabled="Boolean(switchingRole) || item.role === currentDemoRole?.role"
            @click="changeDemoRole(item.role)"
          >
            <Icon
              :icon="
                switchingRole === item.role
                  ? 'ep:loading'
                  : item.role === currentDemoRole?.role
                    ? 'ep:check'
                    : 'ep:user'
              "
              :class="{ 'is-loading': switchingRole === item.role }"
            />
            <div class="demo-role-option">
              <span>{{ item.label }}</span>
              <small>{{ item.description }}</small>
            </div>
          </ElDropdownItem>
        </template>
        <ElDropdownItem>
          <Icon icon="ep:tools" />
          <div @click="toProfile">{{ t('common.profile') }}</div>
        </ElDropdownItem>
        <ElDropdownItem divided>
          <Icon icon="ep:lock" />
          <div @click="lockScreen">{{ t('lock.lockScreen') }}</div>
        </ElDropdownItem>
        <ElDropdownItem divided @click="loginOut">
          <Icon icon="ep:switch-button" />
          <div>{{ t('common.loginOut') }}</div>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>

  <LockDialog v-if="dialogVisible" v-model="dialogVisible" />

  <teleport to="body">
    <transition name="fade-bottom" mode="out-in">
      <LockPage v-if="getIsLock" />
    </transition>
  </teleport>
</template>

<style scoped lang="scss">
.fade-bottom-enter-active,
.fade-bottom-leave-active {
  transition:
    opacity 0.25s,
    transform 0.3s;
}

.fade-bottom-enter-from {
  opacity: 0;
  transform: translateY(-10%);
}

.fade-bottom-leave-to {
  opacity: 0;
  transform: translateY(10%);
}

:deep(.demo-role-current) {
  min-width: 220px;
  opacity: 1;
}

.demo-role-current__content {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-secondary);

  strong {
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

.demo-role-option {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 20px;

  small {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }
}

.is-loading {
  animation: demo-role-spin 0.9s linear infinite;
}

@keyframes demo-role-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
