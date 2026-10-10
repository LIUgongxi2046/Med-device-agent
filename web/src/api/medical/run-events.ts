import { createParser } from 'eventsource-parser'
import type { EventSourceMessage } from 'eventsource-parser'
import { envelope, fail, revision, validate, validContext } from './protocol'
import type { MedicalSession } from './protocol'
import { taskActionAllowed } from './task-detail'
import { taskIdReference } from './tasks'

export interface PublicRunEvent {
  schema_version: '1.0.0'; event_id: string; stream: { stream_id: string; kind: 'RUN'; resource_id: string }
  sequence: string; event_type: string; tenant_id: string; project_id: string; task_id: string; run_id: string
  resource_kind: string; resource_id: string; resource_revision: string; occurred_at: string; payload: Record<string, unknown>
}

export async function consumeRunEvents(session: MedicalSession, projectId: string, taskId: string, runId: string,
  after: string | null, signal: AbortSignal, receive: (event: PublicRunEvent) => Promise<void>): Promise<void> {
  for (const id of [projectId, taskId, runId]) validate(taskIdReference, id)
  if (after !== null) validate(taskIdReference, after)
  const context = session.current_context
  if (!context || !taskActionAllowed(session, projectId, taskId, 'run.read')) fail('ACCESS_DENIED', '事件读取需要当前任务运行许可')
  validContext(context)
  const headers: Record<string, string> = { Accept: 'text/event-stream', 'X-Schema-Version': '1.0.0', 'X-Context-Revision': context.context_revision }
  if (after !== null) headers['Last-Event-ID'] = after
  const response = await fetch('/api/runs/' + runId + '/events', { method: 'GET', credentials: 'same-origin', headers, signal, cache: 'no-store', redirect: 'error' })
  if (!response.ok) {
    const data: unknown = await response.json()
    const returnedHeaders: Record<string, string> = {}; response.headers.forEach((value, name) => { returnedHeaders[name] = value })
    envelope({ status: response.status, data, headers: returnedHeaders, statusText: response.statusText, config: { headers: {} } } as Parameters<typeof envelope>[0])
    fail('CONTENT_MISMATCH', '事件错误响应缺少拒绝结果')
  }
  if (!response.headers.get('content-type')?.startsWith('text/event-stream') || response.headers.get('cache-control') !== 'no-store'
      || response.headers.get('x-schema-version') !== '1.0.0' || !response.body) fail('SCHEMA_INVALID', '运行事件响应Header无效')
  const reader = response.body.getReader(); const decoder = new TextDecoder('utf-8', { fatal: true })
  const queue: EventSourceMessage[] = []; let streamId: string | null = null; let sequence: bigint | null = null
  let cursor = after
  const parser = createParser({
    maxBufferSize: 262144,
    onEvent: event => { queue.push(event) },
    onError: error => { throw error }
  })
  try {
    while (!signal.aborted) {
      const part = await reader.read()
      if (part.done) { parser.feed(decoder.decode()); parser.reset({ consume: true }); fail('DEPENDENCY_UNAVAILABLE', '运行事件连接已经结束，请读取当前快照') }
      if (part.value.byteLength > 1024 * 1024) fail('CONTEXT_LIMIT', '运行事件超过允许缓冲容量')
      parser.feed(decoder.decode(part.value, { stream: true }))
      while (queue.length) {
        const message = queue.shift()!
        const event = validate<PublicRunEvent>('https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/PublicEvent', JSON.parse(message.data))
        if (message.id !== event.event_id || message.event !== event.event_type || event.stream.kind !== 'RUN' || event.stream.resource_id !== runId
            || event.tenant_id !== context.tenant_id || event.project_id !== projectId || event.task_id !== taskId || event.run_id !== runId) {
          fail('CONTENT_MISMATCH', '持久事件与当前运行身份不一致')
        }
        revision(event.sequence); revision(event.resource_revision)
        if (streamId !== null && streamId !== event.stream.stream_id) fail('CONTENT_MISMATCH', '运行事件目录发生变化')
        streamId = event.stream.stream_id
        if (event.event_id === cursor) continue
        const next = BigInt(event.sequence)
        if (next <= 0n || sequence !== null && next !== sequence + 1n) fail('CURSOR_EXPIRED', '运行事件序号需要重新读取快照')
        if (signal.aborted) return
        await receive(event)
        sequence = next; cursor = event.event_id
      }
    }
  } finally { await reader.cancel(); reader.releaseLock() }
}
