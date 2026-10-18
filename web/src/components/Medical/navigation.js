// Presentation only: never synthesize system routes or grant route permissions.
export function medicalNavigationGroups(routes, frameworkRoutes = []) {
  const visible = routes.filter(route => !route.hidden)
  const core = new Set(['/index', '/', '', '/projects', '/library', '/scenarios'])
  const system = new Set(['/enterprises/registrations', '/enterprises/registration-reviews', '/enterprises/members', '/enterprises/roles'])
  return [
    { title: '核心功能', routes: visible.filter(route => core.has(route.path)) },
    { title: '平台管理', routes: visible.filter(route => !core.has(route.path) && !system.has(route.path)) },
    { title: '系统管理', routes: [...visible.filter(route => system.has(route.path)), ...frameworkRoutes.filter(route => !route.hidden)] }
  ].filter(group => group.routes.length)
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
