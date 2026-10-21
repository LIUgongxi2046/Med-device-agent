/** Presentation of PRD §8.7 requirements, not an executable scenario contract. */
export const scenarioPlanning = [
  { id: 'REGULATION_COMPARISON', name: '法规版本比较与变化解读', short: '法规比较', purpose: '比较法规版本并说明变化及其适用影响', hint: '记录希望比较的法规、版本及关注的变化。版本适用性仍需授权人员核验。' },
  { id: 'REPORT_GENERATION', name: '报告生成', short: '报告生成', purpose: '按照独立报告规格组织输入、证据和报告内容', hint: '记录报告用途、已有资料和期望结构。报告模板与审核规则尚未确认。' },
  { id: 'TRANSLATION', name: '翻译', short: '翻译', purpose: '按照语言、术语和人工审查要求翻译获授权内容', hint: '记录原文语言、目标语言及术语关注点。此处不会翻译或发送原文。' },
  { id: 'MARKET_ANALYSIS', name: '市场分析', short: '市场分析', purpose: '按照独立分析范围和资料要求形成市场分析', hint: '记录关注市场、分析范围和已有资料。此处不会检索数据或生成市场结论。' },
  { id: 'MULTIMODAL_QA', name: '多模态问答', short: '多模态问答', purpose: '按照输入类型、知识范围和证据要求回答问题', hint: '记录问题、计划使用的输入类型及知识范围。此处不上传文件或调用模型。' }
] as const
export type PlanningId = typeof scenarioPlanning[number]['id']
export const planningFields = [
  { key: 'roles', label: '使用角色', placeholder: '描述期望使用者及其业务职责' },
  { key: 'inputs', label: '输入要求', placeholder: '描述已有资料、待补资料及输入范围，不粘贴敏感原文' },
  { key: 'knowledge', label: '适用知识', placeholder: '记录希望使用的知识范围及待核验依据' },
  { key: 'rules', label: '任务规则', placeholder: '记录需要确认的业务规则，不作为执行指令' },
  { key: 'outputs', label: '输出要求', placeholder: '记录期望产物及用途，不代表已经生成' },
  { key: 'review', label: '人工审核', placeholder: '记录需要确认的审核角色与责任' },
  { key: 'failures', label: '失败状态', placeholder: '记录需要确认的异常情况与人工处置要求' },
  { key: 'samples', label: '验收样本', placeholder: '描述拟用样本及核验方式，不上传样本' }
] as const
export type DraftField = typeof planningFields[number]['key']
export interface PlanningDraft { title: string; notes: Record<DraftField, string> }
export function emptyPlanningDraft(): PlanningDraft {
  return { title: '', notes: { roles: '', inputs: '', knowledge: '', rules: '', outputs: '', review: '', failures: '', samples: '' } }
}
export function validatePlanningDraft(draft: PlanningDraft): string[] {
  const errors: string[] = []
  if (!draft.title.trim()) errors.push('请填写本地准备记录名称。')
  if (draft.title.length > 80) errors.push('记录名称不能超过 80 个字符。')
  if (!draft.notes.inputs.trim()) errors.push('请填写输入准备说明，以便预览记录。')
  if (planningFields.some(field => draft.notes[field.key].length > 1000)) errors.push('每项准备说明不能超过 1000 个字符。')
  return errors
}
