import { canonicalizeEx } from 'json-canonicalize'
import { schemaIssues } from './protocol'
import { exactHasText, exactReference } from './source-exact-domain'
import type { ExactField, ExactPublication } from './source-exact-domain'

export interface SourceInputIssue { path: string; code: string }
export function parseSourceInput(text: string): { value: unknown; issues: SourceInputIssue[] } {
  try { return { value: JSON.parse(text), issues: [] } }
  catch (error) {
    if (!(error instanceof SyntaxError)) throw error
    return { value: null, issues: [{ path: '', code: 'JSON_SYNTAX' }] }
  }
}
function literalValid(value: string): boolean {
  return exactHasText(value) && Array.from(value).length <= 512
    && !Array.from(value).some(character => { const code = character.codePointAt(0)!; return code >= 0xd800 && code <= 0xdfff })
}
export function exactFieldInputIssues(value: unknown): SourceInputIssue[] {
  const issues = schemaIssues(exactReference + 'Field', value)
  if (issues.length) return issues
  const field = value as ExactField
  if (!literalValid(field.raw_value)) issues.push({ path: '/raw_value', code: 'UNICODE_TEXT' })
  if (!Number.isSafeInteger(field.start_codepoint) || !Number.isSafeInteger(field.end_codepoint)
      || field.end_codepoint <= field.start_codepoint) issues.push({ path: '/end_codepoint', code: 'INVALID_RANGE' })
  if ((field.alias_value === null) !== (field.alias_reason === null)) issues.push({ path: '/alias_reason', code: 'ALIAS_REASON_PAIR' })
  if (field.alias_value !== null) {
    if (!literalValid(field.alias_value) || field.alias_value === field.raw_value) issues.push({ path: '/alias_value', code: 'INDEPENDENT_ALIAS' })
    if (!field.alias_reason?.trim() || field.alias_reason !== field.alias_reason.trim()
        || field.alias_reason !== field.alias_reason.normalize('NFC')) issues.push({ path: '/alias_reason', code: 'NORMALIZED_REASON' })
  }
  return issues
}
export function exactPublicationInputIssues(value: unknown): SourceInputIssue[] {
  const issues = schemaIssues(exactReference + 'PublicationInput', value)
  if (issues.length) return issues
  const input = value as ExactPublication
  if (BigInt(input.expected_revision) >= 18446744073709551615n) issues.push({ path: '/expected_revision', code: 'REVISION_LIMIT' })
  if (!input.reason.trim() || input.reason !== input.reason.trim() || input.reason !== input.reason.normalize('NFC')) {
    issues.push({ path: '/reason', code: 'NORMALIZED_REASON' })
  }
  const seen = new Set<string>()
  input.fields.forEach((field, index) => {
    issues.push(...exactFieldInputIssues(field).map(issue => ({ ...issue, path: '/fields/' + index + issue.path })))
    const identity = canonicalizeEx([field.field_kind, field.raw_value, field.alias_value, field.chunk_id, field.start_codepoint, field.end_codepoint])
    if (seen.has(identity)) issues.push({ path: '/fields/' + index, code: 'DUPLICATE_FIELD' })
    seen.add(identity)
  })
  return issues
}
