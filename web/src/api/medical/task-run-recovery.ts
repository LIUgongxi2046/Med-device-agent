import type { RunView } from './runs'

export type RecoveryChoice = 'inspect' | 'continue' | 'retry'
export interface RecoveryForm { choice: RecoveryChoice; reason: string; versionsChecked: boolean; effectsChecked: boolean }
export const emptyRecoveryForm = (): RecoveryForm => ({ choice: 'inspect', reason: '', versionsChecked: false, effectsChecked: false })
const labels: Record<string, string> = { QUEUED: '已经排队', RUNNING: '正在执行', WAITING_INPUT: '等待补充或人工处理', SUCCEEDED: '已经完成', FAILED: '执行失败', CANCEL_REQUESTED: '正在停止，结果待核查', CANCELLED: '运行已停止', SUPERSEDED: '已被新尝试替代' }
export function recoveryView(snapshot: RunView) {
  const known = Object.hasOwn(labels, snapshot.status)
  const interrupted = snapshot.status === 'CANCELLED' || snapshot.steps.some(step => step.status === 'INTERRUPTED')
  const relevant = known && ['FAILED', 'WAITING_INPUT', 'CANCELLED'].includes(snapshot.status)
  return {
    status: known ? labels[snapshot.status]! : '未知运行状态', rawStatus: String(snapshot.status), known, relevant,
    summary: !known ? '当前状态不受支持。请重新读取服务端状态，不推断运行已失败或可以恢复。'
      : snapshot.status === 'WAITING_INPUT' ? '先查看等待事项与缺失输入；补充资料不等于继续执行。'
      : interrupted ? '已停止或存在中断步骤。停止不代表可以从断点继续，需服务端核查检查点及在途调用。'
      : snapshot.status === 'FAILED' ? '保留原运行及步骤版本，先核查原因与已完成产物，再决定后续处理。'
      : '当前状态不提供继续或重试。读取状态不会创建新的运行。',
    failures: snapshot.steps.filter(step => ['FAILED', 'INTERRUPTED', 'CANCELLED'].includes(step.status)),
    blockers: [
      ...(!known ? ['未知状态不能用于恢复决定。'] : []),
      ...(!relevant ? ['当前运行状态不满足恢复处理条件。'] : []),
      ...(snapshot.waiting_items.length ? ['存在等待事项；需按原事项要求核查处理结果。'] : []),
      ...(snapshot.inflight_invocations.length ? ['存在活动或未结算调用；必须先核查结果与费用，避免重复业务结果。'] : []),
      '当前 RunView 未提供可恢复检查点、完整版本快照及幂等执行资格；不能确认可继续。',
      '继续执行与失败重试接口尚未接入，不能提交或创建新尝试。'
    ],
    canSubmit: false as const
  }
}
export function recoveryFormIssues(form: RecoveryForm, snapshot: RunView, canWrite: boolean): string[] {
  const view = recoveryView(snapshot)
  return [
    ...(!canWrite ? ['当前仅有读取权限，不能申请执行。'] : []),
    ...(form.choice === 'inspect' ? ['请选择继续原运行或重新尝试；选择本身不会执行。'] : []),
    ...(!form.reason.trim() ? ['请填写处理理由。'] : []),
    ...(!form.versionsChecked ? ['请核对原运行、步骤与固定版本。'] : []),
    ...(!form.effectsChecked ? ['请核查已完成产物及在途调用，避免重复结果。'] : []),
    ...view.blockers
  ]
}
