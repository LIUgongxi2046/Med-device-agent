import { prepareMedicalFrameworkRoutes, createMedicalFrameworkRegistry, frameworkRouteNames, isMedicalFrameworkPath } from '@/components/Medical/framework-routes'
export { isMedicalFrameworkPath } from '@/components/Medical/framework-routes'
import auth from '@/plugins/auth'
import router, { constantRoutes, dynamicRoutes, medicalRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import Layout from '@/layout/index'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'

// 匹配views里面所有的.vue文件
const modules = import.meta.glob('./../../views/**/*.vue')
const medicalFrameworkRegistry = createMedicalFrameworkRegistry({ addRoute: route => router.addRoute(route) })
export const isMedicalFrameworkRoute = name => medicalFrameworkRegistry.has(name)

const usePermissionStore = defineStore(
  'permission',
  {
    state: () => ({
      routes: [],
      addRoutes: [],
      defaultRoutes: [],
      topbarRouters: [],
      sidebarRouters: [],
      frameworkRoutes: [],
      medicalNavigationIdentity: null
    }),
    actions: {
      suspendMedicalRoutes() { medicalFrameworkRegistry.suspend() },
      setMedicalRoutes(authenticated, frameworkRouters = [], identity = null) {
        // 只有当前服务端身份与完整授权均一致时，继续使用已登记的导航组件。
        if (authenticated && identity !== null && identity === this.medicalNavigationIdentity) {
          medicalFrameworkRegistry.resume()
          return
        }
        medicalFrameworkRegistry.clear()
        this.medicalNavigationIdentity = authenticated ? identity : null
        const workbench = constantRoutes.find(route => route.children?.some(child => child.name === 'Index'))
        const navigationRoots = new Set(['/enterprises/members', '/enterprises/roles', '/sources/uploads', '/account'])
        const agentEntries = new Set(['MedicalExecutionPolicies', 'MedicalRuntimeRetention', 'MedicalModelRuntime',
          'MedicalFormalEvaluationContextBindings', 'MedicalFormalPublication', 'MedicalFormalAgentDrafts', 'MedicalFormalCapabilities'])
        const routes = authenticated ? [workbench, ...medicalRoutes.filter(route => !route.hidden || navigationRoots.has(route.path))
          .map(route => ({ ...route, hidden: false, ...(route.path === '/agents' ? { children: route.children.map(child => ({ ...child,
            ...(agentEntries.has(child.name) ? { hidden: false } : {}),
            ...(child.name === 'MedicalSimulationRules' ? { path: 'simulation-rules' } : {}) })) } : {}) }))].filter(Boolean) : []
        const approved = authenticated ? prepareMedicalFrameworkRoutes(frameworkRouters, router.getRoutes(), view => /^(system|monitor|tool)\//.test(view) && Boolean(loadView(view))) : []
        this.frameworkRoutes = filterAsyncRouter(approved)
        const existingNames = new Set([...router.getRoutes().map(route => route.name), ...frameworkRouteNames(this.frameworkRoutes)])
        const existingPaths = new Set([...router.getRoutes().map(route => route.path), ...this.frameworkRoutes.map(route => route.path)])
        // These are the existing RuoYi drilldowns, gated by the current server
        // permissions/roles already copied into the user store by readMedical.
        const drilldowns = authenticated ? filterDynamicRoutes(dynamicRoutes).filter(route =>
          isMedicalFrameworkPath(route.path) && !existingPaths.has(route.path)
          && frameworkRouteNames([route]).filter(Boolean).every(name => !existingNames.has(name))
        ).map(markMedicalFrameworkRoute) : []
        medicalFrameworkRegistry.replace([...this.frameworkRoutes, ...drilldowns])
        this.routes = routes.concat(this.frameworkRoutes); this.addRoutes = []
        this.sidebarRouters = this.routes; this.topbarRouters = this.routes; this.defaultRoutes = this.routes
      },
      isMedicalFrameworkRoute(name) { return medicalFrameworkRegistry.has(name) },
      setRoutes(routes) {
        this.addRoutes = routes
        this.routes = constantRoutes.concat(routes)
      },
      setDefaultRoutes(routes) {
        this.defaultRoutes = constantRoutes.concat(routes)
      },
      setTopbarRoutes(routes) {
        this.topbarRouters = routes
      },
      setSidebarRouters(routes) {
        this.sidebarRouters = routes
      },
      generateRoutes(roles) {
        return new Promise(resolve => {
          // 向后端请求路由数据
          getRouters().then(res => {
            const sdata = JSON.parse(JSON.stringify(res.data))
            const rdata = JSON.parse(JSON.stringify(res.data))
            const defaultData = JSON.parse(JSON.stringify(res.data))
            const sidebarRoutes = filterAsyncRouter(sdata)
            const rewriteRoutes = filterAsyncRouter(rdata, false, true)
            const defaultRoutes = filterAsyncRouter(defaultData)
            const asyncRoutes = filterDynamicRoutes(dynamicRoutes)
            asyncRoutes.forEach(route => { router.addRoute(route) })
            this.setRoutes(rewriteRoutes)
            this.setSidebarRouters(constantRoutes.concat(sidebarRoutes))
            this.setDefaultRoutes(sidebarRoutes)
            this.setTopbarRoutes(defaultRoutes)
            resolve(rewriteRoutes)
          })
        })
      }
    }
  })

function markMedicalFrameworkRoute(route) {
  return { ...route, meta: { ...route.meta, medical: true, medicalFramework: true, public: false, noCache: true },
    ...(route.children ? { children: route.children.map(markMedicalFrameworkRoute) } : {}) }
}

// 遍历后台传来的路由字符串，转换为组件对象
function filterAsyncRouter(asyncRouterMap, lastRouter = false, type = false) {
  return asyncRouterMap.filter(route => {
    if (type && route.children) {
      route.children = filterChildren(route.children)
    }
    if (route.component) {
      // Layout ParentView 组件特殊处理
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else if (route.component === 'InnerLink') {
        route.component = InnerLink
      } else {
        route.component = loadView(route.component)
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, route, type)
    } else {
      delete route['children']
      delete route['redirect']
    }
    return true
  })
}

function filterChildren(childrenMap, lastRouter = false) {
  var children = []
  childrenMap.forEach(el => {
    el.path = lastRouter ? lastRouter.path + '/' + el.path : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

// 动态路由遍历，验证是否具备权限
export function filterDynamicRoutes(routes) {
  const res = []
  routes.forEach(route => {
    if (route.permissions) {
      if (auth.hasPermiOr(route.permissions)) {
        res.push(route)
      }
    } else if (route.roles) {
      if (auth.hasRoleOr(route.roles)) {
        res.push(route)
      }
    }
  })
  return res
}

export const loadView = (view) => {
  let res
  for (const path in modules) {
    const dir = path.split('views/')[1].split('.vue')[0]
    if (dir === view) {
      res = () => modules[path]()
    }
  }
  return res
}

export default usePermissionStore
