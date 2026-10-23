import { createApp } from 'vue'

import Cookies from 'js-cookie'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import locale from 'element-plus/es/locale/lang/zh-cn'

import '@/assets/styles/index.scss' // global css

import App from './App'
import store from './store'
import router from './router'
import directive from './directive' // directive

// 注册指令
import plugins from './plugins' // plugins
import { download } from '@/utils/request'

// svg图标
import SvgIcon from '@/components/SvgIcon'
import elementIcons from '@/components/SvgIcon/svgicon'

import './permission' // permission control

import { useDict } from '@/utils/dict'
import { getConfigKey } from "@/api/system/config"
import { parseTime, resetForm, addDateRange, handleTree, selectDictLabel, selectDictLabels } from '@/utils/ruoyi'

// 分页组件
import Pagination from '@/components/Pagination'
// 自定义表格工具组件
import RightToolbar from '@/components/RightToolbar'
// 富文本组件
import Editor from "@/components/Editor"
// 文件上传组件
import FileUpload from "@/components/FileUpload"
// 图片上传组件
import ImageUpload from "@/components/ImageUpload"
// 图片预览组件
import ImagePreview from "@/components/ImagePreview"
import useUserStore from '@/store/modules/user'
import { MedicalFailure } from '@/api/medical/protocol'
import { isCancel } from 'axios'
import '@/assets/styles/medical.scss'
// 字典标签组件
import DictTag from '@/components/DictTag'

const app = createApp(App)

// 全局方法挂载
app.config.globalProperties.useDict = useDict
app.config.globalProperties.download = download
app.config.globalProperties.parseTime = parseTime
app.config.globalProperties.resetForm = resetForm
app.config.globalProperties.handleTree = handleTree
app.config.globalProperties.addDateRange = addDateRange
app.config.globalProperties.getConfigKey = getConfigKey
app.config.globalProperties.selectDictLabel = selectDictLabel
app.config.globalProperties.selectDictLabels = selectDictLabels

// 全局组件挂载
app.component('DictTag', DictTag)
app.component('Pagination', Pagination)
app.component('FileUpload', FileUpload)
app.component('ImageUpload', ImageUpload)
app.component('ImagePreview', ImagePreview)
app.component('RightToolbar', RightToolbar)
app.component('Editor', Editor)

app.use(router)
app.use(store)
const medicalChannel = new BroadcastChannel('medical-enterprise-context')
window.addEventListener('unhandledrejection', event => {
  if (isCancel(event.reason)) event.preventDefault()
})
window.addEventListener('medical-failure', event => {
  const user = useUserStore()
  if (event.detail.code !== 'DEPENDENCY_UNAVAILABLE') user.invalidateMedical()
  user.medicalError = event.detail
  if (event.detail.code === 'AUTHENTICATION_REQUIRED') void router.replace('/access')
})
app.config.errorHandler = error => {
  if (isCancel(error)) return
  const user = useUserStore()
  if (!user.medicalMode) throw error
  if (!(error instanceof MedicalFailure) || error.code !== 'DEPENDENCY_UNAVAILABLE') user.invalidateMedical()
  user.medicalError = error instanceof MedicalFailure ? error : new MedicalFailure('INTERNAL_ERROR', '业务操作未完成，请重新读取会话')
}
router.onError((error, target) => {
  // 已被新导航取消的读取继续终止，当前导航保留自己的会话核查。
  if (isCancel(error)) return
  const user = useUserStore()
  if (!user.medicalMode) throw error
  user.invalidateMedical()
  user.medicalError = error instanceof MedicalFailure ? error : new MedicalFailure('INTERNAL_ERROR', '页面读取未完成，请重新核查业务会话')
  if (error instanceof MedicalFailure && error.code === 'AUTHENTICATION_REQUIRED') { void router.replace('/access'); return }
  if (target.meta.medical && !target.meta.public) user.medicalPendingNavigation = target.fullPath
  void router.replace('/session-recovery')
})
window.addEventListener('medical-context-invalidated', () => medicalChannel.postMessage('CONTEXT_INVALIDATED'))
medicalChannel.addEventListener('message', event => {
  if (event.data !== 'CONTEXT_INVALIDATED') throw new Error('企业上下文通知无效')
  const user = useUserStore()
  if (!user.medicalMode) return
  user.invalidateMedical()
  if (!document.hidden && !user.medicalWriting && !router.currentRoute.value.meta.public) void user.readMedical()
})
document.addEventListener('visibilitychange', () => {
  const user = useUserStore()
  if (!user.medicalMode || router.currentRoute.value.meta.public) return
  if (document.hidden) user.invalidateMedical()
  else if (!user.medicalWriting) void user.readMedical()
})
window.addEventListener('pagehide', () => {
  if (useUserStore().medicalMode) useUserStore().invalidateMedical()
})
window.addEventListener('pageshow', event => { if (event.persisted && useUserStore().medicalMode) location.reload() })
app.use(plugins)
app.use(elementIcons)
app.component('svg-icon', SvgIcon)

directive(app)

// 使用element-plus 并且设置全局的大小
app.use(ElementPlus, {
  locale: locale,
  // 支持 large、default、small
  size: Cookies.get('size') || 'default'
})

app.mount('#app')
