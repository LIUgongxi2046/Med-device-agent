import request from '@/utils/request'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { Basis, Context, MedicalSession } from './protocol'
import { formatProjectJson } from './project-json'
import { projectTextIssue } from './project-write'
import { readOperation } from './operations'
import type { Operation } from './operations'

export interface BudgetLimits {
  amount: string; tokens: string; calls: string; research_rounds: string; revision_rounds: string; deadline_at: string
}
export interface BudgetApproval {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; budget_id: string; currency: string; limits: BudgetLimits
  approval_basis: Basis; approver_id: string; reason: string; authority_grant_id: string; authority_revision: string
  based_on_budget_revision: string | null
}
export interface BudgetAccount {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; budget_id: string; budget_revision: string; currency: string
  spent: string; reserved: string; spent_tokens: string; reserved_tokens: string; spent_calls: string; reserved_calls: string
  approval: BudgetApproval; content_sha256: string; approver_id: string; approved_at: string
  created_by: string; created_at: string; updated_at: string; revision: string; allowed_actions: string[]
}
export interface BudgetListing { schema_version: '1.0.0'; budgets: BudgetAccount[]; total: string; next_after: string | null }
export interface BudgetHistoryEntry {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; budget_id: string; budget_revision: string
  current_budget_revision: string; account_revision: string; currency: string; approval: BudgetApproval
  content_sha256: string; approver_id: string; approved_at: string; is_current: boolean; allowed_actions: ['budget.read']
}
export interface BudgetHistoryListing {
  schema_version: '1.0.0'; tenant_id: string; project_id: string | null; budget_id: string
  current_budget_revision: string; account_revision: string; approvals: BudgetHistoryEntry[]; total: string; next_after: string | null
}
export interface CreateBudget {
  schema_version: '1.0.0'; project_id: string | null; currency: string; limits: BudgetLimits
  approval_basis: Basis; reason: string; approval_attested: true
}
export interface ApproveBudget {
  schema_version: '1.0.0'; expected_revision: string; based_on_budget_revision: string; currency: string; limits: BudgetLimits
  approval_basis: Basis; reason: string; approval_attested: true
}
export type BudgetWriteBody = CreateBudget | ApproveBudget
export type BudgetOperation = 'BD-02' | 'BD-04'
export interface PendingBudgetWrite {
  actor: string; tenant_id: string; operation: BudgetOperation; budget_id: string | null; project_id: string | null
  key: string; context: Context; body_sha256: string; expected_revision: string | null; based_on_budget_revision: string | null; currency: string
}
export interface BudgetReceipt {
  schema_version: '1.0.0'; operation_id: string; decision: 'CREATE' | 'APPROVE'; tenant_id: string; project_id: string | null
  budget_id: string; budget_revision: string; revision: string; currency: string; content_sha256: string; current_context: Context
}
interface Target { project_id?: string | null; budget_id: string | null }
const pendingSchema = 'urn:medical:budget-write-view:1.0.0'
const schema = 'urn:medical:budgets:1.0.0#/$defs/'
const maximum = '18446744073709551615'

export function budgetActionAllowed(session: MedicalSession | null, action: string, target?: Target): boolean {
  const context = session?.current_context
  if (!session || !context) return false
  return session.action_summary.some(item => item.action === action && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === context.tenant_id
    || item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id
      && (target?.project_id === undefined || item.project_id === target.project_id)
    || item.resource_kind === 'BUDGET' && (target === undefined ||
      (item.project_id === null || target.project_id === undefined || item.project_id === target.project_id)
      && (item.resource_id === null || item.resource_id === target.budget_id))
  ))
}

export function budgetCreationAllowed(session: MedicalSession | null): boolean {
  if (!session?.current_context) return false
  return session.action_summary.some(item => item.action === 'budget.approve' && (
    item.resource_kind === 'ENTERPRISE' && item.project_id === null && item.resource_id === session.current_context!.tenant_id
    || item.resource_kind === 'PROJECT' && item.project_id !== null && item.resource_id === item.project_id
    || item.resource_kind === 'BUDGET' && item.resource_id === null
  ))
}

