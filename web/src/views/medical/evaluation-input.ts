import { schemaIssues } from '@/api/medical/protocol'
import { evaluationSchema } from '@/api/medical/evaluation-domain'
import type { EvaluationAppend, EvaluationCreate } from '@/api/medical/evaluation-domain'
import { projectTextIssue } from '@/api/medical/project-text'
import { ruleConstantJsonIssue } from '@/api/medical/rule-values'

/** User-input preflight only: never dispatches medical-failure or establishes artifact integrity. */
export function evaluationJsonInputIssue(text: string): string {
  if (text.length > 2_000_000) return '定义文本过长，请缩小评测批次'
  return ruleConstantJsonIssue(text)
}
export function evaluationCreateInputIssue(input: unknown): string {
  const issues = schemaIssues(evaluationSchema + 'Create', input)
  if (issues.length) return '请补全评测定义并检查字段格式：' + (issues[0]!.path || '必填字段')
  const body = input as EvaluationCreate
  if (projectTextIssue(body.reason, 2000)) return '请填写规范的登记理由'
  if (new Set(body.samples.map(item => item.sample_id)).size !== body.samples.length) return '样本标识不能重复'
  if (new Set(body.candidates.map(item => item.version_id)).size !== body.candidates.length) return '候选固定版本不能重复'
  if (body.samples.length * body.candidates.length * body.required_dimensions.length * body.variance_policy.repeat_count > 10_000) return '固定评分矩阵最多 10000 个单元'
  const families = new Map<string, string>(), inputs = new Map<string, string>()
  for (const sample of body.samples) {
    if (families.has(sample.source_family) && families.get(sample.source_family) !== sample.split) return '同一来源家族不能跨开发、调优与验收分组'
    if (inputs.has(sample.input_ref.version_id) && inputs.get(sample.input_ref.version_id) !== sample.source_family) return '同一输入不能标记为不同来源家族'
    families.set(sample.source_family, sample.split); inputs.set(sample.input_ref.version_id, sample.source_family)
    if (new Set(sample.expectations.map(item => item.dimension)).size !== sample.expectations.length) return '同一样本的预期维度不能重复'
    if (sample.expectations.some(item => item.dimension !== 'EVAL-11' && item.control_item_ids.length)) return '合法对照项目仅适用于 EVAL-11'
  }
  return ''
}

export function evaluationScoreInputIssue(input: unknown): string {
  const issues = schemaIssues(evaluationSchema + 'Append', input)
  if (issues.length) return '请补全评分记录并检查字段格式：' + (issues[0]!.path || '必填字段')
  const body = input as EvaluationAppend
  if (projectTextIssue(body.reason, 2000)) return '请填写规范的评分或修订理由'
  if ((body.numerator === null) !== (body.denominator === null) || (body.control_numerator === null) !== (body.control_denominator === null)
      || body.status === 'SCORED' && (body.numerator === null || body.output_ref === null || body.evidence_refs.length === 0)
      || body.status !== 'SCORED' && (body.numerator !== null || body.control_numerator !== null)
      || body.numerator !== null && BigInt(body.numerator) > BigInt(body.denominator!)
      || body.control_numerator !== null && BigInt(body.control_numerator) > BigInt(body.control_denominator!)) return '评分须填写实际输出和依据，成对填写计数且分子不能大于分母；未知或阻断不填分数'
  return ''
}
