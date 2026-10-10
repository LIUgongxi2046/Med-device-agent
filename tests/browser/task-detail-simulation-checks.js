import assert from 'node:assert/strict'

// 页面字段使用当前 TK-02 响应逐项核验。
export async function verifyCurrentTaskDetail(page, { origin, project, task, otherProject }, checks) {
  const endpoint = '/api/tasks/' + task
  const target = '/projects/' + project + '/tasks/' + task + '/config'
  const reads = []
  const trackReads = request => {
    const pathname = new URL(request.url()).pathname
    if (pathname === endpoint || pathname === '/api/projects/' + project + '/tasks') reads.push(pathname)
  }
  page.on('request', trackReads)
  const waitDetail = () => page.waitForResponse(response => new URL(response.url()).pathname === endpoint)
  const section = page.locator('[data-task-detail]')
  const verify = async response => {
    assert.equal(response.status(), 200)
    assert.equal(response.request().method(), 'GET')
    assert.equal(response.request().headers().authorization, undefined)
    assert.equal(new URL(response.url()).search, '')
    const envelope = await response.json()
    const detail = envelope.data
    assert.equal(detail.task_id, task); assert.equal(detail.project_id, project)
    assert(BigInt(envelope.resource_revision) > 0n)
    await section.waitFor()
    for (const [label, value] of [
      ['项目标识', detail.project_id], ['任务标识', detail.task_id],
      ['固定任务版本', detail.task_revision], ['当前记录修订', envelope.resource_revision],
      ['责任人员', detail.owner_id], ['已确认计划版本', detail.plan_revision ?? '尚未确认计划'],
      ['固定来源快照', detail.snapshot_id ?? '尚未形成来源快照']
    ]) {
      const row = section.locator('.el-descriptions__body tr').filter({ hasText: label })
      assert.equal((await row.locator('td').last().innerText()).trim(), value)
    }
    const requirements = section.locator('.el-table__body-wrapper tbody tr')
    assert.equal(await requirements.count(), detail.missing_requirements.length)
    for (let index = 0; index < detail.missing_requirements.length; index += 1) {
      const requirement = detail.missing_requirements[index]
      const cells = await requirements.nth(index).locator('td .cell').allTextContents()
      assert.deepEqual(cells, [requirement.requirement_id, requirement.node_id ?? '未关联目录节点',
        requirement.field_id ?? '未关联字段', requirement.reason_code,
        requirement.allowed_actions.join('、') || '当前没有获准处理动作'])
    }
    assert.deepEqual(await section.locator('ul li').allTextContents(), [
      ...detail.document_ids, ...detail.active_run_ids, ...detail.waiting_item_ids
    ])
    return envelope
  }
  const initial = waitDetail()
  await page.locator('[data-task-table] .el-table__body-wrapper tbody tr').first()
    .getByRole('link', { name: '读取当前状态', exact: true }).click()
  await page.waitForURL(origin + target)
  await verify(await initial)
  checks.push('current_task_entry_reads_tk02_and_matches_complete_response')

  const refreshed = waitDetail()
  await page.reload()
  await verify(await refreshed)
  const repeated = waitDetail()
  await page.getByRole('button', { name: '重新读取任务', exact: true }).click()
  await verify(await repeated)
  checks.push('current_task_refresh_and_manual_read_obtain_current_server_revision')

  const requests = []
  const track = request => { if (new URL(request.url()).pathname === endpoint) requests.push(request.method()) }
  page.on('request', track)
  try {
    await page.goto(origin + '/projects/' + otherProject + '/tasks/' + task + '/config')
    await page.getByText('读取任务需要当前企业身份及任务读取权限', { exact: true }).waitFor()
    assert.equal(await section.count(), 0)
    assert.equal(await page.getByRole('button', { name: '重新读取任务', exact: true }).isDisabled(), true)
    assert.deepEqual(requests, [])
  } finally { page.off('request', track) }
  checks.push('current_task_project_change_clears_detail_and_checks_scope')

  const returning = waitDetail()
  await page.goto(origin + target)
  await verify(await returning)
  const listing = page.waitForResponse(response => new URL(response.url()).pathname === '/api/projects/' + project + '/tasks')
  await page.getByRole('link', { name: '返回项目任务', exact: true }).click()
  assert.equal((await listing).status(), 200)
  await page.locator('[data-task-table]').waitFor()
  assert.equal(await section.count(), 0)
  page.off('request', trackReads)
  assert.equal(reads.filter(pathname => pathname === endpoint).length, 4)
  assert.equal(reads.filter(pathname => pathname === '/api/projects/' + project + '/tasks').length, 1)
  checks.push('current_task_return_to_list_releases_detail_and_reads_current_listing')
}
