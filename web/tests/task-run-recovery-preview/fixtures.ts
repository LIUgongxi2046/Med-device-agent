import type { RunView } from '../../src/api/medical/runs'
// Isolated simulated data only. This module does not call a server.
export function fixture(status: RunView['status'] = 'FAILED'): RunView {
  return { run_id: '模拟运行-20261020-001', task_id: '模拟任务-临床评价', task_revision: '7', status, reason_code: 'SIMULATED_SOURCE_REQUIRED',
    steps: [{ step_id: '模拟步骤-证据核查', role_version: '模拟角色@3', section_id: '模拟章节-02', status: status === 'CANCELLED' ? 'INTERRUPTED' : 'FAILED', lease_generation: '2', revision: '4' }, { step_id: '模拟步骤-计划', role_version: '模拟角色@3', section_id: null, status: 'SUCCEEDED', lease_generation: '1', revision: '2' }],
    waiting_items: status === 'WAITING_INPUT' ? [{ waiting_item_id: '模拟等待-01', step_id: '模拟步骤-证据核查', action_id: '模拟动作-资料确认', target_revision: '7', reason_code: 'SIMULATED_MISSING_SOURCE', question: '模拟：请核查第 2 章缺失的授权来源', requirement_refs: ['模拟需求-FR09'], allowed_resolution_actions: ['模拟动作-补充资料'], assignee_id: '模拟责任人员', status: 'OPEN', resolution_id: null, revision: '1' }] : [],
    inflight_invocations: [], budget: { budget_id: '模拟预算-01', budget_revision: '2', spent: '12.00', reserved: '3.00', limit: '50.00', currency: 'CNY' }, started_at: '2026-10-20T07:00:00Z', ended_at: null, revision: '9', event_cursor: null }
}
