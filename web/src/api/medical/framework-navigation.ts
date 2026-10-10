import request from '@/utils/request'
import { envelope, fail, validate } from './protocol'
import type { MedicalSession } from './protocol'

export interface FrameworkNavigation {
  schema_version: '1.0.0'; framework_revision: 'ruoyi-3.9.2'; user_id: string; framework_user_id: string
  roles: string[]; permissions: string[]; routers: Record<string, unknown>[]
}

export async function readFrameworkNavigation(session: MedicalSession, signal: AbortSignal): Promise<FrameworkNavigation> {
  if (session.user.username === null || session.user.platform_role === null) fail('ACCESS_DENIED', '当前身份没有关联本地系统账户')
  const response = envelope(await request.get('/api/framework/navigation', { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0' } }))
  const value = validate<FrameworkNavigation>('urn:medical:framework-navigation:1.0.0', response.data)
  if (value.user_id !== session.user.user_id || response.resource_revision !== null
      || BigInt(value.framework_user_id) > 9223372036854775807n) {
    fail('CONTENT_MISMATCH', '原系统菜单与当前账户身份不一致')
  }
  return value
}
