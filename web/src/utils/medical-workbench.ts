import type { Project } from '@/api/medical/protocol'

/** A bounded preview, never an enterprise-wide ranking. */
export function recentWorkbenchProjects(projects: Project[]): Project[] {
  return [...projects].sort((a, b) => Date.parse(b.updated_at) - Date.parse(a.updated_at)).slice(0, 6)
}

/** Null means restricted/unavailable, not zero. Avoid partial totals disguised as complete. */
export function workbenchCount(projects: Project[], field: keyof Project['children']): string {
  if (projects.some(project => project.children[field] === null)) return '部分不可读取'
  return projects.reduce((total, project) => total + BigInt(project.children[field]!), 0n).toString()
}