function current(session: MedicalSession): Context {
  if (!session.current_context) fail('ACCESS_DENIED', '预算操作需要当前企业身份')
  validContext(session.current_context)
  return session.current_context
}
function positive(value: string): void { if (revision(value) === '0') fail('SCHEMA_INVALID', '预算版本必须为有效正整数') }
function checkLimits(limits: BudgetLimits): void {
  validate(schema + 'Limits', limits)
  for (const value of [limits.tokens, limits.calls, limits.research_rounds, limits.revision_rounds]) revision(value)
}
function checkAccount(value: BudgetAccount, context: Context): void {
  const approval = value.approval
  if (value.tenant_id !== context.tenant_id || approval.tenant_id !== value.tenant_id
      || approval.project_id !== value.project_id || approval.budget_id !== value.budget_id
      || approval.currency !== value.currency || approval.approver_id !== value.approver_id
      || approval.based_on_budget_revision === value.budget_revision || projectTextIssue(approval.reason, 2000)) {
    fail('CONTENT_MISMATCH', '预算账户与当前企业、批准身份或固定版本不一致')
  }
  positive(value.revision); positive(approval.authority_revision); checkLimits(approval.limits)
  for (const count of [value.spent_tokens, value.reserved_tokens, value.spent_calls, value.reserved_calls]) revision(count)
}

