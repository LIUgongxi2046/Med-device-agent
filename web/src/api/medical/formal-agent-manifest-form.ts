import { draftCopy, draftSchemaDefinitions } from './formal-agent-drafts';
export interface ManifestChoice {
    label: string;
    value: any;
    group: string;
}
export function resolveManifestSchema(input: Record<string, any>): Record<string, any> {
    let value = { ...input };
    if (value.$ref) {
        const ref = value.$ref.split('/').pop();
        value = { ...resolveManifestSchema(draftSchemaDefinitions[ref] || {}), ...value };
        delete value.$ref;
    }
    if (value.allOf) {
        for (const part of value.allOf)
            if (part.$ref || part.type || part.minItems !== undefined)
                value = { ...value, ...resolveManifestSchema(part) };
        delete value.allOf;
    }
    return value;
}
export function emptyManifestValue(input: Record<string, any>): any {
    const s = resolveManifestSchema(input);
    if ('const' in s)
        return s.const;
    if (s.type === 'object')
        return Object.fromEntries((s.required || []).map((k: string) => [k, emptyManifestValue(s.properties[k])]));
    if (s.type === 'array')
        return [];
    if (s.type === 'boolean')
        return false;
    return '';
}
export function manifestReferenceType(input: Record<string, any>): string | null {
    const s = resolveManifestSchema(input), p = s.properties || {};
    return p.schema_ref && p.schema_sha256 ? 'SCHEMA' : p.artifact_id && p.sha256 ? 'ARTIFACT' : p.version_id && p.content_sha256 ? 'VERSION' : null;
}
export function manifestLabel(key: string): string {
    const labels: Record<string, string> = { schema_version: '契约版本', role_versions: '专业角色', skill_versions: 'Skill', tool_versions: '工具', prompt_revisions: '提示词', input_output_schema_refs: '输入输出契约绑定', template_versions: '模板', rule_versions: '规则', retrieval_profiles: '检索配置', model_config_revisions: '模型配置', limits: '运行限额', human_checkpoints: '人工检查点', runtime_manifest: '固定运行环境', release_scope: '候选适用范围', source_repository: '源码仓库', source_commit: '固定提交', source_version: '源码版本', harness_distribution: 'Harness 发行物', python_runtime: 'Python 运行时', platform: '平台', plugins: '插件', profile: '执行配置', sdk_configuration: 'SDK 配置', validators: 'AS-01—15 校验器', event_adapters: 'C07/C12 事件适配器', member_ids: '成员编号', role_ids: '企业角色编号', project_ids: '项目编号', allowed_classifications: '允许的数据分类', source_scope_policy: '固定来源范围策略' };
    return labels[key] || key.replaceAll('_', ' ');
}
export function referenceChoices(path: string, kind: string, choices: ManifestChoice[]): ManifestChoice[] {
    if (kind === 'SCHEMA')
        return choices.filter(x => x.group === 'SCHEMA');
    if (kind === 'ARTIFACT')
        return choices.filter(x => x.group === 'ARTIFACT');
    const top = path.split('.')[0], group: Record<string, string> = { role_versions: 'ROLE', skill_versions: 'SKILL', tool_versions: 'TOOL', prompt_revisions: 'PROMPT', template_versions: 'TEMPLATE', rule_versions: 'RULE', model_config_revisions: 'MODEL', retrieval_profiles: 'RETRIEVAL' };
    return choices.filter(x => kind === 'VERSION' && !['SCHEMA', 'ARTIFACT', 'MEMBER_ID', 'ROLE_ID', 'PROJECT_ID', 'TENANT_ID'].includes(x.group) && (!group[top!] || x.group === group[top!]));
}
export function selectedManifestChoice(choices: ManifestChoice[], index: string): Record<string, unknown> | null { const choice = choices[Number(index)]; return index !== '' && choice ? draftCopy(choice.value) : null; }
export function identityChoices(path: string, choices: ManifestChoice[]): ManifestChoice[] {
    const group = path.includes('member_ids') ? 'MEMBER_ID' : path.includes('role_ids') ? 'ROLE_ID' : path.includes('project_ids') ? 'PROJECT_ID' : path.endsWith('tenant_id') ? 'TENANT_ID' : null;
    if (group)
        return choices.filter(x => x.group === group);
    return choices.filter(x => (!path.includes('role_version_ids') || x.group === 'ROLE') && x.value && typeof x.value === 'object' && typeof x.value.version_id === 'string').map(x => ({ ...x, value: x.value.version_id }));
}
