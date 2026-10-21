import type { Basis } from './protocol';
import type { EvaluationRead } from './evaluation-domain';
import type { BudgetAccount } from './budgets';
import type { ContextSummary, ContextView } from './formal-evaluation-context';
export const sameN01Basis = (a: Basis, b: Basis): boolean => a.version_id === b.version_id && a.content_sha256 === b.content_sha256;
/** Counts always describe the entire registered definition, never a user-selected subset. */
export function n01OriginalCoverage(view: EvaluationRead) {
    const request = view.definition.request;
    return { samples: request.samples.length, candidates: request.candidates.length, dimensions: request.required_dimensions.length,
        repeats: request.variance_policy.repeat_count, scoring_cells: request.samples.length * request.candidates.length * request.required_dimensions.length * request.variance_policy.repeat_count };
}
export function n01ContextChoices(items: ContextSummary[], evaluation: EvaluationRead, candidate: Basis | null): ContextSummary[] {
    const project = evaluation.definition.request.project_id;
    if (!project || !candidate || !evaluation.definition.request.candidates.some(item => sameN01Basis(item, candidate)))
        return [];
    return items.filter(item => item.status === 'APPROVED' && item.project_id === project && sameN01Basis(item.candidate, candidate));
}
/** The full approved record must still match the selected directory entry. No status is inferred. */
export function n01ContextIssue(detail: ContextView, summary: ContextSummary, evaluation: EvaluationRead, candidate: Basis): string {
    if (detail.status !== 'APPROVED' || detail.revision !== summary.revision || !sameN01Basis(detail.binding, summary.binding)
        || detail.fixed.definition.project_id !== evaluation.definition.request.project_id
        || !sameN01Basis(detail.fixed.definition.candidate, candidate) || !sameN01Basis(detail.fixed.definition.execution_policy, summary.execution_policy)) {
        return '上下文绑定的固定版本、状态或范围已变化，请重新读取目录并明确选择。';
    }
    return '';
}
export function n01BudgetChoices(items: BudgetAccount[], tenant: string, project: string | null): BudgetAccount[] {
    return items.filter(item => item.tenant_id === tenant && item.project_id === project && item.allowed_actions.includes('budget.read'));
}
export function n01BudgetBasis(value: BudgetAccount): Basis { return { version_id: value.budget_revision, content_sha256: value.content_sha256 }; }
export function n01EvaluationIssue(view: EvaluationRead): string {
    if (!view.definition.request.project_id)
        return 'N01 计划需要真实项目及固定项目属性；企业级批次当前不能准备此计划。';
    if (view.definition.request.environment !== 'PRODUCTION')
        return '此入口仅准备正式来源批次的 N01 计划；当前批次为仿真来源。';
    if (view.result)
        return '该批次已经固定评测结果，不能再发起新的计划准备。';
    if (!view.definition.request.samples.length || !view.definition.request.candidates.length)
        return '完整定义缺少样本或候选，不能准备计划。';
    return '';
}