export async function listBudgets(session: MedicalSession, filters: { limit: string; project_id?: string; after?: string }, signal: AbortSignal): Promise<BudgetListing> {
  const context = current(session)
  if (!budgetActionAllowed(session, 'budget.read')) fail('ACCESS_DENIED', '预算读取需要明确授权')
  if (!['10', '20', '50', '100'].includes(filters.limit)) fail('SCHEMA_INVALID', '预算分页数量无效')
  for (const value of [filters.project_id, filters.after]) if (value !== undefined) validate(schema + 'Id', value)
  const response = envelope(await request.get('/api/budgets', { medical: 'envelope', signal, params: filters,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const listing = validate<BudgetListing>(schema + 'Listing', response.data)
  revision(listing.total)
  if (response.resource_revision !== null || new Set(listing.budgets.map(item => item.budget_id)).size !== listing.budgets.length
      || BigInt(listing.total) < BigInt(listing.budgets.length) || listing.budgets.length > Number(filters.limit)
      || listing.next_after !== null && listing.next_after !== listing.budgets.at(-1)?.budget_id) {
    fail('CONTENT_MISMATCH', '预算集合版本、游标或数量不一致')
  }
  let previous = filters.after
  for (const account of listing.budgets) {
    checkAccount(account, context)
    if (!account.allowed_actions.includes('budget.read') || !budgetActionAllowed(session, 'budget.read', account)
        || filters.project_id !== undefined && account.project_id !== filters.project_id
        || previous !== undefined && account.budget_id <= previous) fail('CONTENT_MISMATCH', '预算列表超出当前授权、筛选或排序范围')
    previous = account.budget_id
  }
  return listing
}

export async function readBudget(session: MedicalSession, id: string, signal: AbortSignal): Promise<BudgetAccount> {
  const context = current(session); validate(schema + 'Id', id)
  if (!budgetActionAllowed(session, 'budget.read', { budget_id: id })) fail('ACCESS_DENIED', '预算读取需要明确授权')
  const response = envelope(await request.get('/api/budgets/' + id, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const account = validate<BudgetAccount>(schema + 'Account', response.data)
  checkAccount(account, context)
  if (account.budget_id !== id || response.resource_revision !== account.revision
      || !account.allowed_actions.includes('budget.read') || !budgetActionAllowed(session, 'budget.read', account)) {
    fail('CONTENT_MISMATCH', '预算响应与请求目标、版本或授权不一致')
  }
  return account
}

function checkHistoryEntry(value: BudgetHistoryEntry, session: MedicalSession, id: string): void {
  const context = current(session); const approval = value.approval
  if (value.tenant_id !== context.tenant_id || value.budget_id !== id
      || approval.tenant_id !== value.tenant_id || approval.project_id !== value.project_id
      || approval.budget_id !== id || approval.currency !== value.currency || approval.approver_id !== value.approver_id
      || approval.based_on_budget_revision === value.budget_revision || projectTextIssue(approval.reason, 2000)
      || value.is_current !== (value.current_budget_revision === value.budget_revision)
      || !budgetActionAllowed(session, 'budget.read', value)) fail('CONTENT_MISMATCH', '预算历史与企业、目标、批准身份或当前读取许可不一致')
  positive(value.account_revision); positive(approval.authority_revision); checkLimits(approval.limits)
}

export async function listBudgetHistory(session: MedicalSession, id: string, filters: { limit: string; after?: string }, signal: AbortSignal): Promise<BudgetHistoryListing> {
  const context = current(session); validate(schema + 'Id', id)
  if (!budgetActionAllowed(session, 'budget.read', { budget_id: id })) fail('ACCESS_DENIED', '预算历史读取需要明确授权')
  if (!['10', '20', '50', '100'].includes(filters.limit)) fail('SCHEMA_INVALID', '预算历史分页数量无效')
  if (filters.after !== undefined) validate(schema + 'Id', filters.after)
  const response = envelope(await request.get('/api/budgets/' + id + '/revisions', { medical: 'envelope', signal, params: filters,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const listing = validate<BudgetHistoryListing>('urn:medical:budget-history:1.0.0#/$defs/Listing', response.data)
  positive(listing.account_revision); revision(listing.total)
  if (listing.tenant_id !== context.tenant_id || listing.budget_id !== id || response.resource_revision !== listing.account_revision
      || !budgetActionAllowed(session, 'budget.read', listing)
      || new Set(listing.approvals.map(item => item.budget_revision)).size !== listing.approvals.length
      || BigInt(listing.total) < BigInt(listing.approvals.length) || listing.approvals.length > Number(filters.limit)
      || listing.next_after !== null && listing.next_after !== listing.approvals.at(-1)?.budget_revision) {
    fail('CONTENT_MISMATCH', '预算历史集合与目标、版本、授权或分页数量不一致')
  }
  let previous = filters.after
  for (const entry of listing.approvals) {
    checkHistoryEntry(entry, session, id)
    if (entry.project_id !== listing.project_id || entry.current_budget_revision !== listing.current_budget_revision
        || entry.account_revision !== listing.account_revision || previous !== undefined && entry.budget_revision <= previous) {
      fail('CONTENT_MISMATCH', '预算历史集合包含其他范围、版本或排序结果')
    }
    previous = entry.budget_revision
  }
  return listing
}

export async function readBudgetHistory(session: MedicalSession, id: string, version: string, signal: AbortSignal): Promise<BudgetHistoryEntry> {
  const context = current(session); validate(schema + 'Id', id); validate(schema + 'Id', version)
  if (!budgetActionAllowed(session, 'budget.read', { budget_id: id })) fail('ACCESS_DENIED', '预算历史读取需要明确授权')
  const response = envelope(await request.get('/api/budgets/' + id + '/revisions/' + version, { medical: 'envelope', signal,
    headers: { 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision } }))
  const entry = validate<BudgetHistoryEntry>('urn:medical:budget-history:1.0.0#/$defs/Entry', response.data)
  checkHistoryEntry(entry, session, id)
  if (entry.budget_revision !== version || response.resource_revision !== entry.account_revision) {
    fail('CONTENT_MISMATCH', '预算历史响应与指定版本或账户版本不一致')
  }
  return entry
}

function storageKey(session: MedicalSession, id: string | null): string {
  return 'medical.budget.write:' + session.user.user_id + ':' + current(session).tenant_id + ':' + (id ?? 'CREATE')
}
export function readPendingBudgetWrite(session: MedicalSession, id: string | null): PendingBudgetWrite | null {
  const saved = sessionStorage.getItem(storageKey(session, id))
  if (saved === null) return null
  const value = validate<PendingBudgetWrite>(pendingSchema, JSON.parse(saved))
  validContext(value.context)
  if (value.expected_revision !== null) positive(value.expected_revision)
  if (value.actor !== session.user.user_id || value.tenant_id !== current(session).tenant_id || value.budget_id !== id
      || value.context.tenant_id !== value.tenant_id) fail('CONTENT_MISMATCH', '原预算请求与当前主体、企业或目标不一致')
  return value
}
export function budgetBodyReference(operation: BudgetOperation): string { return schema + (operation === 'BD-02' ? 'Create' : 'Decide') }
function checkBody(operation: BudgetOperation, body: BudgetWriteBody): void {
  validate(budgetBodyReference(operation), body); checkLimits(body.limits)
  if (projectTextIssue(body.reason, 2000)) fail('SCHEMA_INVALID', '预算批准原因无效')
  if ('expected_revision' in body) {
    positive(body.expected_revision)
    if (body.expected_revision === maximum) fail('REVISION_LIMIT', '预算账户版本无法继续增加')
  }
}
async function bodyHash(body: BudgetWriteBody): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(formatProjectJson(body)))
  return Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('')
}
function requireWrite(session: MedicalSession, operation: BudgetOperation, target: Target): void {
  if (!budgetActionAllowed(session, 'budget.approve', target)
      || operation === 'BD-04' && !budgetActionAllowed(session, 'budget.read', target)) fail('ACCESS_DENIED', '当前预算动作没有明确授权')
}
export async function prepareBudgetWrite(session: MedicalSession, operation: BudgetOperation, id: string | null,
  body: BudgetWriteBody, projectId?: string | null): Promise<PendingBudgetWrite> {
  const context = current(session)
  if (!budgetActionAllowed(session, 'budget.approve')) fail('ACCESS_DENIED', '当前预算动作没有明确授权')
  checkBody(operation, body)
  if ((operation === 'BD-02') !== (id === null) || operation === 'BD-04' && projectId === undefined) fail('SCHEMA_INVALID', '预算动作缺少明确目标范围')
  const project = 'project_id' in body ? body.project_id : projectId!
  requireWrite(session, operation, { project_id: project, budget_id: id })
  const digest = await bodyHash(body); const previous = readPendingBudgetWrite(session, id)
  if (previous !== null) {
    if (previous.operation !== operation || previous.project_id !== project || previous.body_sha256 !== digest) {
      fail('IDEMPOTENCY_CONFLICT', '原预算请求仍待核查，请保留完整原参数及版本')
    }
    return previous
  }
  if (context.context_revision === maximum || context.governance_epoch === maximum) fail('REVISION_LIMIT', '当前上下文或治理版本无法继续增加')
  const value: PendingBudgetWrite = {
    actor: session.user.user_id, tenant_id: context.tenant_id, operation, budget_id: id, project_id: project,
    key: crypto.randomUUID(), context: { ...context }, body_sha256: digest, currency: body.currency,
    expected_revision: 'expected_revision' in body ? body.expected_revision : null,
    based_on_budget_revision: 'based_on_budget_revision' in body ? body.based_on_budget_revision : null
  }
  validate(pendingSchema, value)
  // 保留原请求技术身份；限制数值、批准原因和依据内容不写入浏览器持久存储。
  sessionStorage.setItem(storageKey(session, id), JSON.stringify(value))
  return value
}
export async function submitBudgetWrite(session: MedicalSession, pending: PendingBudgetWrite, body: BudgetWriteBody): Promise<BudgetReceipt> {
  const context = current(session); validate(pendingSchema, pending); validContext(pending.context); checkBody(pending.operation, body)
  requireWrite(session, pending.operation, pending)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== context.tenant_id || pending.context.tenant_id !== context.tenant_id
      || body.currency !== pending.currency || await bodyHash(body) !== pending.body_sha256
      || 'project_id' in body && body.project_id !== pending.project_id
      || 'expected_revision' in body && (body.expected_revision !== pending.expected_revision || body.based_on_budget_revision !== pending.based_on_budget_revision)) {
    fail('CONTENT_MISMATCH', '原预算请求与当前主体、企业、版本或完整参数不一致')
  }
  const response = await request.post(pending.operation === 'BD-02' ? '/api/budgets' : '/api/budgets/' + pending.budget_id + '/decisions',
    formatProjectJson(body), { medical: 'envelope', headers: {
      'X-Schema-Version': '1.0.0', 'X-Context-Revision': pending.context.context_revision,
      'Idempotency-Key': pending.key, [session.csrf.header_name]: session.csrf.token
    } })
  const value = envelope(response)
  if (response.status !== (pending.operation === 'BD-02' ? 201 : 200)) fail('CONTENT_MISMATCH', '预算写入尚未取得明确完成响应')
  const receipt = validate<BudgetReceipt>(schema + 'Receipt', value.data)
  validContext(receipt.current_context); positive(receipt.revision)
  if (receipt.tenant_id !== pending.tenant_id || receipt.current_context.tenant_id !== pending.tenant_id
      || receipt.project_id !== pending.project_id || pending.budget_id !== null && receipt.budget_id !== pending.budget_id
      || receipt.decision !== (pending.operation === 'BD-02' ? 'CREATE' : 'APPROVE') || receipt.currency !== pending.currency
      || value.resource_revision !== receipt.revision || receipt.budget_revision === pending.based_on_budget_revision
      || receipt.revision !== (pending.expected_revision === null ? '1' : (BigInt(pending.expected_revision) + 1n).toString())
      || receipt.current_context.context_revision !== (BigInt(pending.context.context_revision) + 1n).toString()
      || receipt.current_context.governance_epoch !== (BigInt(pending.context.governance_epoch) + 1n).toString()
      || receipt.current_context.auth_epoch !== pending.context.auth_epoch) fail('CONTENT_MISMATCH', '预算回执与原目标、批准版本或上下文不一致')
  return receipt
}
export async function confirmBudgetOperation(session: MedicalSession, pending: PendingBudgetWrite, id: string,
  budgetId: string, signal: AbortSignal): Promise<Operation> {
  validate(pendingSchema, pending); validate(schema + 'Id', budgetId)
  if (pending.actor !== session.user.user_id || pending.tenant_id !== current(session).tenant_id
      || pending.budget_id !== null && pending.budget_id !== budgetId) fail('ACCESS_DENIED', '原预算操作核查缺少当前本人及企业身份')
  const operation = await readOperation(session, id, signal)
  if (operation.status !== 'COMPLETED' || operation.resource_id !== budgetId) fail('CONTENT_MISMATCH', '原预算操作尚未取得一致完成结果')
  return operation
}
export function verifyBudgetWrite(account: BudgetAccount, pending: PendingBudgetWrite, body: BudgetWriteBody, receipt: BudgetReceipt): void {
  const approval = account.approval
  if (account.budget_id !== receipt.budget_id || account.project_id !== pending.project_id || account.revision !== receipt.revision
      || account.budget_revision !== receipt.budget_revision || account.currency !== receipt.currency || account.content_sha256 !== receipt.content_sha256
      || approval.approver_id !== pending.actor || approval.based_on_budget_revision !== pending.based_on_budget_revision
      || approval.reason !== body.reason || approval.approval_basis.version_id !== body.approval_basis.version_id
      || approval.approval_basis.content_sha256 !== body.approval_basis.content_sha256
      || (Object.keys(body.limits) as (keyof BudgetLimits)[]).some(key => body.limits[key] !== approval.limits[key])) {
    fail('SCOPE_STALE', '原操作完成以后预算或批准定义已变化，请核查当前账户')
  }
}
export function clearCompletedBudgetWrite(session: MedicalSession, pending: PendingBudgetWrite, operation: Operation): void {
  const saved = readPendingBudgetWrite(session, pending.budget_id)
  validate('urn:medical:shared#/$defs/Operation', operation)
  if (saved === null || saved.key !== pending.key || saved.body_sha256 !== pending.body_sha256 || operation.status !== 'COMPLETED'
      || operation.resource_id === null || pending.budget_id !== null && operation.resource_id !== pending.budget_id) {
    fail('CONTENT_MISMATCH', '原预算请求仍需核查')
  }
  sessionStorage.removeItem(storageKey(session, pending.budget_id))
}
