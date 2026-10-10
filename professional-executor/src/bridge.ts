import { lstat, readFile } from 'node:fs/promises'
import { relative } from 'node:path'
import { Client } from 'undici'
import { createParser } from 'eventsource-parser'
import { canonicalize } from 'json-canonicalize'
import { decodeModelStream, type StreamRequest, type StreamEvent, type CancelRequest, type InvocationStatus } from '@medical-agent/contracts/model-stream'
import { type SchemaCatalogue, type JsonValue } from '@medical-agent/contracts'
import { readJson } from './json.js'
import { protocolError, type ModelResultReader } from './model-stream.js'

export interface BridgeLimits {
  receiveTimeoutMs: number
  streamTimeoutMs: number
  maxResponseBytes: number
  maxEventCharacters: number
}

export type ModelOpening = { kind: 'stream'; events: AsyncIterable<StreamEvent> } | { kind: 'original'; status: InvocationStatus }

export class PrivateBridge implements ModelResultReader {
  private constructor(private readonly client: Client, private readonly token: string,
    private readonly catalogue: SchemaCatalogue, private readonly limits: BridgeLimits) {}

  static async connect(socketPath: string, tokenPath: string, catalogue: SchemaCatalogue, limits: BridgeLimits): Promise<PrivateBridge> {
    const socket = await lstat(socketPath)
    const tokenFile = await lstat(tokenPath)
    if (!socket.isSocket() || socket.isSymbolicLink() || (socket.mode & 0o077) !== 0 || socket.uid !== process.getuid?.()) throw new Error('专业通信 Socket 权限不正确')
    if (!tokenFile.isFile() || tokenFile.isSymbolicLink() || (tokenFile.mode & 0o077) !== 0 || tokenFile.uid !== process.getuid?.()) throw new Error('专业通信凭据权限不正确')
    const token = (await readFile(tokenPath, 'utf-8')).trim()
    if (!/^[A-Za-z0-9_-]{43,128}$/.test(token)) throw new Error('专业通信凭据格式不正确')
    const connectionPath = relative(process.cwd(), socketPath)
    if (Buffer.byteLength(connectionPath) >= 104) throw new Error('专业通信 Socket 路径超过系统限制')
    return new PrivateBridge(new Client('http://localhost', { socketPath: connectionPath, headersTimeout: limits.receiveTimeoutMs, connectTimeout: limits.receiveTimeoutMs,
      bodyTimeout: limits.receiveTimeoutMs, maxResponseSize: limits.maxResponseBytes, pipelining: 0 }), token, catalogue, limits)
  }

  private async request(path: string, body: JsonValue | null, signal: AbortSignal, key?: string) {
    signal.throwIfAborted()
    const headers: Record<string, string> = { authorization: `Bearer ${this.token}`, accept: 'application/json, text/event-stream' }
    if (body !== null) headers['content-type'] = 'application/json; charset=utf-8'
    if (key !== undefined) headers['idempotency-key'] = key
    const response = await this.client.request({ path, method: body === null ? 'GET' : 'POST', headers,
      ...(body === null ? {} : { body: canonicalize(body) }), signal })
    if (response.statusCode !== 200) {
      await response.body.dump()
      throw new Error(`专业通信返回 HTTP ${response.statusCode}`)
    }
    return response
  }

  async prepare(projection: JsonValue, signal: AbortSignal): Promise<StreamRequest> {
    const response = await this.request('/v1/model-contexts', projection, signal)
    return decodeModelStream(this.catalogue, 'StreamRequest', readJson(await response.body.text()))
  }

  async open(request: StreamRequest, signal: AbortSignal): Promise<ModelOpening> {
    const value = readJson(canonicalize(request))
    const response = await this.request('/v1/model-streams', value, signal, request.action_id)
    const contentType = response.headers['content-type']
    if (typeof contentType !== 'string') { await response.body.dump(); protocolError('模型响应缺少媒体类型') }
    if (contentType.split(';')[0]?.trim() === 'application/json') {
      const status = decodeModelStream(this.catalogue, 'InvocationStatus', readJson(await response.body.text()))
      if (status.origin_action_id !== request.action_id || status.context_manifest_id !== request.context_manifest_id || status.model_config_revision !== request.model_config_revision) protocolError('原调用状态身份不一致')
      return { kind: 'original', status }
    }
    if (!/^text\/event-stream\s*;\s*charset=utf-8$/i.test(contentType) || response.headers['cache-control'] !== 'no-store') {
      await response.body.dump()
      protocolError('模型流响应头不正确')
    }
    const catalogue = this.catalogue
    const maxBufferSize = this.limits.maxEventCharacters
    async function* events(): AsyncIterable<StreamEvent> {
      const decoder = new TextDecoder('utf-8', { fatal: true })
      const queue: StreamEvent[] = []
      const parser = createParser({ maxBufferSize, onError: error => { throw error }, onRetry: () => protocolError('模型流不能重连'), onEvent: message => {
        const event = decodeModelStream(catalogue, 'StreamEvent', readJson(message.data))
        if (message.event !== event.payload.type || message.id !== `${event.invocation_id}:${event.sequence}`) protocolError('模型 SSE 事件标识不一致')
        queue.push(event)
      } })
      try {
        for await (const bytes of response.body) {
          signal.throwIfAborted()
          parser.feed(decoder.decode(bytes as Uint8Array, { stream: true }))
          while (queue.length !== 0) yield queue.shift()!
        }
        parser.feed(decoder.decode())
        parser.reset({ consume: true })
        while (queue.length !== 0) yield queue.shift()!
      } finally {
        response.body.destroy()
      }
    }
    return { kind: 'stream', events: events() }
  }

  async readResult(artifactId: string, signal: AbortSignal): Promise<Uint8Array> {
    const response = await this.request(`/v1/model-results/${encodeURIComponent(artifactId)}`, null, signal)
    return response.body.bytes()
  }

  async cancel(request: CancelRequest): Promise<InvocationStatus> {
    decodeModelStream(this.catalogue, 'CancelRequest', request)
    const response = await this.request('/v1/model-cancellations', readJson(canonicalize(request)), AbortSignal.timeout(10_000), request.action_id)
    return decodeModelStream(this.catalogue, 'InvocationStatus', readJson(await response.body.text()))
  }

  async tool(toolId: string, body: JsonValue, signal: AbortSignal): Promise<JsonValue> {
    const response = await this.request(`/v1/tools/${encodeURIComponent(toolId)}`, body, signal)
    return readJson(await response.body.text())
  }

  async close(): Promise<void> { await this.client.close() }
}
