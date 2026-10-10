import type { DocumentBody } from '../../../../contracts/typescript/src/interfaces'
import { canonicalizeEx } from 'json-canonicalize'
import { shallowRef } from 'vue'
import { useManualRefHistory } from '@vueuse/core'
import type { ShallowRef } from 'vue'
import type { UseManualRefHistoryReturn } from '@vueuse/core'
import { MedicalFailure, validate } from './protocol'

export interface EncodedDocumentBody {
  body: DocumentBody; text: string; blob: Blob; sha256: string; sizeBytes: string
}

export const documentReference = 'https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/'
export const documentBodyReference = 'interfaces.schema.yaml#/$defs/DocumentBody'

export function canonicalDocumentValue(value: unknown): string {
  const ancestors = new WeakSet<object>()
  function check(item: unknown): void {
    if (typeof item === 'string') {
      if (new TextDecoder('utf-8', { fatal: true }).decode(new TextEncoder().encode(item)) !== item) {
        throw new MedicalFailure('SCHEMA_INVALID', '正文包含不完整Unicode字符')
      }
    } else if (typeof item === 'number') {
      if (!Number.isFinite(item) || Number.isInteger(item) && !Number.isSafeInteger(item)) {
        throw new MedicalFailure('SCHEMA_INVALID', '正文数值无法精确编码')
      }
    } else if (Array.isArray(item)) {
      if (ancestors.has(item)) throw new MedicalFailure('SCHEMA_INVALID', '正文JSON包含循环关系')
      ancestors.add(item)
      for (const child of item) check(child)
      ancestors.delete(item)
    } else if (item !== null && typeof item === 'object') {
      if (ancestors.has(item)) throw new MedicalFailure('SCHEMA_INVALID', '正文JSON包含循环关系')
      if (Object.getPrototypeOf(item) !== Object.prototype && Object.getPrototypeOf(item) !== null) {
        throw new MedicalFailure('SCHEMA_INVALID', '正文需要完整JSON对象')
      }
      ancestors.add(item)
      for (const [key, child] of Object.entries(item)) { check(key); check(child) }
      ancestors.delete(item)
    } else if (item !== null && typeof item !== 'boolean') {
      throw new MedicalFailure('SCHEMA_INVALID', '正文包含未经JSON定义的内容')
    }
  }
  check(value)
  return canonicalizeEx(value, { strictUndefined: true })
}

export async function documentBytesDigest(bytes: BufferSource): Promise<string> {
  const hash = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
  return Array.from(hash, byte => byte.toString(16).padStart(2, '0')).join('')
}

export function immutableDocumentValue<T>(value: T): T {
  const copy = structuredClone(value)
  const visited = new WeakSet<object>()
  function freeze(item: unknown): void {
    if (item === null || typeof item !== 'object') return
    if (visited.has(item)) return
    visited.add(item)
    Object.values(item).forEach(freeze); Object.freeze(item)
  }
  freeze(copy); return copy
}

export async function encodeDocumentBody(value: DocumentBody): Promise<EncodedDocumentBody> {
  // 在第一次异步处理以前取得独立正文，后续编辑不能改变本次上传字节。
  const body = immutableDocumentValue(value)
  const text = canonicalDocumentValue(body)
  validate(documentReference + 'DocumentBody', body)
  const bytes = new TextEncoder().encode(text)
  return Object.freeze({ body, text, blob: new Blob([bytes], { type: 'application/json' }),
    sha256: await documentBytesDigest(bytes), sizeBytes: String(bytes.length) })
}

export interface DocumentEditCapture { readonly id: string; readonly generation: bigint; readonly body: DocumentBody }

export class DocumentEditBuffer {
  private value: ShallowRef<DocumentBody | null>
  private history: UseManualRefHistoryReturn<DocumentBody | null, DocumentBody | null>
  private savedText: string | null
  private generation = 0n
  private pending: DocumentEditCapture | null = null

