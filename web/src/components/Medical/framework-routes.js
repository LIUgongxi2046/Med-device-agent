const roots = ['/system', '/monitor', '/tool']
export function isMedicalFrameworkPath(path) {
  return typeof path === 'string' && roots.some(root => path === root || path.startsWith(root + '/'))
}

// Only a fresh server navigation response is an authority source. Never use a
// hard-coded management menu or the client role list to manufacture routes.
export function prepareMedicalFrameworkRoutes(records, existingRoutes, hasView) {
  const names = new Set(existingRoutes.map(route => route.name).filter(Boolean))
  const paths = new Set(existingRoutes.map(route => route.path))
  function visit(record, parent = '') {
    if (!record || typeof record !== 'object' || Array.isArray(record)) return null
    const raw = record.path
    if (typeof raw !== 'string' || !/^\/?[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*$/.test(raw)) return null
    const path = raw.startsWith('/') ? raw : parent + '/' + raw
    if (!isMedicalFrameworkPath(path) || parent && !path.startsWith(parent + '/') || !parent && !raw.startsWith('/')) return null
    if (typeof record.name !== 'string' || !record.name || names.has(record.name) || paths.has(path) || record.alias !== undefined) return null
    if (typeof record.component !== 'string' || !['Layout', 'ParentView'].includes(record.component) && !hasView(record.component)) return null
    if (record.component === 'Layout' && parent) return null
    names.add(record.name); paths.add(path)
    const children = Array.isArray(record.children) ? record.children.map(child => visit(child, path)).filter(Boolean) : []
    if (record.children?.length && !children.length) return null
    const meta = record.meta && typeof record.meta === 'object' ? record.meta : {}
    // Deliberately do not forward links, arbitrary redirects, aliases, route
    // guards, or public markers supplied in menu presentation metadata.
    return {
      path: raw, name: record.name, component: record.component,
      hidden: record.hidden === true, alwaysShow: record.alwaysShow === true,
      meta: { title: typeof meta.title === 'string' ? meta.title : record.name,
        icon: typeof meta.icon === 'string' ? meta.icon : 'system',
        medical: true, medicalFramework: true, public: false, noCache: true },
      ...(children.length ? { children } : {})
    }
  }
  return Array.isArray(records) ? records.map(record => visit(record)).filter(Boolean) : []
}

export function frameworkRouteNames(records) {
  return records.flatMap(record => [record.name, ...frameworkRouteNames(record.children ?? [])])
}

export function createMedicalFrameworkRegistry(router) {
  let removals = []; let authorized = new Set()
  return {
    clear() { removals.forEach(remove => remove()); removals = []; authorized = new Set() },
    replace(records) {
      this.clear()
      try {
        records.forEach(record => removals.push(router.addRoute(record)))
        authorized = new Set(frameworkRouteNames(records))
      } catch (error) { this.clear(); throw error }
    },
    has(name) { return typeof name === 'string' && authorized.has(name) }
  }
}
