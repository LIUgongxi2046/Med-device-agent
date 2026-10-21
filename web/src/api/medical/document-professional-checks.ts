import type { AssemblyManifest, ReviewFindings, IssueProposal } from '../../../../contracts/typescript/src/artifacts'
import type { CheckReceipt, CheckResultRequest, DocumentSnapshot } from '../../../../contracts/typescript/src/interfaces'
/** Local presentation inputs only, not a server DTO or authority verifier. */
export interface ProfessionalChecksInput {
  snapshot: DocumentSnapshot
  dirty?: boolean | undefined
  isCurrent?: boolean | undefined
  active?: boolean | undefined
  findings?: ReviewFindings | null | undefined
  assembly?: AssemblyManifest | null | undefined
  checkRequest?: CheckResultRequest | null | undefined
  checkReceipt?: CheckReceipt | null | undefined
}
export function professionalChecksModel(input: ProfessionalChecksInput) {
  const { snapshot: s, findings: f, assembly: a, checkRequest: request, checkReceipt: receipt } = input
  const findingsMatch = !!f && f.target_kind === 'DOCUMENT' && f.target_revision === s.document_revision && f.target_sha256 === s.body.sha256
  const assemblyMatch = !!a && a.target_kind === 'DOCUMENT' && a.document_revision === s.document_revision
  const requestMatch = !!request && request.target_kind === 'DOCUMENT' && request.target_revision === s.document_revision
  const pairedReceipt = requestMatch && receipt?.check_id === request?.check_id ? receipt : null
  const missingClaims = f?.required_claim_ids.filter(id => !f.claim_checks.some(check => check.claim_id === id)) ?? []
  const missingRules = f?.required_rule_ids.filter(id => !f.rule_checks.some(check => check.rule_id === id)) ?? []
  const warnings = [...s.invalidation_reasons]
  if (input.dirty) warnings.push('本地正文尚未保存；检查仅针对已保存版本。')
  if (input.isCurrent !== true) warnings.push('当前正文版本尚未核对或已过期。')
  if (input.active === false) warnings.push('工作区未激活，定位操作不可用。')
  if (f && !findingsMatch) warnings.push('独立检查产物属于候选、其他正文版本或摘要不一致，不能作为当前正文依据。')
  if (a && !assemblyMatch) warnings.push('总稿清单属于候选集或其他正文版本，不能作为当前总稿依据。')
  if (request && !requestMatch) warnings.push('检查请求不属于当前正文；不能用候选检查覆盖正文复验。')
  if (receipt && !pairedReceipt) warnings.push('检查回执没有匹配的当前正文请求，不能关联到此版本。')
  return { findingsMatch, assemblyMatch, requestMatch, pairedReceipt, missingClaims, missingRules, warnings: [...new Set(warnings)],
    issues: f?.issue_proposals ?? [],
    canLocate: findingsMatch && input.active !== false && input.isCurrent === true && !input.dirty,
    canCheck: false as const, canConfirm: false as const, canSign: false as const,
    currentValidity: 'UNKNOWN' as const }
}
export function filterProfessionalIssues(issues: IssueProposal[], severity: string, query: string) {
  const text = query.trim().toLocaleLowerCase()
  return issues.filter(issue => (severity === 'ALL' || issue.severity_code === severity) && (!text || [issue.message, issue.code, issue.rule_id, issue.anchor.node_id].some(value => value.toLocaleLowerCase().includes(text))))
}
