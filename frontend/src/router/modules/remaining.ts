import { Layout } from '@/utils/routerHelper'
import type { RouteLocationNormalized } from 'vue-router'

const { t } = useI18n()

const redirectTo = (path: string) => (to: RouteLocationNormalized) => ({
  path,
  query: to.query,
  hash: to.hash
})

// TuriX only keeps routes that are required by the phase-one contract workflow.
// Business menus continue to come from the backend permission model.
const remainingRouter: AppRouteRecordRaw[] = [
  {
    path: '/redirect',
    component: Layout,
    name: 'RedirectRoot',
    children: [
      {
        path: '/redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/Redirect/Redirect.vue'),
        meta: {}
      }
    ],
    meta: { hidden: true, noTagsView: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/index',
    name: 'Home',
    meta: {},
    children: [
      {
        path: 'index',
        component: () => import('@/views/Home/Index.vue'),
        name: 'Index',
        meta: {
          title: t('router.home'),
          icon: 'ep:home-filled',
          noCache: false,
          affix: true
        }
      }
    ]
  },
  {
    path: '/user',
    component: Layout,
    name: 'UserInfo',
    meta: { hidden: true },
    children: [
      {
        path: 'profile',
        component: () => import('@/views/Profile/Index.vue'),
        name: 'Profile',
        meta: {
          canTo: true,
          hidden: true,
          noTagsView: false,
          icon: 'ep:user',
          title: t('common.profile')
        }
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/Login/Login.vue'),
    name: 'Login',
    meta: {
      hidden: true,
      title: t('router.login'),
      noTagsView: true
    }
  },
  {
    path: '/403',
    component: () => import('@/views/Error/403.vue'),
    name: 'NoAccess',
    meta: { hidden: true, title: '403', noTagsView: true }
  },
  {
    path: '/404',
    component: () => import('@/views/Error/404.vue'),
    name: 'NoFound',
    meta: { hidden: true, title: '404', noTagsView: true }
  },
  {
    path: '/500',
    component: () => import('@/views/Error/500.vue'),
    name: 'Error',
    meta: { hidden: true, title: '500', noTagsView: true }
  },
  {
    path: '/clm',
    component: Layout,
    name: 'ClmRemaining',
    meta: { hidden: true },
    children: [
      {
        path: 'contract/create',
        name: 'ClmContractCreate',
        redirect: redirectTo('/clm/drafting/draft-center'),
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '新建合同',
          activeMenu: '/clm/drafting/draft-center'
        }
      },
      {
        path: 'collaboration/detail/:id',
        component: () => import('@/views/clm/collaboration/index.vue'),
        name: 'ClmCollaborationDetail',
        props: (route) => ({ id: route.params.id }),
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '合同协同详情',
          activeMenu: '/clm/drafting/collaboration'
        }
      },
      {
        path: 'approval/task/:taskId',
        component: () => import('@/views/clm/approval/index.vue'),
        name: 'ClmApprovalTask',
        props: (route) => ({ taskId: route.params.taskId }),
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '合同审批任务',
          activeMenu: '/clm/approval-management/approval'
        }
      },
      {
        path: 'approval/history/:contractId',
        component: () => import('@/views/clm/approval/index.vue'),
        name: 'ClmApprovalHistory',
        props: (route) => ({ contractId: route.params.contractId }),
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '合同审批历史',
          activeMenu: '/clm/approval-management/approval'
        }
      },
      {
        path: 'contract/detail/:id',
        component: () => import('@/views/clm/contract/detail/index.vue'),
        name: 'ClmContractDetail',
        props: (route) => ({ id: route.params.id }),
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '合同详情',
          activeMenu: '/clm/drafting/contract'
        }
      },
      {
        path: 'contract/online-edit',
        component: () => import('@/views/clm/contract/onlineEdit/index.vue'),
        name: 'ClmContractOnlineEdit',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '在线编辑',
          activeMenu: '/clm/drafting/contract'
        }
      },
      {
        path: 'contract/compare',
        component: () => import('@/views/clm/contract/compare/index.vue'),
        name: 'ClmContractCompare',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '版本并排查看',
          activeMenu: '/clm/drafting/contract'
        }
      },
      {
        path: 'base-settings/contract-type/editor',
        component: () => import('@/views/clm/contractType/editor/index.vue'),
        name: 'ClmContractTypeEditor',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '页面布局配置',
          activeMenu: '/clm/base-settings/page-layout'
        }
      },
      {
        path: 'approval-management/workflow-settings/process/editor',
        component: () => import('@/views/clm/governance/process/editor/index.vue'),
        name: 'ClmGovernanceProcessEditor',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: '编辑流程图',
          activeMenu: '/clm/approval-management/workflow-settings/process'
        }
      },
      {
        path: 'draft-center',
        name: 'ClmLegacyDraftCenter',
        redirect: redirectTo('/clm/drafting/draft-center'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'contract',
        name: 'ClmLegacyContractList',
        redirect: redirectTo('/clm/drafting/contract'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'collaboration',
        name: 'ClmLegacyCollaboration',
        redirect: redirectTo('/clm/drafting/collaboration'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'approval',
        name: 'ClmLegacyApproval',
        redirect: redirectTo('/clm/approval-management/approval'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/process',
        name: 'ClmLegacyProcessDefinitions',
        redirect: redirectTo('/clm/approval-management/workflow-settings/process'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/process/editor',
        name: 'ClmLegacyProcessEditor',
        redirect: redirectTo('/clm/approval-management/workflow-settings/process/editor'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/routing',
        name: 'ClmLegacyWorkflowRouting',
        redirect: redirectTo('/clm/approval-management/workflow-settings/routing'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/contract-type',
        name: 'ClmLegacyContractType',
        redirect: redirectTo('/clm/base-settings/contract-type'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/contract-type/editor',
        name: 'ClmLegacyContractTypeEditor',
        redirect: redirectTo('/clm/base-settings/contract-type/editor'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'contract-type',
        name: 'ClmLegacyRootContractType',
        redirect: redirectTo('/clm/base-settings/contract-type'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'contract-type/editor',
        name: 'ClmLegacyRootContractTypeEditor',
        redirect: redirectTo('/clm/base-settings/contract-type/editor'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/template',
        name: 'ClmLegacyTemplate',
        redirect: redirectTo('/clm/template'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/numbering',
        name: 'ClmLegacyNumbering',
        redirect: redirectTo('/clm/base-settings/numbering'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/settings/permission',
        name: 'ClmLegacyPermission',
        redirect: redirectTo('/clm/base-settings/permission'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/parties/directory',
        name: 'ClmLegacyPartyDirectory',
        redirect: redirectTo('/clm/basic-data/directory'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/parties/import',
        name: 'ClmLegacyPartyImport',
        redirect: redirectTo('/clm/basic-data/import'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/exceptions/issues',
        name: 'ClmLegacyGovernanceIssues',
        redirect: redirectTo('/clm/exceptions/issues'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/exceptions/reconciliation',
        name: 'ClmLegacyReconciliation',
        redirect: redirectTo('/clm/exceptions/reconciliation'),
        meta: { hidden: true, noTagsView: true }
      },
      {
        path: 'governance/exceptions/handover',
        name: 'ClmLegacyHandover',
        redirect: redirectTo('/clm/handover/owner-change'),
        meta: { hidden: true, noTagsView: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/Error/404.vue'),
    name: 'NotFound',
    meta: {
      title: '404',
      hidden: true,
      breadcrumb: false
    }
  }
]

export default remainingRouter
