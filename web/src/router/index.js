import { createWebHistory, createRouter } from 'vue-router'
/* Layout */
import Layout from '@/layout'

/**
 * Note: 路由配置项
 *
 * hidden: true                     // 当设置 true 的时候该路由不会再侧边栏出现 如401，login等页面，或者如一些编辑页面/edit/1
 * alwaysShow: true                 // 当你一个路由下面的 children 声明的路由大于1个时，自动会变成嵌套的模式--如组件页面
 *                                  // 只有一个时，会将那个子路由当做根路由显示在侧边栏--如引导页面
 *                                  // 若你想不管路由下面的 children 声明的个数都显示你的根路由
 *                                  // 你可以设置 alwaysShow: true，这样它就会忽略之前定义的规则，一直显示根路由
 * redirect: noRedirect             // 当设置 noRedirect 的时候该路由在面包屑导航中不可被点击
 * name:'router-name'               // 设定路由的名字，一定要填写不然使用<keep-alive>时会出现各种问题
 * query: '{"id": 1, "name": "ry"}' // 访问路由的默认传递参数
 * roles: ['admin', 'common']       // 访问路由的角色权限
 * permissions: ['a:a:a', 'b:b:b']  // 访问路由的菜单权限
 * meta : {
    noCache: true                   // 如果设置为true，则不会被 <keep-alive> 缓存(默认 false)
    title: 'title'                  // 设置该路由在侧边栏和面包屑中展示的名字
    icon: 'svg-name'                // 设置该路由的图标，对应路径src/assets/icons/svg
    breadcrumb: false               // 如果设置为false，则不会在breadcrumb面包屑中显示
    activeMenu: '/system/user'      // 当路由设置了该属性，则会高亮相对应的侧边栏。
  }
 */

