/** Presentation only. These categories are not configured connections or provider claims. */
export type ConnectionAccess = 'unknown' | 'allowed' | 'denied'
export type ConnectionViewState = 'blocked' | 'empty' | 'loading' | 'error' | 'disconnected' | 'verifying' | 'sync-error'
export const connectionCategories = [
  { id: 'identity', name: '账号与单点登录', description: '核对身份接入、账号映射与企业岗位边界。', icon: 'ID' },
  { id: 'documents', name: '资料系统连接', description: '核对客户 B/S 系统、资料归属与批准的同步范围。', icon: 'DOC' },
  { id: 'services', name: '专家服务协作', description: '核对服务责任、专家授权与交付边界。', icon: 'TEAM' }
] as const
export type ConnectionCategory = typeof connectionCategories[number]['id']
export interface ConnectionDraft { name: string; system: string; endpoint: string; specification: string; mapping: string; ownership: string; scope: string; reason: string }
export type DraftErrors = Partial<Record<keyof ConnectionDraft, string>>
export const blankConnectionDraft = (): ConnectionDraft => ({ name: '', system: '', endpoint: '', specification: '', mapping: '', ownership: '', scope: '', reason: '' })
export function validateConnectionDraft(draft: ConnectionDraft): DraftErrors {
  const errors: DraftErrors = {}
  const labels: Record<keyof ConnectionDraft, string> = { name: '连接名称', system: '目标系统', endpoint: '接口地址', specification: '接口规格版本', mapping: '账号映射规则', ownership: '文档归属规则', scope: '授权与同步范围', reason: '配置理由' }
  for (const key of Object.keys(labels) as (keyof ConnectionDraft)[]) {
    if (!draft[key].trim()) errors[key] = `请填写${labels[key]}`
    else if (draft[key].length > (key === 'endpoint' ? 2048 : 500)) errors[key] = `${labels[key]}超出长度限制`
  }
  if (draft.endpoint.trim()) {
    try {
      const url = new URL(draft.endpoint.trim())
      if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash) errors.endpoint = '请填写不含账号、密码、查询参数或片段的 HTTPS 地址'
    } catch { errors.endpoint = '请输入完整的 HTTPS 地址' }
  }
  return errors
}
export const connectionStateCopy: Record<ConnectionViewState, { title: string; detail: string }> = {
  blocked: { title: '接入条件待确认 · BLOCKED', detail: '客户接口规格、账号映射、文档归属及批准范围尚未核实。不能验证、同步或宣称连接可用。' },
  empty: { title: '暂无可展示的连接记录', detail: '当前界面未接入连接目录；没有记录不代表企业实际不存在连接。可先了解接入要求。' },
  loading: { title: '正在读取连接信息', detail: '等待权威连接目录与当前权限，暂不展示旧配置。' },
  error: { title: '连接信息读取失败', detail: '无法确认当前连接状态。请在服务接入后重新读取，不使用旧数据替代当前结果。' },
  disconnected: { title: '连接状态无法确认', detail: '当前处于离线或连接中断状态。不会将网络中断视为远端同步失败，也不会修改已确认资料。' },
  verifying: { title: '等待验证回执', detail: '尚无真实验证回执；不显示验证成功，不开放同步操作。' },
  'sync-error': { title: '同步错误待核查', detail: '错误来源、影响对象与重试条件需由服务端记录确认。未核查前禁止重复同步；已确认资料不被修改。' }
}
