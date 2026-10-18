import { schemaIssues } from './protocol'
import { projectTextIssue } from './project-write'
import type { PlanVersionInput } from './task-plans'

export interface PlanInputIssue { path: string; message: string }

/** Only for the unsent editable draft. Strict API/read/recovery validation remains unchanged. */
export function planInputIssues(input: unknown): PlanInputIssue[] {
  const shape = schemaIssues('urn:medical:tasks:1.0.0#/$defs/PlanVersionInput', input)
  if (shape.length) return shape.map(issue => ({ path: issue.path, message: '请核查此字段的必填内容、格式或范围' }))
  const body = input as PlanVersionInput
  const issues: PlanInputIssue[] = []
  const add = (path: string, message: string) => issues.push({ path, message })
  if (projectTextIssue(body.reason, 2000)) add('/reason', '请填写规范的计划理由，不含首尾空白、控制字符或非 NFC 文本')
  const content = body.content
  const nodes = new Set(content.requirement_node_ids)
  const covered = new Set<string>()
  const sections = new Map(content.sections.map(section => [section.section_id, section]))
  if (!nodes.size) add('/content/requirement_node_ids', '计划需要完整目录节点')
  if (sections.size !== content.sections.length) add('/content/sections', '章节标识不能重复')
  content.sections.forEach((section, index) => {
    const path = '/content/sections/' + index
    if (projectTextIssue(section.goal, Number.MAX_SAFE_INTEGER)) add(path + '/goal', '请填写规范的章节目标，不含首尾空白、控制字符或非 NFC 文本')
    if (!section.requirement_node_ids.length) add(path + '/requirement_node_ids', '章节至少需要一个目录节点')
    for (const node of section.requirement_node_ids) {
      if (!nodes.has(node) || covered.has(node)) add(path + '/requirement_node_ids', '目录节点必须属于本计划，且仅由一个章节覆盖')
      covered.add(node)
    }
    if (section.depends_on_section_ids.some(id => id === section.section_id || !sections.has(id))) {
      add(path + '/depends_on_section_ids', '依赖必须指向当前计划内其他章节；请处理删除后遗留的引用')
    }
  })
  if (covered.size !== nodes.size) add('/content/sections', '仍有目录节点尚未分配章节')
  const questionKeys = new Set(content.questions.map(question => question.question_key))
  const seenKeys = new Set<string>()
  content.questions.forEach((question, index) => {
    const path = '/content/questions/' + index
    if (seenKeys.has(question.question_key)) add(path + '/question_key', '研究问题标识重复，请保持每个问题身份唯一')
    seenKeys.add(question.question_key)
    const section = sections.get(question.section_id)
    if (!section) add(path + '/section_id', '请选择当前计划内的章节；原章节可能已被移除')
    if (projectTextIssue(question.question, Number.MAX_SAFE_INTEGER)) add(path + '/question', '请填写规范的研究问题，不含首尾空白、控制字符或非 NFC 文本')
    if (section && question.requirement_ids.some(id => !section.requirement_node_ids.includes(id))) {
      add(path + '/requirement_ids', '问题目录要求必须属于所选章节')
    }
    if (question.depends_on_question_keys.some(key => key === question.question_key || !questionKeys.has(key))) {
      add(path + '/depends_on_question_keys', '问题依赖必须指向当前计划内其他问题；请处理改名或删除后遗留的引用')
    }
  })
  return issues
}
