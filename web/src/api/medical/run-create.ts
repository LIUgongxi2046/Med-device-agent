import Ajv2020 from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import request from '@/utils/request';
import contract from '../../../../business/medical-persistence/src/main/resources/run-create.schema.json';
import { envelope, fail, revision, validContext, validate } from './protocol';
import type { MedicalSession } from './protocol';
import { taskActionAllowed } from './task-detail';
import type { CurrentTask } from './task-detail';
import { taskIdReference } from './tasks';
import { sourceDigest } from './content-digest';
import { lookupOperation } from './operations';
import type { Operation, OperationLookup } from './operations';
import { readRun } from './runs';
import type { RunView } from './runs';
export interface RunCreateBody {
    schema_version: '1.0.0';
    task_revision: string;
    plan_revision: string;
    snapshot_id: string;
    budget_revision: string;
    expected_revision: string;
}
export interface RunCreateReceipt {
    operation: Operation;
    run_id: string;
    task_id: string;
    task_revision: string;
    plan_revision: string;
    snapshot_id: string;
    budget_revision: string;
    command_id: string;
    status: 'QUEUED';
    revision: '1';
}
export interface PendingRunCreate {
    operation: 'RN-01';
    actor_id: string;
    tenant_id: string;
    project_id: string;
    task_id: string;
    budget_id: string;
    key: string;
    body_sha256: string;
    body: RunCreateBody;
}
const schema = 'urn:medical:frontend-run-create:1.0.0';
const ajv = new Ajv2020({
    strictSchema: true,
    strictTypes: false,
    allErrors: true
});
addFormats(ajv);
ajv.addSchema({
    ...contract,
    $id: schema
});
const pendingSchema = {
    type: 'object',
    additionalProperties: false,
    required: ['operation', 'actor_id', 'tenant_id', 'project_id', 'task_id', 'budget_id', 'key', 'body_sha256',
        'body'],
    properties: {
        operation: {
            const: 'RN-01'
        },
        ...Object.fromEntries(['actor_id', 'tenant_id', 'project_id', 'task_id', 'budget_id', 'key'].map(k => [k, {
                type: 'string',
                format: 'uuid'
            }])),
        body_sha256: {
            type: 'string',
            pattern: '^[a-f0-9]{64}$'
        },
        body: {
            $ref: schema + '#/$defs/Create'
        }
    }
};
const validatePending = ajv.compile(pendingSchema);
export function runCreateValidate<T>(name: string, value: unknown): T {
    const check = ajv.getSchema(schema + '#/$defs/' + name)!;
    if (!check(value))
        fail('SCHEMA_INVALID', '运行请求或回执字段不符合当前接口');
    return value as T;
}
function identity(session: MedicalSession, projectId: string, taskId: string): void {
    validate(taskIdReference, projectId);
    validate(taskIdReference, taskId);
    if (!session.current_context)
        fail('ACCESS_DENIED', '运行需要当前企业身份');
    validContext(session.current_context);
}
function storageKey(session: MedicalSession, projectId: string, taskId: string): string {
    identity(session, projectId, taskId);
    return ['medical.run.create', session.user.user_id, session.current_context!.tenant_id, projectId, taskId].join(':');
}
function owned(session: MedicalSession, p: PendingRunCreate): void {
    if (!validatePending(p))
        fail('CONTENT_MISMATCH', '原启动请求元数据不完整');
    identity(session, p.project_id, p.task_id);
    revision(p.body.expected_revision);
    if (p.actor_id !== session.user.user_id || p.tenant_id !== session.current_context!.tenant_id)
        fail('ACCESS_DENIED', '原请求属于其他身份或企业');
}
export function pendingRunCreate(session: MedicalSession, projectId: string, taskId: string): PendingRunCreate | null {
    const raw = sessionStorage.getItem(storageKey(session, projectId, taskId));
    if (raw === null)
        return null;
    let p: PendingRunCreate;
    try {
        p = JSON.parse(raw);
    }
    catch {
        fail('CONTENT_MISMATCH', '原启动请求元数据无法解析，请核查原操作');
    }
    ;
    owned(session, p);
    if (p.project_id !== projectId || p.task_id !== taskId)
        fail('CONTENT_MISMATCH', '原启动请求目标不一致');
    return p;
}
export function runCreateReady(current: CurrentTask): boolean {
    return current.detail.status === 'READY' && !!current.detail.plan_revision && !!current.detail.snapshot_id && current
        .detail.missing_requirements.length === 0 && current.detail.active_run_ids.length === 0;
}
export function runCreateInput(current: CurrentTask, budgetRevision: string): RunCreateBody {
    if (!runCreateReady(current))
        fail('PLAN_UNCONFIRMED', '启动需要就绪任务、已确认计划与来源快照，且没有活动运行');
    validate(taskIdReference, budgetRevision);
    revision(current.revision);
    if (current.revision === '0' || current.revision === '18446744073709551615')
        fail('REVISION_LIMIT', '任务记录修订不能用于启动');
    return {
        schema_version: '1.0.0',
        task_revision: current.detail.task_revision,
        plan_revision: current.detail.plan_revision!,
        snapshot_id: current.detail.snapshot_id!,
        budget_revision: budgetRevision,
        expected_revision: current.revision
    };
}
export async function prepareRunCreate(session: MedicalSession, current: CurrentTask, budgetRevision: string, signal: AbortSignal, budgetId: string): Promise<PendingRunCreate> {
    validate(taskIdReference, budgetId);
    const d = current.detail;
    identity(session, d.project_id, d.task_id);
    if (!taskActionAllowed(session, d.project_id, d.task_id, 'task.run'))
        fail('ACCESS_DENIED', '首次启动需要当前任务执行授权');
    const body = runCreateValidate<RunCreateBody>('Create', runCreateInput(current, budgetRevision));
    const hash = await sourceDigest(body);
    signal.throwIfAborted();
    const prior = pendingRunCreate(session, d.project_id, d.task_id);
    if (prior) {
        if (prior.budget_id !== budgetId || prior.body_sha256 !== hash || await sourceDigest(prior.body) !== hash)
            fail('IDEMPOTENCY_CONFLICT', '原启动请求必须保留原任务、计划、快照、预算及记录修订');
        return prior;
    }
    const p: PendingRunCreate = {
        operation: 'RN-01',
        actor_id: session.user.user_id,
        tenant_id: session.current_context!.tenant_id,
        project_id: d.project_id,
        task_id: d.task_id,
        budget_id: budgetId,
        key: crypto.randomUUID(),
        body_sha256: hash,
        body
    };
    owned(session, p);
    signal.throwIfAborted();
    sessionStorage.setItem(storageKey(session, d.project_id, d.task_id), JSON.stringify(p));
    return p;
}
async function verifyPending(session: MedicalSession, p: PendingRunCreate): Promise<void> {
    owned(session, p);
    const stored = pendingRunCreate(session, p.project_id, p.task_id);
    if (!stored || stored.key !== p.key || stored.body_sha256 !== p.body_sha256 || await sourceDigest(p.body) !== p
        .body_sha256 || await sourceDigest(stored.body) !== p.body_sha256)
        fail('IDEMPOTENCY_CONFLICT', '原启动请求已变化，不能发送或清除');
}
export async function submitRunCreate(session: MedicalSession, p: PendingRunCreate, signal: AbortSignal): Promise<RunCreateReceipt> {
    await verifyPending(session, p);
    if (!taskActionAllowed(session, p.project_id, p.task_id, 'task.run'))
        fail('ACCESS_DENIED', '当前身份没有任务执行授权');
    signal
        .throwIfAborted();
    // Once dispatched, this write is deliberately not aborted by navigation. Client cancellation
    // cannot prove server rollback. Keep the original key until lookup and run reread verify it.
    const raw = await request.post('/api/tasks/' + p.task_id + '/runs', p.body, {
        medical: 'envelope',
        headers: {
            'X-Schema-Version': '1.0.0',
            'X-Context-Revision': session.current_context!.context_revision,
            'Idempotency-Key': p.key,
            [session.csrf.header_name]: session.csrf.token
        }
    });
    const response = envelope(raw), r = runCreateValidate<RunCreateReceipt>('Receipt', response.data);
    if (raw.status !== 202 || response.resource_revision !== '1' || r.task_id !== p.task_id || r.operation
        .resource_id !== r.run_id || r.operation.status_url !== '/api/operations/' + r.operation.operation_id || [
        'task_revision', 'plan_revision', 'snapshot_id', 'budget_revision'
    ].some(key => r[key as keyof RunCreateReceipt] !== p.body[key as keyof RunCreateBody]))
        fail('CONTENT_MISMATCH', '排队回执与原任务、固定输入或操作不一致');
    return r;
}
export async function recoverRunCreate(session: MedicalSession, p: PendingRunCreate, signal: AbortSignal, receipt?: RunCreateReceipt): Promise<{
    lookup: OperationLookup;
    run: RunView | null;
}> {
    await verifyPending(session, p);
    signal.throwIfAborted();
    const lookup = await lookupOperation(session, 'RN-01', p.key, signal);
    let run: RunView | null = null;
    if (lookup.operation) {
        const op = lookup.operation;
        if (!op.resource_id || receipt && (receipt.operation.operation_id !== op.operation_id || receipt.run_id !== op
            .resource_id))
            fail('CONTENT_MISMATCH', '原启动操作与排队回执不一致');
        run = await readRun(session, p.project_id, p.task_id, op.resource_id, signal);
        if (run.task_revision !== p.body.task_revision || run.budget.budget_revision !== p.body.budget_revision || run
            .budget.budget_id !== p.budget_id)
            fail('CONTENT_MISMATCH', '运行与原固定任务或预算不一致');
        signal.throwIfAborted();
        if (op.status === 'COMPLETED' || op.status === 'FAILED') {
            await verifyPending(session, p);
            signal.throwIfAborted();
            sessionStorage.removeItem(storageKey(session, p.project_id, p.task_id));
        }
    }
    else if (receipt)
        fail('CONTENT_MISMATCH', '已收到排队回执，但原操作尚未能确认，保留请求标识');
    return {
        lookup,
        run
    };
}