  constructor(body: DocumentBody, historyCapacity: number) {
    if (!Number.isSafeInteger(historyCapacity) || historyCapacity < 1) throw new MedicalFailure('SCHEMA_INVALID', '正文撤销需要明确的历史容量')
    const value = immutableDocumentValue(body)
    const text = canonicalDocumentValue(value); validate(documentReference + 'DocumentBody', value)
    this.value = shallowRef(value); this.savedText = text
    this.history = useManualRefHistory<DocumentBody | null>(this.value, { capacity: historyCapacity })
  }

  get body(): DocumentBody {
    if (this.value.value === null) throw new MedicalFailure('ACTION_STALE', '当前文稿编辑已经终止')
    return this.value.value
  }
  get dirty(): boolean { return this.value.value !== null && canonicalDocumentValue(this.value.value) !== this.savedText }
  get canUndo(): boolean { return this.value.value !== null && this.history.canUndo.value }
  get canRedo(): boolean { return this.value.value !== null && this.history.canRedo.value }
  get saving(): boolean { return this.pending !== null }
  isCurrentCapture(capture: DocumentEditCapture): boolean { return this.value.value !== null && this.pending === capture }
  releaseUnissued(capture: DocumentEditCapture): void { if (this.pending === capture) this.pending = null }

  edit(body: DocumentBody): void {
    const previous = this.body, value = immutableDocumentValue(body), text = canonicalDocumentValue(value)
    validate(documentReference + 'DocumentBody', value)
    if (previous.template_version !== value.template_version) throw new MedicalFailure('SCOPE_STALE', '当前编辑不能改变固定模板身份')
    if (canonicalDocumentValue(previous) === text) return
    this.value.value = value; this.history.commit(); this.generation++
  }

  undo(): void {
    this.body
    if (!this.canUndo) throw new MedicalFailure('ACTION_STALE', '当前文稿没有可以撤销的编辑')
    this.history.undo(); this.generation++
  }

  redo(): void {
    this.body
    if (!this.canRedo) throw new MedicalFailure('ACTION_STALE', '当前文稿没有可以重做的编辑')
    this.history.redo(); this.generation++
  }

  capture(): DocumentEditCapture {
    const body = this.body
    if (this.pending !== null) return this.pending
    if (!this.dirty) throw new MedicalFailure('ACTION_STALE', '当前正文没有尚未保存的修改')
    this.pending = Object.freeze({ id: crypto.randomUUID(), generation: this.generation, body })
    return this.pending
  }

  confirm(capture: DocumentEditCapture, saved: DocumentBody): void {
    this.body
    if (this.pending !== capture) throw new MedicalFailure('ACTION_STALE', '正文保存回执属于其他编辑操作')
    const value = immutableDocumentValue(saved), text = canonicalDocumentValue(value)
    validate(documentReference + 'DocumentBody', value)
    if (text !== canonicalDocumentValue(capture.body)) throw new MedicalFailure('CONTENT_MISMATCH', '正文重读与本次保存内容不一致')
    this.savedText = text
    // 保存期间的继续编辑保留当前输入，原回执只确认对应generation。
    if (this.generation === capture.generation) this.value.value = value
    this.pending = null
  }

  replaceBaseline(saved: DocumentBody, chosen: DocumentBody): void {
    this.body
    const baseline = immutableDocumentValue(saved), edited = immutableDocumentValue(chosen)
    const baselineText = canonicalDocumentValue(baseline); canonicalDocumentValue(edited)
    validate(documentReference + 'DocumentBody', baseline); validate(documentReference + 'DocumentBody', edited)
    if (baseline.template_version !== edited.template_version) throw new MedicalFailure('SCOPE_STALE', '冲突处理正文与当前模板身份不一致')
    this.savedText = baselineText; this.value.value = baseline; this.history.commit(); this.history.clear()
    if (baselineText !== canonicalDocumentValue(edited)) { this.value.value = edited; this.history.commit() }
    this.generation++; this.pending = null
  }

  invalidate(): void { this.value.value = null; this.history.commit(); this.history.clear(); this.savedText = null; this.pending = null }
}
