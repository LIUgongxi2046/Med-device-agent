import router from './router'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { isHttp, isPathMatch } from '@/utils/validate'
import { isRelogin } from '@/utils/request'
import useUserStore from '@/store/modules/user'
import useLockStore from '@/store/modules/lock'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/register']

const isWhiteList = (path) => {
  return whiteList.some(pattern => isPathMatch(pattern, path))
}

const medicalFrameworkPath = (path) => ['/system', '/monitor', '/tool'].some(prefix => path === prefix || path.startsWith(prefix + '/'))

router.beforeEach(async (to, from) => {
  NProgress.start()
  // On a direct management link, install only routes returned for the current medical identity.
  // A path prefix alone never grants access; the refreshed named route must exist in that exact registry.
  if (import.meta.env.VITE_APP_ENV === 'medical' && medicalFrameworkPath(to.path) && !to.meta.medicalFramework) {
    await useUserStore().readMedical()
    const current = router.resolve(to.fullPath)
    if (!current.meta.medicalFramework || !usePermissionStore().isMedicalFrameworkRoute(current.name)) {
      NProgress.done()
      return { path: '/access/denied' }
    }
    return { path: to.fullPath, replace: true }
  }
  if (to.meta.medical) {
    useUserStore().medicalMode = true
    to.meta.title && useSettingsStore().setTitle(to.meta.title)
    if (to.meta.public) { NProgress.done(); return true }
    await useUserStore().readMedical()
    if (to.meta.medicalFramework && !usePermissionStore().isMedicalFrameworkRoute(to.name)) {
      NProgress.done()
      return { path: '/access/denied' }
    }
    useUserStore().medicalPendingNavigation = null
    return true
  }
  if (useUserStore().medicalMode) {
    NProgress.done()
    return { path: '/access' }
  }
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title)
    const isLock = useLockStore().isLock
    if (to.path === '/login') {
      NProgress.done()
      return { path: '/' }
    }
    if (isWhiteList(to.path)) {
      return true
    }
    if (isLock && to.path !== '/lock') {
      NProgress.done()
      return { path: '/lock' }
    }
    if (!isLock && to.path === '/lock') {
      NProgress.done()
      return { path: '/' }
    }
    if (useUserStore().roles.length === 0) {
      isRelogin.show = true
      try {
        // 拉取user_info信息
        await useUserStore().getInfo()
        isRelogin.show = false
        // 根据roles权限生成可访问的路由
        const accessRoutes = await usePermissionStore().generateRoutes()
        accessRoutes.forEach(route => {
          if (!isHttp(route.path)) {
            router.addRoute(route)
          }
        })
        // 重新导航到目标路由，确保动态路由已注册
        return { ...to, replace: true }
      } catch (err) {
        await useUserStore().logOut()
        ElMessage.error(err)
        return { path: '/' }
      }
    }
    return true
  } else {
    // 没有token
    if (isWhiteList(to.path)) {
      // 在免登录白名单，直接进入
      return true
    }
    NProgress.done()
    return `/login?redirect=${to.fullPath}` // 否则全部重定向到登录页
  }
})

router.afterEach(() => {
  NProgress.done()
})
