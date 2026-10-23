// 分类只负责展示，入口继续来自当前已授权的可见路由。
const businessGroups = [
  { id: 'workbench', title: '工作台', icon: 'dashboard', paths: ['/index'], direct: true },
  { id: 'projects', title: '项目与业务', icon: 'list', paths: ['/projects', '/scenarios', '/connections'] },
  { id: 'documents', title: '资料与文稿', icon: 'documentation', paths: ['/library', '/sources/uploads', '/templates', '/rules', '/document-save-audit'] },
  { id: 'agents', title: 'Agent 与模型', icon: 'system', paths: ['/agents', '/agents/drafts', '/agents/capabilities', '/model-configurations', '/agents/model-runtime', '/agents/execution-policies', '/agents/budgets', '/agents/retention'] },
  { id: 'evaluation', title: '评测与运营', icon: 'chart', paths: ['/evaluation', '/feedback', '/operations', '/simulation-model-configurations', '/agents/simulation-preparation', '/agents/simulation-rules', '/agents/context-bindings', '/agents/publication'] },
  { id: 'enterprise', title: '企业与协作', icon: 'peoples', paths: ['/enterprises/members', '/enterprises/roles', '/enterprises/registrations', '/enterprises/registration-reviews', '/account'] },
  { id: 'system', title: '系统管理', icon: 'tree', prefix: '/system/' },
  { id: 'monitor', title: '运行监控', icon: 'monitor', prefix: '/monitor/' },
  { id: 'tools', title: '开发工具', icon: 'tool', prefix: '/tool/' }
]

function visibleNavigationRoutes(records, parent = '', parentIcon = 'component') {
  return records.filter(record => !record.hidden).flatMap(record => {
    const path = record.path.startsWith('/') ? record.path : parent + '/' + record.path
    const icon = record.meta?.icon || parentIcon
    const children = record.children?.filter(child => !child.hidden) ?? []
    if (children.length) return visibleNavigationRoutes(children, path === '/' ? '' : path, icon)
    if (!record.meta?.title) return []
    return [{ ...record, path: path.replace(/\/$/, '') || '/index', children: undefined,
      meta: { ...record.meta, icon } }]
  })
}

export function medicalNavigationGroups(routes, frameworkRoutes = []) {
  const visible = [...new Map(visibleNavigationRoutes([...routes, ...frameworkRoutes]).map(route => [route.path, route])).values()]
  const groups = businessGroups.map(group => ({ ...group, routes: [] }))
  for (const route of visible) {
    const group = groups.find(group => group.paths?.includes(route.path) || (group.prefix && route.path.startsWith(group.prefix)))
    if (!group) throw new Error(`导航入口缺少业务分类：${route.path}`)
    group.routes.push(route)
  }
  for (const group of groups) {
    if (group.paths) group.routes.sort((left, right) => group.paths.indexOf(left.path) - group.paths.indexOf(right.path))
  }
  return groups.filter(group => group.routes.length)
}

export function medicalNavigationEntries(routes, frameworkRoutes = []) {
  return medicalNavigationGroups(routes, frameworkRoutes).map(group => ({ title: group.title,
    entries: group.routes.map(route => ({ path: route.path, title: route.meta.title, icon: route.meta.icon })) }))
}
export const librarySections = [
  { path: '/library', label: '资料库' },
  { path: '/library/collections', label: '资料集' },
  { path: '/library/processing', label: '处理队列' },
  { path: '/library/search', label: '检索验证' },
  { path: '/library/candidates', label: '网络候选' },
  { path: '/library/artifacts', label: '任务成果' }
]
export function activeLibrarySection(path) {
  return librarySections.slice(1).find(item => path === item.path || path.startsWith(item.path + '/'))?.path ?? '/library'
}
export function libraryLocation(path, query) {
  return { path, query: typeof query.project_id === 'string' ? { project_id: query.project_id } : {} }
}
