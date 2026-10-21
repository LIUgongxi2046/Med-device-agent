/** Labels only: never infer professional node identity, result IDs, or action eligibility. */
const labels = {
  step: { PENDING: '等待前置条件', READY: '准备就绪', RUNNING: '正在执行', WAITING_INPUT: '等待处理', SUCCEEDED: '已完成', FAILED: '执行失败', CANCEL_REQUESTED: '停止请求处理中', CANCELLED: '已取消', SKIPPED: '已跳过' },
  waiting: { WAITING_INPUT: '等待处理', RESOLVED: '已处理', CANCELLED: '已取消', SUPERSEDED: '已被替代' },
  invocation: { RESERVED: '已预留', DISPATCHING: '正在派发', IN_FLIGHT: '执行中', SUCCEEDED: '调用成功', FAILED: '调用失败', CANCEL_REQUESTED: '停止请求处理中', CANCELLED: '已取消', UNKNOWN: '状态待核查' },
  settlement: { PENDING: '等待结算', SETTLED: '已结算', DISPUTED: '结算存在争议' }
} as const
export function runRecordStatus(kind: keyof typeof labels, status: string): string {
  const group: Readonly<Record<string, string>> = labels[kind]
  return Object.prototype.hasOwnProperty.call(group, status) ? `${group[status]}（${status}）` : `未知状态（${status}）`
}
