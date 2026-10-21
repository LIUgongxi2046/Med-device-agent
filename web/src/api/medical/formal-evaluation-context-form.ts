import { capabilityGuard, capabilityCanonical as canonical, detached } from './formal-capability-domain'
import { fail } from './protocol'
import type { Basis } from './protocol'
import { sourceDigest } from './content-digest'
import { readDraft } from './formal-agent-drafts'
import type { DraftSummary } from './formal-agent-drafts'
import { readPolicy } from './execution-policies'
import type { PolicyView } from './execution-policy-domain'
import type { ContextAuthor, ContextReader, ContextSchema, ContextNode } from './formal-evaluation-context'
export interface ContextManifest { role_versions: Basis[]; prompt_revisions: Basis[]; tool_versions: Basis[]; input_output_schema_refs: {consumer_version_id:string;direction:'INPUT'|'OUTPUT';schema:ContextSchema}[] }
export interface ContextCandidate { basis: Basis; record_id: string; name: string; status: string; project_id: string | null; created_by: string; execution_policy: Basis; manifest: ContextManifest }
export interface ContextTool { basis: Basis; record_id: string; project_id: string | null; source_artifact: Basis; input_schema: ContextSchema; output_schema: ContextSchema }
export interface NodeSelection { node_kind: string; role: Basis; prompt_id: string; tool_id: string }
export const sameBasis = (a: Basis, b: Basis) => a.version_id === b.version_id && a.content_sha256 === b.content_sha256
export function memberBasis(values: Basis[], id: string): Basis | null { const found = values.filter(x=>x.version_id===id); return found.length === 1 ? found[0]! : null }
export async function loadContextCandidate(reader: ContextReader, summary: DraftSummary, project: string, signal: AbortSignal): Promise<{candidate:ContextCandidate;policy:PolicyView}> {
 const g = capabilityGuard(reader,signal)
 if (summary.kind !== 'AGENT' || summary.project_id !== null && summary.project_id !== project) fail('CONTENT_MISMATCH','候选不属于当前项目范围')
 const view = await readDraft(reader,'AGENT',summary.record_id,signal); g.assert(); const f = view.fixed, hash = await sourceDigest(f); g.assert()
 if (f.version_id !== summary.version_id || hash !== summary.content_sha256 || f.project_id !== summary.project_id || f.created_by !== summary.created_by) fail('CONTENT_MISMATCH','候选目录与完整固定内容不一致')
 const policy = await readPolicy(reader,f.execution_policy.version_id,signal); g.assert()
 if (policy.fixed.definition_sha256 !== f.execution_policy.content_sha256 || policy.fixed.definition.project_id !== null && policy.fixed.definition.project_id !== project) fail('CONTENT_MISMATCH','候选与固定策略摘要或范围不一致')
 return {candidate:detached({basis:{version_id:f.version_id,content_sha256:hash},record_id:summary.record_id,name:f.name,status:view.status,project_id:f.project_id,created_by:f.created_by,execution_policy:f.execution_policy,manifest:f.manifest}),policy}
}
export async function loadContextTool(reader: ContextReader, summary: DraftSummary, candidate: ContextCandidate, project: string, signal: AbortSignal): Promise<ContextTool> {
 const g = capabilityGuard(reader,signal), basis = memberBasis(candidate.manifest.tool_versions,summary.version_id)
 if (!basis || summary.kind !== 'TOOL' || basis.content_sha256 !== summary.content_sha256 || summary.project_id !== null && summary.project_id !== project) fail('CONTENT_MISMATCH','工具不在候选的固定依赖范围内')
 const view = await readDraft(reader,'TOOL',summary.record_id,signal); g.assert(); const body = view.content.request
 if (view.resource_id !== basis.version_id || view.content_sha256 !== basis.content_sha256 || body.project_id !== summary.project_id) fail('CONTENT_MISMATCH','工具固定声明与候选成员不一致')
 const result: ContextTool = {basis:detached(basis),record_id:summary.record_id,project_id:body.project_id,source_artifact:body.source_artifact,input_schema:body.input_schema_ref,output_schema:body.output_schema_ref}
 for (const [direction,schema] of [['INPUT',result.input_schema],['OUTPUT',result.output_schema]] as const) {
  const bindings = candidate.manifest.input_output_schema_refs.filter(x=>x.consumer_version_id===basis.version_id&&x.direction===direction)
  if (bindings.length !== 1 || canonical(bindings[0]!.schema) !== canonical(schema)) fail('CONTENT_MISMATCH','工具输入输出契约与候选清单不一致')
 }
 return detached(result)
}
export function modelNodeSelections(candidate: ContextCandidate, policy: PolicyView): NodeSelection[] {
 if (!sameBasis(candidate.execution_policy,{version_id:policy.fixed.version_id,content_sha256:policy.fixed.definition_sha256})) throw new Error('候选与执行策略不是同一个固定版本')
 const nodes = policy.fixed.definition.bindings.filter(x=>x.model_version!==null)
 if (!nodes.length) throw new Error('所选策略没有模型节点，无法编写模型上下文绑定')
 if (new Set(nodes.map(x=>x.node_kind)).size !== nodes.length) throw new Error('执行策略中出现重复模型节点')
 return nodes.map(x=>{if (candidate.manifest.role_versions.filter(v=>sameBasis(v,x.role_version)).length !== 1) throw new Error('策略角色不在候选的固定角色清单中'); return {node_kind:x.node_kind,role:detached(x.role_version),prompt_id:'',tool_id:''}})
}
export function buildContextAuthor(project: string, candidate: ContextCandidate | null, policy: PolicyView | null, selections: NodeSelection[], tools: ContextTool[], reason: string): ContextAuthor {
 if (!project || !candidate || !policy) throw new Error('请先选择项目并读取真实 Agent 候选与执行策略')
 if (candidate.status === 'DISABLED' || candidate.project_id !== null && candidate.project_id !== project || policy.status !== 'APPROVED') throw new Error('候选必须适用于当前项目，且其固定执行策略已获独立批准')
 const expected = modelNodeSelections(candidate,policy)
 if (expected.length !== selections.length || new Set(selections.map(x=>x.node_kind)).size !== selections.length) throw new Error('必须为全部模型节点逐一填写绑定')
 const nodes: ContextNode[] = selections.map(selection=>{
  const node = expected.find(n=>n.node_kind===selection.node_kind), prompt = memberBasis(candidate.manifest.prompt_revisions,selection.prompt_id), tool = tools.find(t=>t.basis.version_id===selection.tool_id)
  if (!node || !sameBasis(node.role,selection.role) || !prompt || !tool || !candidate.manifest.tool_versions.some(v=>sameBasis(v,tool.basis))) throw new Error('请为每个节点明确选择候选内的提示词，并读取所选工具固定声明')
  for (const [direction,schema] of [['INPUT',tool.input_schema],['OUTPUT',tool.output_schema]] as const) { const declared = candidate.manifest.input_output_schema_refs.filter(x=>x.consumer_version_id===tool.basis.version_id&&x.direction===direction); if (declared.length!==1 || canonical(declared[0]!.schema)!==canonical(schema)) throw new Error('所选工具输入输出契约与候选清单不一致') }
  return {node_kind:selection.node_kind,role:detached(node.role),prompt:detached(prompt),context_assembler:detached(tool.basis),input_schema:detached(tool.input_schema),output_schema:detached(tool.output_schema)}
 })
 return {schema_version:'1.0.0',definition:{schema_version:'1.0.0',project_id:project,candidate:detached(candidate.basis),execution_policy:detached(candidate.execution_policy),node_bindings:nodes},reason}
}
