import { readN01Json } from '@/api/medical/n01-json'
import { readExecutionPolicyJson } from '@/api/medical/execution-policy-json'
import { readRetentionJson } from '@/api/medical/retention-json'
import axios from 'axios'
import { ElNotification , ElMessageBox, ElMessage, ElLoading } from 'element-plus'
import { getToken } from '@/utils/auth'
import errorCode from '@/utils/errorCode'
import { tansParams, blobValidate } from '@/utils/ruoyi'
import cache from '@/plugins/cache'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import { fail, MedicalFailure } from '@/api/medical/protocol'
import { readProjectJson, readBoundedJson, writeProjectJson } from '@/api/medical/project-json'
import { readGruExportJson } from '@/api/medical/gru-export-json'
import { readComparisonJson } from '@/api/medical/document-comparison-json'
import { readCapabilityJson } from '@/api/medical/formal-capability-json'
import { prepareFrameworkRequest, verifyFrameworkResponse } from '@/api/medical/framework-request'

let downloadLoadingInstance
// 是否显示重新登录
export let isRelogin = { show: false }

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // 超时
  timeout: 10000
})

// request拦截器
service.interceptors.request.use(config => {
  if (!config.medical && import.meta.env.VITE_APP_ENV === 'medical') prepareFrameworkRequest(config)
  if (config.medical) {
    const target = new URL(config.url, location.origin)
    if (target.origin !== location.origin || !['/api/', '/auth/'].some(prefix => target.pathname.startsWith(prefix))) {
      fail('ACCESS_DENIED', '医疗请求地址未获准')
    }
    config.baseURL = ''
    config.headers.delete('Authorization')
    config.withCredentials = true
    config.validateStatus = () => true
    if (config.medical === 'envelope') {
      if (/^\/api\/gru-record-exports\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/content$/.test(target.pathname)) {
        config.responseType = 'arraybuffer'
        config.transformResponse = []
      } else if (/^\/api\/gru-conversations\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/(export-basis|exports|export-operations)$/.test(target.pathname)
          || /^\/api\/gru-record-exports\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || target.pathname === '/api/gru-record-export-feature-availability') {
        config.responseType = 'text'
        config.transformResponse = [readGruExportJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (target.pathname === '/api/document-comparisons'
          || /^\/api\/document-comparisons\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || target.pathname === '/api/document-comparison-operations'
          || target.pathname === '/api/document-comparison-feature-availability') {
        config.responseType = 'text'
        config.transformResponse = [readComparisonJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/runtime-retention-availability', '/api/runtime-retention-policies', '/api/runtime-retention-retirements', '/api/runtime-retention-policy-operations', '/api/runtime-retention-retirement-operations', '/api/runtime-retention-cancellations', '/api/runtime-retention-cancellation-operations'].includes(target.pathname)
          || /^\/api\/runtime-retention-policies\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readRetentionJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/agent-execution-policy-feature-availability', '/api/agent-execution-policies', '/api/agent-execution-policies/query', '/api/agent-execution-policy-decisions'].includes(target.pathname)
          || /^\/api\/agent-execution-policies\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\/records\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/operation-receipts\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?$/.test(target.pathname)
          || /^\/api\/agent-execution-policy-records\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readExecutionPolicyJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/formal-evaluation-node-input-availability', '/api/formal-evaluation-node-inputs', '/api/formal-evaluation-node-inputs/status', '/api/formal-evaluation-node-inputs/content', '/api/formal-evaluation-node-inputs/recovery', '/api/formal-evaluation-plan-feature-availability', '/api/formal-evaluation-plan-preparations', '/api/formal-evaluation-plan-preparations/executions', '/api/formal-evaluation-plan-preparations/executions/recovery', '/api/formal-evaluation-plan-preparations/preview', '/api/formal-evaluation-plan-preparations/status', '/api/formal-evaluation-plan-preparations/recovery', '/api/formal-evaluation-plan-preparations/content'].includes(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readN01Json]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/formal-evaluation-context-feature-availability', '/api/formal-evaluation-context-bindings', '/api/formal-evaluation-context-decisions', '/api/formal-evaluation-context-bindings/query', '/api/formal-evaluation-context-bindings/recovery'].includes(target.pathname)
          || /^\/api\/formal-evaluation-context-bindings\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readCapabilityJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/formal-agent-publication-feature-availability', '/api/formal-agent-publications', '/api/formal-agent-publications/disable', '/api/formal-agent-publications/disable/recovery', '/api/formal-agent-acceptance-decisions', '/api/formal-evaluation-score-bindings', '/api/formal-evaluation-score-bindings/recovery'].includes(target.pathname)
          || /^\/api\/formal-evaluation-policies\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || /^\/api\/expert-qualifications\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || /^\/api\/formal-agent-publication-operations\/(?:[A-Za-z0-9._:-]|%3[Aa]){16,128}$/.test(target.pathname)
          || /^\/api\/formal-agent-acceptance-records\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\/operation-receipts\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readCapabilityJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (target.pathname === '/api/source-versions'
          || /^\/api\/source-versions\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readBoundedJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (/^\/api\/model-verification-tokenizer-candidates\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || target.pathname === '/api/model-enablement-policy-preparations'
          || /^\/api\/model-enablement-policy-preparations\/(?:operations\/)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || /^\/api\/model-enablement-policy-preparation-candidates\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || /^\/api\/formal-evaluation-input-source-candidates\/[A-Z][A-Z0-9_]{0,63}\/(?:0|[1-9][0-9]{0,5})$/.test(target.pathname)
          || target.pathname === '/api/formal-evaluation-input-feature-availability'
          || target.pathname === '/api/formal-evaluation-inputs'
          || /^\/api\/formal-evaluation-inputs\/(?:operations\/)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || target.pathname === '/api/model-verification-inputs'
          || /^\/api\/model-verification-inputs\/(?:operations\/)?[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || /^\/api\/model-verification-input-candidates\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/(?:DOCUMENT_BODY|MODEL_VERIFICATION)$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readCapabilityJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/formal-invocation-cancellations','/api/formal-invocation-cancellation-operations','/api/model-runtime-availability','/api/model-connection-verifications','/api/model-connection-verification-operations','/api/model-connection-verification-completions','/api/model-eligibilities','/api/model-eligibility-operations'].includes(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readCapabilityJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (['/api/formal-agents', '/api/formal-runtime-capabilities', '/api/formal-agent-drafts/query', '/api/formal-agent-authoring-feature-availability'].includes(target.pathname)
          || /^\/api\/formal-(?:agent|runtime-capability)-records\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\/operation-receipts\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readCapabilityJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (target.pathname === '/api/agent-authoring/capabilities'
          || target.pathname === '/api/agent-authoring/capability-cancellations'
          || target.pathname === '/api/agent-authoring/capability-cancellation-operations'
          || target.pathname === '/api/agent-authoring/records/query'
          || /^\/api\/agent-authoring\/records\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || /^\/api\/agent-authoring\/records\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/operation-receipts\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(target.pathname)
          || target.pathname === '/api/agent-authoring-feature-availability') {
        config.responseType = 'text'
        config.transformResponse = [readCapabilityJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (target.pathname === '/api/uploads' || target.pathname.startsWith('/api/uploads/')) {
        config.responseType = 'text'
        config.transformResponse = [readBoundedJson]
        if (config.method === 'put' && /^\/api\/uploads\/[0-9a-f-]{36}\/parts\/[1-9][0-9]*$/.test(target.pathname)) {
          config.transformRequest = [value => {
            if (!(value instanceof Blob) || value.size === 0) fail('SCHEMA_INVALID', '上传分段需要实际文件内容')
            return value
          }]
          config.headers.setContentType('application/octet-stream', true)
        } else {
          config.transformRequest = [writeProjectJson]
          config.headers.setContentType('application/json;charset=utf-8', true)
        }
      } else if (/^\/api\/projects\/[0-9a-f-]{36}\/tasks$/.test(target.pathname)
          || /^\/api\/tasks\/[0-9a-f-]{36}$/.test(target.pathname)) {
        config.responseType = 'text'
        config.transformResponse = [readBoundedJson]
      } else if (target.pathname === '/api/projects' || target.pathname.startsWith('/api/projects/')
          || target.pathname === '/api/budgets' || target.pathname.startsWith('/api/budgets/')
          || target.pathname === '/api/operation-lookups' || target.pathname.startsWith('/api/operations/')) {
        config.responseType = 'text'
        config.transformResponse = [readProjectJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else if (target.pathname === '/api/documents' || target.pathname.startsWith('/api/documents/')
          || target.pathname.startsWith('/api/annotation-threads/')
          || target.pathname.startsWith('/api/runs/')
          || target.pathname === '/api/artifact-upload-grants' || target.pathname.startsWith('/api/artifact-upload-grants/')
          || target.pathname === '/api/rule-versions' || target.pathname.startsWith('/api/rule-versions/')
          || target.pathname === '/api/model-config-revisions' || target.pathname.startsWith('/api/model-config-revisions/')
          || target.pathname === '/api/simulation-model-config-revisions' || target.pathname.startsWith('/api/simulation-model-config-revisions/')
          || target.pathname.startsWith('/api/multimodal-qa/simulations/')
          || target.pathname === '/api/template-versions' || target.pathname.startsWith('/api/template-versions/')
          || target.pathname === '/api/enterprise-registration-reviews' || target.pathname.startsWith('/api/enterprise-registration-reviews/')
          || target.pathname === '/api/enterprise-registrations' || target.pathname.startsWith('/api/enterprise-registrations/')) {
        config.responseType = 'text'
        config.transformResponse = [readBoundedJson]
        config.transformRequest = [writeProjectJson]
        config.headers.setContentType('application/json;charset=utf-8', true)
      } else {
        config.responseType = 'json'
        config.transitional = { silentJSONParsing: false, forcedJSONParsing: true }
      }
    }
    return config
  }
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  // 间隔时间(ms)，小于此时间视为重复提交
  const interval = (config.headers || {}).interval || 1000
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken() // 让每个请求携带自定义token 请根据实际情况自行修改
  }
  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const requestSize = Object.keys(JSON.stringify(requestObj)).length // 请求数据大小
    const limitSize = 5 * 1024 * 1024 // 限制存放数据5M
    if (requestSize >= limitSize) {
      console.warn(`[${config.url}]: ` + '请求数据大小超出允许的5M限制，无法进行防重复提交验证。')
      return config
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url                // 请求地址
      const s_data = sessionObj.data              // 请求数据
      const s_time = sessionObj.time              // 请求时间
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        const message = '数据正在处理，请勿重复提交'
        console.warn(`[${s_url}]: ` + message)
        return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, error => {
    console.log(error)
    Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(async res => {
    if (res.config.medical === 'framework') await verifyFrameworkResponse(res)
    else if (res.config.medical) return res
    // 未设置状态码则默认成功状态
    const code = res.data.code || 200
    // 获取错误信息
    const msg = errorCode[code] || res.data.msg || errorCode['default']
    // 二进制数据则直接返回
    if (res.request.responseType ===  'blob' || res.request.responseType ===  'arraybuffer') {
      return res.data
    }
    if (code === 401) {
      if (!isRelogin.show) {
        isRelogin.show = true
        ElMessageBox.confirm('登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', { confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning' }).then(() => {
          isRelogin.show = false
          useUserStore().logOut().then(() => {
            location.href = '/index'
          })
      }).catch(() => {
        isRelogin.show = false
      })
    }
      return Promise.reject('无效的会话，或者会话已过期，请重新登录。')
    } else if (code === 500) {
      ElMessage({ message: msg, type: 'error' })
      return Promise.reject(new Error(msg))
    } else if (code === 601) {
      ElMessage({ message: msg, type: 'warning' })
      return Promise.reject(new Error(msg))
    } else if (code !== 200) {
      ElNotification.error({ title: msg })
      return Promise.reject('error')
    } else {
      return  Promise.resolve(res.data)
    }
  },
  error => {
    if (error instanceof MedicalFailure) return Promise.reject(error)
    if (error.config?.medical) {
      if (axios.isCancel(error)) return Promise.reject(error)
      fail('DEPENDENCY_UNAVAILABLE', '连接请求未完成，请恢复连接后重新读取会话')
    }
    console.log('err' + error)
    let { message } = error
    if (message == "Network Error") {
      message = "后端接口连接异常"
    } else if (message.includes("timeout")) {
      message = "系统接口请求超时"
    } else if (message.includes("Request failed with status code")) {
      message = "系统接口" + message.slice(-3) + "异常"
    }
    ElMessage({ message: message, type: 'error', duration: 5 * 1000 })
    return Promise.reject(error)
  }
)

// 通用下载方法
export function download(url, params, filename, config) {
  downloadLoadingInstance = ElLoading.service({ text: "正在下载数据，请稍候", background: "rgba(0, 0, 0, 0.7)", })
  return service.post(url, params, {
    transformRequest: [(params) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  }).then(async (data) => {
    const isBlob = blobValidate(data)
    if (isBlob) {
      const blob = new Blob([data])
      saveAs(blob, filename)
    } else {
      const resText = await data.text()
      const rspObj = JSON.parse(resText)
      const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default']
      ElMessage.error(errMsg)
    }
    downloadLoadingInstance.close()
  }).catch((r) => {
    console.error(r)
    ElMessage.error('下载文件出现错误，请联系管理员！')
    downloadLoadingInstance.close()
  })
}

export default service
