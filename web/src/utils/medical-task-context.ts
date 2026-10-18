import type { TaskItem } from '@/api/medical/tasks'

const id = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
export interface WaitingTaskContext { waitingItemId: string | null; runId: string | null; requested: boolean; invalid: boolean }
export function readWaitingTaskContext(query: Record<string, unknown>): WaitingTaskContext {
  const waiting = query.waiting_item_id, run = query.run_id
  const requested = waiting !== undefined || run !== undefined
  const valid = (value: unknown) => typeof value === 'string' && id.test(value)
  const invalid = (waiting !== undefined && !valid(waiting)) || (run !== undefined && !valid(run))
  return { waitingItemId: !invalid && valid(waiting) ? waiting as string : null,
    runId: !invalid && valid(run) ? run as string : null, requested, invalid }
}
export function waitingTaskMatch(item: TaskItem, context: WaitingTaskContext): 'WAITING_ITEM' | 'ACTIVE_RUN' | null {
  if (context.invalid) return null
  if (context.waitingItemId && item.detail.waiting_item_ids.includes(context.waitingItemId)) return 'WAITING_ITEM'
  if (context.runId && item.detail.active_run_ids.includes(context.runId)) return 'ACTIVE_RUN'
  return null
}
export function waitingTaskLocation(projectId: string, waitingItemId: string, runId: string) {
  return { path: '/projects/' + projectId + '/tasks', query: { waiting_item_id: waitingItemId, run_id: runId } }
}
