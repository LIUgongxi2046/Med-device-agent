import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import useUserStore from '@/store/modules/user'
import { tansParams } from '@/utils/ruoyi'
import { envelope, fail } from './protocol'

const frameworkPrefixes = ['/system/', '/monitor/', '/tool/', '/common/']

export function prepareFrameworkRequest(config: InternalAxiosRequestConfig): void {
  const user = useUserStore()
  const session = user.medicalSession
  if (!user.medicalMode || session === null || user.frameworkNavigation === null || document.hidden) {
    fail('AUTHENTICATION_REQUIRED', '原管理操作需要重新核查当前账户')
  }
  const original = config.url
  if (typeof original !== 'string' || !frameworkPrefixes.some(prefix => original.startsWith(prefix))
      || config.auth !== undefined) fail('ACCESS_DENIED', '原管理请求地址未获准')
  const target = new URL(original, location.origin)
  if (target.origin !== location.origin || target.hash !== '' || !frameworkPrefixes.some(prefix => target.pathname.startsWith(prefix))
      || user.frameworkNavigation.user_id !== session.user.user_id) {
    fail('ACCESS_DENIED', '原管理请求与当前账户不一致')
  }
  config.url = '/api' + original
  if (config.method === 'get' && config.params) {
    const parameters = tansParams(config.params)
    config.url += (target.search === '' ? '?' : '&') + parameters.slice(0, -1)
    config.params = {}
  }
  config.medical = 'framework'
  config.medicalFrameworkIdentity = { actor: session.user.user_id, generation: user.medicalGeneration }
  config.headers.set('X-Schema-Version', '1.0.0', true)
  config.headers.set('Cache-Control', 'no-store', true)
  // 原请求实例负责传输；仅将当前Cookie和CSRF接入原管理方法。
  if (!['get', 'head', 'options'].includes(config.method ?? 'get')) {
    config.headers.set(session.csrf.header_name, session.csrf.token, true)
  }
  config.headers.delete(['isToken', 'repeatSubmit', 'interval'])
  if (config.responseType !== 'blob' && config.responseType !== 'arraybuffer') {
    config.responseType = 'json'
    config.transitional = { silentJSONParsing: false, forcedJSONParsing: true }
  }
}

export async function verifyFrameworkResponse(response: AxiosResponse<unknown>): Promise<void> {
  if (response.status < 200 || response.status >= 300) {
    const value = response.data instanceof Blob ? JSON.parse(await response.data.text())
      : response.data instanceof ArrayBuffer ? JSON.parse(new TextDecoder().decode(response.data)) : response.data
    envelope({ ...response, data: value })
  }
  const identity = response.config.medicalFrameworkIdentity
  const user = useUserStore()
  if (identity === undefined || user.medicalSession === null || document.hidden
      || identity.actor !== user.medicalSession.user.user_id || identity.generation !== user.medicalGeneration) {
    fail('CONTEXT_CHANGED', '原管理响应对应的会话已经变化，请重新读取')
  }
  if (response.headers['cache-control'] !== 'no-store') fail('SCHEMA_INVALID', '原管理响应未禁止缓存')
  if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') return
  const value = response.data
  if (value === null || typeof value !== 'object' || !('code' in value) || typeof value.code !== 'number'
      || !Number.isInteger(value.code)) fail('SCHEMA_INVALID', '原管理响应缺少有效状态')
  if (value.code === 401) fail('AUTHENTICATION_REQUIRED', '原管理会话已经失效，请重新登录')
}