export const medicalRoutes = [
  { path: '/session-recovery', component: () => import('@/views/medical/session-recovery.vue'), name: 'MedicalSessionRecovery', hidden: true,
    meta: { medical: true, public: true, noCache: true, title: '重新核查业务会话' } },
  { path: '/enterprises/members', component: Layout, hidden: true, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/enterprise-members.vue'), name: 'MedicalEnterpriseMembers',
      meta: { medical: true, noCache: true, title: '企业成员与授权', activeMenu: '/projects' } }
  ] },
  { path: '/member-invitations/acceptances', component: Layout, hidden: true, meta: { medical: true, public: true }, children: [
    { path: '', component: () => import('@/views/medical/invitation-accept.vue'), name: 'MedicalInvitationAcceptance',
      meta: { medical: true, public: true, noCache: true, title: '接受企业邀请', activeMenu: '/projects' } }
  ] },
  { path: '/enterprises/roles', component: Layout, hidden: true, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/enterprise-roles.vue'), name: 'MedicalEnterpriseRoles',
      meta: { medical: true, noCache: true, title: '企业岗位', activeMenu: '/projects' } }
  ] },
  { path: '/sources/uploads', component: Layout, hidden: true, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/enterprise-source-uploads.vue'), name: 'MedicalEnterpriseSourceUploads',
      meta: { medical: true, noCache: true, title: '上传企业资料', activeMenu: '/library' } }
  ] },
  { path: '/library', component: Layout, meta: { medical: true }, children: [
    { path: 'search/exact', component: () => import('@/views/medical/source-exact-search.vue'), name: 'MedicalSourceExactSearch', hidden: true,
      meta: { medical: true, noCache: true, title: '结构化字段等值检索', activeMenu: '/library' } },
    { path: 'versions/:sourceVersion/exact-fields', component: () => import('@/views/medical/source-exact-fields.vue'), name: 'MedicalSourceExactFields', hidden: true,
      meta: { medical: true, noCache: true, title: '结构化字段核准', activeMenu: '/library' } },
    { path: 'versions/:sourceVersion/rights', component: () => import('@/views/medical/source-rights.vue'), name: 'MedicalSourceRights', hidden: true,
      meta: { medical: true, noCache: true, title: '来源权利审查', activeMenu: '/library' } },
    { path: '', component: () => import('@/views/medical/source-catalogue.vue'), name: 'MedicalSourceCatalogue',
      meta: { medical: true, noCache: true, title: '资料中心', icon: 'documentation' } },
    { path: 'candidates', component: () => import('@/views/medical/source-candidates.vue'), name: 'MedicalSourceCandidates', hidden: true,
      meta: { medical: true, noCache: true, title: '网络候选', activeMenu: '/library' } },
    { path: 'collections', component: () => import('@/views/medical/collections.vue'), name: 'MedicalCollections', hidden: true,
      meta: { medical: true, noCache: true, title: '资料集', activeMenu: '/library' } },
    { path: 'search', component: () => import('@/views/medical/source-search.vue'), name: 'MedicalSourceSearch', hidden: true,
      meta: { medical: true, noCache: true, title: '资料全文检索', activeMenu: '/library' } },
    { path: 'processing', component: () => import('@/views/medical/source-processing.vue'), name: 'MedicalSourceProcessing', hidden: true,
      meta: { medical: true, noCache: true, title: '资料处理队列', activeMenu: '/library' } },
    { path: 'artifacts', component: () => import('@/views/medical/artifact-catalogue.vue'), name: 'MedicalArtifactCatalogue', hidden: true,
      meta: { medical: true, noCache: true, title: '任务成果', activeMenu: '/library' } },
    { path: 'versions/:sourceVersion', component: () => import('@/views/medical/source-governance.vue'), name: 'MedicalSourceGovernance', hidden: true,
      meta: { medical: true, noCache: true, title: '资料版本核准', activeMenu: '/library' } },
    { path: 'collections/new', component: () => import('@/views/medical/collections.vue'), name: 'MedicalCollectionCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '创建资料集', collectionCreate: true, activeMenu: '/library' } },
    { path: 'collections/:collectionId', component: () => import('@/views/medical/collections.vue'), name: 'MedicalCollectionDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '资料集固定版本', activeMenu: '/library' } }
  ] },
  { path: '/enterprises/registration-reviews', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/enterprise-registration-review.vue'), name: 'MedicalEnterpriseReview',
      meta: { medical: true, noCache: true, title: '企业申请人工审查', icon: 'peoples' } },
    { path: ':registrationId', component: () => import('@/views/medical/enterprise-registration-review.vue'), name: 'MedicalEnterpriseReviewDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '企业申请人工审查', activeMenu: '/enterprises/registration-reviews' } }
  ] },
  { path: '/enterprises/registrations', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/enterprise-registration.vue'), name: 'MedicalEnterpriseRegistration',
      meta: { medical: true, noCache: true, title: '企业登记申请', icon: 'peoples' } }
  ] },
  { path: '/templates', component: Layout, meta: { medical: true }, children: [
    { path: 'new', component: () => import('@/views/medical/template-create.vue'), name: 'MedicalTemplateCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '创建模板草稿', activeMenu: '/templates' } },
    { path: '', component: () => import('@/views/medical/template-approval.vue'), name: 'MedicalTemplateApprovalEntry',
      meta: { medical: true, noCache: true, title: '模板人工审查', icon: 'tree-table' } },
    { path: ':templateId/approval', component: () => import('@/views/medical/template-approval.vue'), name: 'MedicalTemplateApproval', hidden: true,
      meta: { medical: true, noCache: true, title: '模板人工审查', activeMenu: '/templates' } }
  ] },
  { path: '/rules', component: Layout, meta: { medical: true }, children: [
    { path: 'new', component: () => import('@/views/medical/rule-create.vue'), name: 'MedicalRuleCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '创建规则草稿', activeMenu: '/rules' } },
    { path: '', component: () => import('@/views/medical/rule-management.vue'), name: 'MedicalRuleEntry',
      meta: { medical: true, noCache: true, title: '规则管理与人工审查', icon: 'tree-table' } },
    { path: ':ruleId', component: () => import('@/views/medical/rule-management.vue'), name: 'MedicalRuleDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '固定规则版本', activeMenu: '/rules' } }
  ] },
  { path: '/document-save-audit', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/document-save-audit.vue'), name: 'MedicalDocumentSaveAudit',
      meta: { medical: true, noCache: true, title: '文稿保存审计', icon: 'list' } }
  ] },
  { path: '/evaluation', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/evaluation-results.vue'), name: 'MedicalEvaluationEntry',
      meta: { medical: true, noCache: true, title: '评测与结果', icon: 'chart' } },
    { path: 'new', component: () => import('@/views/medical/evaluation-results.vue'), name: 'MedicalEvaluationCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '登记固定评测定义', evaluationCreate: true, activeMenu: '/evaluation' } },
    { path: ':evaluationId', component: () => import('@/views/medical/evaluation-results.vue'), name: 'MedicalEvaluationDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '固定评测记录', activeMenu: '/evaluation' } }
  ] },
  { path: '/simulation-model-configurations', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/simulation-model-configurations.vue'), name: 'MedicalSimulationModelConfigurations',
      meta: { medical: true, noCache: true, title: '模拟模型配置草稿', icon: 'system' } }
  ] },
  { path: '/model-configurations', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/model-configurations.vue'), name: 'MedicalModelConfigurations',
      meta: { medical: true, noCache: true, title: '模型配置草稿', icon: 'system' } }
  ] },
  { path: '/access/denied', component: () => import('@/views/medical/access-denied.vue'), name: 'MedicalAccessDenied', hidden: true,
    meta: { medical: true, public: true, noCache: true, title: '访问受限' } },
  { path: '/access', alias: ['/auth/login', '/local-auth/login.html'], component: () => import('@/views/login.vue'), hidden: true,
    meta: { medical: true, public: true, noCache: true, title: '业务账户' } },
  { path: '/account', component: Layout, hidden: true, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/system/user/profile/index.vue'), name: 'MedicalAccount',
      meta: { medical: true, noCache: true, title: '本人账户', icon: 'user' } }
  ] },
  { path: '/projects', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/projects.vue'), name: 'MedicalProjects',
      meta: { medical: true, noCache: true, title: '项目中心', icon: 'list' } },
    { path: 'new', component: () => import('@/views/medical/project-write.vue'), name: 'MedicalProjectCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '创建项目', projectWrite: 'CREATE', activeMenu: '/projects' } },
    { path: ':projectId/profile', component: () => import('@/views/medical/project-write.vue'), name: 'MedicalProjectProfile', hidden: true,
      meta: { medical: true, noCache: true, title: '修订项目属性', projectWrite: 'REVISE', activeMenu: '/projects' } },
    { path: ':projectId/status', component: () => import('@/views/medical/project-write.vue'), name: 'MedicalProjectStatus', hidden: true,
      meta: { medical: true, noCache: true, title: '决定项目状态', projectWrite: 'DECIDE', activeMenu: '/projects' } },
    { path: ':projectId/tasks', component: () => import('@/views/medical/tasks.vue'), name: 'MedicalProjectTasks', hidden: true,
      meta: { medical: true, noCache: true, title: '项目任务', activeMenu: '/projects' } },
    { path: ':projectId/tasks/new', component: () => import('@/views/medical/task-create.vue'), name: 'MedicalTaskCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '创建项目任务', activeMenu: '/projects' } },
    { path: ':projectId/tasks/:taskId/config', component: () => import('@/views/medical/task-detail.vue'), name: 'MedicalTaskDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '任务当前状态', activeMenu: '/projects' } },
    { path: ':projectId/tasks/:taskId/documents/:documentId/write', component: () => import('@/views/medical/task-detail.vue'), name: 'MedicalTaskWrite', hidden: true,
      meta: { medical: true, noCache: true, title: '撰写核验', activeMenu: '/projects' } },
    { path: ':projectId/tasks/:taskId/documents/:documentId/review', component: () => import('@/views/medical/task-detail.vue'), name: 'MedicalTaskReview', hidden: true,
      meta: { medical: true, noCache: true, title: '审查签发', activeMenu: '/projects' } },
    { path: ':projectId/templates', component: () => import('@/views/medical/template-applicability.vue'), name: 'MedicalProjectTemplates', hidden: true,
      meta: { medical: true, noCache: true, title: '项目适用模板', activeMenu: '/projects' } },
    { path: ':projectId/uploads', component: () => import('@/views/medical/source-uploads.vue'), name: 'MedicalSourceUploads', hidden: true,
      meta: { medical: true, noCache: true, title: '上传项目资料', activeMenu: '/projects' } },
    { path: ':projectId', component: () => import('@/views/medical/project.vue'), name: 'MedicalProject', hidden: true,
      meta: { medical: true, noCache: true, title: '项目详情', activeMenu: '/projects' } }
  ] },
  { path: '/scenarios', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/scenarios.vue'), name: 'MedicalScenarios',
      meta: { medical: true, noCache: true, title: '场景目录', icon: 'list' } }
  ] },
  { path: '/agents', component: Layout, meta: { medical: true }, children: [
    { path: 'execution-policies/new', component: () => import('@/views/medical/execution-policy-create.vue'), name: 'MedicalExecutionPolicyCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '登记执行策略', activeMenu: '/agents' } },
    { path: 'execution-policies/:versionId', component: () => import('@/views/medical/execution-policy-detail.vue'), name: 'MedicalExecutionPolicyDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '执行策略固定详情', activeMenu: '/agents' } },
    { path: 'execution-policies', component: () => import('@/views/medical/execution-policy-list.vue'), name: 'MedicalExecutionPolicies', hidden: true,
      meta: { medical: true, noCache: true, title: '执行策略', activeMenu: '/agents' } },
    { path: 'retention', component: () => import('@/views/medical/runtime-retention.vue'), name: 'MedicalRuntimeRetention', hidden: true,
      meta: { medical: true, noCache: true, title: '保存策略', activeMenu: '/agents' } },
    { path: 'model-runtime', component: () => import('@/views/medical/model-runtime.vue'), name: 'MedicalModelRuntime', hidden: true, meta: { title: '模型连接与资格', medical: true, noCache: true, activeMenu: '/agents' } },
    { path: 'drafts', component: () => import('@/views/medical/formal-agent-drafts.vue'), name: 'MedicalFormalAgentDrafts', hidden: true,
      meta: { medical: true, noCache: true, title: '候选与运行能力草稿', activeMenu: '/agents' } },
    { path: 'capabilities/new', component: () => import('@/views/medical/formal-capability-create.vue'), name: 'MedicalFormalCapabilityCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '登记正式能力草稿', activeMenu: '/agents' } },
    { path: 'capabilities/:recordId', component: () => import('@/views/medical/formal-capability-detail.vue'), name: 'MedicalFormalCapabilityDetail', hidden: true,
      meta: { medical: true, noCache: true, title: '正式能力固定草稿', activeMenu: '/agents' } },
    { path: 'capabilities', component: () => import('@/views/medical/formal-capability-list.vue'), name: 'MedicalFormalCapabilities', hidden: true,
      meta: { medical: true, noCache: true, title: '正式能力草稿', activeMenu: '/agents' } },
    { path: '', component: () => import('@/views/medical/agents.vue'), name: 'MedicalAgents',
      meta: { medical: true, noCache: true, title: 'Agent 管理', icon: 'system' } },
    { path: 'simulation-preparation', component: () => import('@/views/medical/simulation-preparation.vue'), name: 'MedicalSimulationPreparation',
      meta: { medical: true, noCache: true, title: '模拟评测准备', activeMenu: '/agents' } },
    { path: 'simulation-rules/:ruleVersion?', component: () => import('@/views/medical/simulation-rule-management.vue'), name: 'MedicalSimulationRules',
      meta: { medical: true, noCache: true, title: '模拟评分规则', activeMenu: '/agents' } }
  ] },
  { path: '/agents/budgets', component: Layout, meta: { medical: true }, children: [
    { path: '', component: () => import('@/views/medical/budgets.vue'), name: 'MedicalBudgets',
      meta: { medical: true, noCache: true, title: '模型预算', icon: 'money' } },
    { path: 'new', component: () => import('@/views/medical/budget-write.vue'), name: 'MedicalBudgetCreate', hidden: true,
      meta: { medical: true, noCache: true, title: '创建预算', budgetWrite: 'CREATE', activeMenu: '/agents/budgets' } },
    { path: ':budgetId/decisions', component: () => import('@/views/medical/budget-write.vue'), name: 'MedicalBudgetApprove', hidden: true,
      meta: { medical: true, noCache: true, title: '追加预算批准', budgetWrite: 'APPROVE', activeMenu: '/agents/budgets' } },
    { path: ':budgetId/revisions', component: () => import('@/views/medical/budget-history.vue'), name: 'MedicalBudgetHistory', hidden: true,
      meta: { medical: true, noCache: true, title: '预算批准历史', activeMenu: '/agents/budgets' } },
    { path: ':budgetId/revisions/:budgetRevision', component: () => import('@/views/medical/budget-history.vue'), name: 'MedicalBudgetRevision', hidden: true,
      meta: { medical: true, noCache: true, title: '预算批准版本', activeMenu: '/agents/budgets' } },
    { path: ':budgetId', component: () => import('@/views/medical/budget.vue'), name: 'MedicalBudget', hidden: true,
      meta: { medical: true, noCache: true, title: '预算详情', activeMenu: '/agents/budgets' } }
  ] }
]

// 公共路由
export const constantRoutes = [
  ...medicalRoutes,
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true,
    meta: { medical: import.meta.env.VITE_APP_ENV === 'medical', public: true, noCache: true }
  },
  {
    path: '/register',
    component: () => import('@/views/register'),
    hidden: true
  },
  {
    path: "/:pathMatch(.*)*",
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  {
    path: '',
    component: Layout,
    redirect: '/index',
    children: [
      {
        path: '/index',
        component: () => import('@/views/index'),
        name: 'Index',
        meta: { title: import.meta.env.VITE_APP_ENV === 'medical' ? '工作台' : '首页', icon: 'dashboard', affix: true,
          medical: import.meta.env.VITE_APP_ENV === 'medical' }
      }
    ]
  },
  {
    path: '/lock',
    component: () => import('@/views/lock'),
    hidden: true,
    meta: { title: '锁定屏幕' }
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile/:activeTab?',
        component: () => import('@/views/system/user/profile/index'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  }
]

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:edit'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:edit'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  },
  {
    path: '/monitor/job-log',
    component: Layout,
    hidden: true,
    permissions: ['monitor:job:list'],
    children: [
      {
        path: 'index/:jobId(\\d+)',
        component: () => import('@/views/monitor/job/log'),
        name: 'JobLog',
        meta: { title: '调度日志', activeMenu: '/monitor/job' }
      }
    ]
  },
  {
    path: '/tool/gen-edit',
    component: Layout,
    hidden: true,
    permissions: ['tool:gen:edit'],
    children: [
      {
        path: 'index/:tableId(\\d+)',
        component: () => import('@/views/tool/gen/editTable'),
        name: 'GenEdit',
        meta: { title: '修改生成配置', activeMenu: '/tool/gen' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
})

export default router
