import assert from 'node:assert/strict'

export async function checkExactActions({ page, origin, projectId, sourceVersion, pick, check }) {
  const checks = [], fieldPath = '/library/versions/' + sourceVersion + '/exact-fields'
  const chooseScope = async () => {
    const sourcePromise = page.waitForResponse(response => response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/source-versions/' + sourceVersion)
    await page.goto(origin + fieldPath)
    await page.getByRole('heading', { name: '结构化字段核准', exact: true }).waitFor()
    const response = await sourcePromise; assert.equal(response.status(), 200)
    const source = (await response.json()).data
    assert.equal(source.rights_status, 'APPROVED')
    const review = source.reviews.find(item => item.approval_status === 'APPROVED' && item.index_status === 'READY')
    assert(review); assert(source.bindings.some(item => item.project_id === projectId && item.applicability === 'APPLICABLE'))
    await pick('字段适用项目', projectId); await pick('字段解析及索引', review.parse_version + ' / ' + review.index_version)
  }
  const readManifest = async () => {
    const region = page.getByRole('region', { name: '固定字段清单', exact: true })
    await region.waitFor(); return JSON.parse(await region.locator('pre').innerText())
  }
  const publish = async reason => {
    await page.getByRole('textbox', { name: '字段核准理由', exact: true }).fill(reason)
    await check('已核查全部字段、人工别名和固定原文位置')
    const responsePromise = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/exact-fields'))
    await page.getByRole('button', { name: '核准完整字段清单', exact: true }).click()
    const response = await responsePromise; assert.equal(response.status(), 201)
    const receipt = (await response.json()).data
    await page.getByText('字段核准回执已经重新读取：' + receipt.operation_id + '；' + receipt.field_revision, { exact: true }).waitFor()
    const manifest = await readManifest(); assert.deepEqual(manifest, receipt.manifest)
    assert.equal(await page.evaluate(() => Object.values(sessionStorage).map(value => JSON.parse(value)).filter(value => value?.operation === 'KN-EXACT-PUBLISH').length), 0)
    return receipt
  }
  const search = async (kind, query, expected) => {
    await page.goto(origin + '/library/search/exact')
    await page.getByRole('heading', { name: '结构化字段等值检索', exact: true }).waitFor()
    const projectInput = page.getByRole('combobox', { name: '等值检索项目', exact: true })
    await page.waitForFunction(element => !element.disabled, await projectInput.elementHandle())
    await projectInput.focus(); await projectInput.press('ArrowDown')
    await page.getByRole('option').filter({ hasText: 'SIMULATION' }).click()
    await pick('等值检索字段类别', kind)
    await page.getByRole('textbox', { name: '完整等值查询', exact: true }).fill(query)
    const resultPromise = page.waitForResponse(response => response.request().method() === 'GET' && /^\/api\/retrievals\/exact\/[^/]+$/.test(new URL(response.url()).pathname))
    await page.getByRole('button', { name: '检索核准字段', exact: true }).click()
    const response = await resultPromise; assert.equal(response.status(), 200)
    const value = (await response.json()).data
    await page.getByRole('region', { name: '固定等值检索结果', exact: true }).waitFor()
    assert.equal(value.project_id, projectId); assert.equal(value.query, query); assert.equal(value.status, expected)
    assert.equal(new URL(page.url()).searchParams.has('query'), false)
    assert.equal(await page.evaluate(() => Object.values(sessionStorage).map(value => JSON.parse(value)).filter(value => value?.operation === 'KN-14-EXACT').length), 0)
    return value
  }
  await chooseScope()
  await page.getByRole('button', { name: '读取固定解析片段', exact: true }).click()
  const chunks = page.getByRole('combobox', { name: '字段原文片段', exact: true })
  await page.waitForFunction(element => !element.disabled, await chunks.elementHandle())
  await chunks.focus(); await chunks.press('ArrowDown'); await page.getByRole('option').click()
  const raw = await page.getByLabel('字段完整原文', { exact: true }).innerText()
  assert(raw.startsWith('SIMULATION'))
  await page.getByRole('spinbutton').nth(0).fill('0'); await page.getByRole('spinbutton').nth(1).fill('10')
  await page.getByRole('spinbutton').nth(1).blur()
  assert.equal(await page.getByLabel('选定字段原值', { exact: true }).innerText(), 'SIMULATION')
  await page.getByRole('button', { name: '加入完整字段清单', exact: true }).click()
  await page.getByRole('textbox', { name: '字段别名 1', exact: true }).fill('SIMULATION-A001')
  await page.getByRole('textbox', { name: '别名审核理由 1', exact: true }).fill('SIMULATION 第一项人工型号别名核查')
  await page.getByRole('button', { name: '加入完整字段清单', exact: true }).click()
  await page.getByRole('textbox', { name: '字段别名 2', exact: true }).fill('SIMULATION-A002')
  await page.getByRole('textbox', { name: '别名审核理由 2', exact: true }).fill('SIMULATION 第二项人工型号别名核查')
  for (const kind of ['产品技术要求编号', '文件文号', '标准编号', '标准年代号']) {
    await pick('结构化字段类别', kind); await page.getByRole('button', { name: '加入完整字段清单', exact: true }).click()
  }
  await page.getByRole('textbox', { name: '原字段清单修订', exact: true }).fill('0')
  const first = await publish('SIMULATION 五类完整字段核准')
  assert.equal(first.field_count, 6); assert.equal(first.revision, '1')
  console.log(JSON.stringify({ stage: 'EXACT_PUBLICATION_VERIFIED', revision: first.revision }))
  checks.push('actual_five_field_kinds_and_independent_reviewed_aliases_published_and_reopened')
  const original = await search('器械型号', 'SIMULATION', 'FOUND')
  assert.equal(original.total, '1'); assert.equal(original.items[0].matches.length, 2)
  assert.equal(original.items[0].field_revision, first.field_revision)
  const historical = page.url()
  console.log(JSON.stringify({ stage: 'EXACT_ORIGINAL_QUERY_VERIFIED', matches: original.items[0].matches.length }))
  checks.push('original_values_merge_complete_matches_with_fixed_field_and_source_identity')
  await page.getByRole('button', { name: '读取固定原文位置', exact: true }).click()
  await page.getByRole('heading', { name: '资料版本与人工核准', exact: true }).waitFor()
  await page.getByText(raw, { exact: true }).waitFor()
  assert.equal(new URL(page.url()).searchParams.get('chunk_id'), original.items[0].chunk_id)
  console.log(JSON.stringify({ stage: 'EXACT_FIXED_SOURCE_LOCATION_VERIFIED' }))
  checks.push('actual_full_original_excerpt_opens_the_verified_fixed_source_location')
  await chooseScope(); await page.getByRole('button', { name: '读取完整字段清单', exact: true }).click()
  assert.deepEqual(await readManifest(), first.manifest)
  await page.getByRole('button', { name: '使用当前读取清单修订', exact: true }).click()
  assert.equal(await page.getByRole('textbox', { name: '原字段清单修订', exact: true }).inputValue(), '1')
  await page.getByRole('textbox', { name: '字段别名 1', exact: true }).fill('SIMULATION-A001-UPDATED')
  const second = await publish('SIMULATION 完整字段修订及别名核查')
  assert.equal(second.revision, '2'); assert.notEqual(second.field_revision, first.field_revision)
  console.log(JSON.stringify({ stage: 'EXACT_REVISION_VERIFIED', revision: second.revision }))
  checks.push('current_complete_manifest_revision_and_full_original_basis_used_for_cas_update')
  await page.getByRole('textbox', { name: '固定字段版本', exact: true }).fill(first.field_revision)
  await page.getByRole('button', { name: '读取完整字段清单', exact: true }).click()
  await page.getByText('字段版本：' + first.field_revision, { exact: false }).waitFor()
  assert.deepEqual(await readManifest(), first.manifest)
  checks.push('historical_fixed_field_manifest_preserves_original_values_aliases_and_digest')
  const alias = await search('器械型号', 'SIMULATION-A001-UPDATED', 'FOUND')
  assert.equal(alias.items[0].matches.length, 1); assert.equal(alias.items[0].field_revision, second.field_revision)
  checks.push('new_reviewed_alias_uses_current_field_revision_and_exact_value')
  for (const literal of ['simulation', ' SIMULATION', 'SIMULATION-A001']) await search('器械型号', literal, 'EMPTY')
  checks.push('case_whitespace_and_previous_alias_queries_return_actual_empty_results')
  for (const kind of ['产品技术要求编号', '文件文号', '标准编号', '标准年代号']) {
    const value = await search(kind, 'SIMULATION', 'FOUND'); assert.equal(value.items[0].matches.length, 1)
  }
  checks.push('all_five_structured_field_kinds_have_actual_equality_results')
  const historyPromise = page.waitForResponse(response => response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/retrievals/exact/' + original.retrieval_id)
  await page.goto(historical)
  const historyResponse = await historyPromise; assert.equal(historyResponse.status(), 200)
  const history = (await historyResponse.json()).data
  assert.deepEqual(history, original); await page.getByRole('region', { name: '固定等值检索结果', exact: true }).waitFor()
  checks.push('original_retrieval_history_keeps_complete_original_field_revision_after_update')
  await chooseScope(); await page.getByRole('button', { name: '读取完整字段清单', exact: true }).click(); await readManifest()
  await page.getByRole('button', { name: '使用当前读取清单修订', exact: true }).click()
  await page.getByRole('textbox', { name: '字段核准理由', exact: true }).fill('SIMULATION 原字段请求恢复')
  await check('已核查全部字段、人工别名和固定原文位置')
  const requestPromise = page.waitForRequest(request => request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/exact-fields'))
  await page.getByRole('button', { name: '核准完整字段清单', exact: true }).click()
  const request = await requestPromise
  await page.goto(origin + '/library'); await page.goto(origin + fieldPath)
  await page.waitForFunction(() => ['结构化字段核准', '重新核查业务会话'].includes(document.querySelector('main h1')?.textContent ?? ''), undefined, { timeout: 120000 })
  if (new URL(page.url()).pathname === '/session-recovery') await page.getByRole('button', { name: '重新读取会话并继续页面', exact: true }).click()
  await page.getByRole('heading', { name: '结构化字段核准', exact: true }).waitFor()
  await page.getByRole('region', { name: '原字段核准请求', exact: true }).waitFor()
  const saved = await page.evaluate(() => Object.values(sessionStorage).map(value => JSON.parse(value)).find(value => value?.operation === 'KN-EXACT-PUBLISH'))
  assert.equal(saved.key, request.headers()['idempotency-key'])
  assert.equal(JSON.stringify(saved).includes('SIMULATION 原字段请求恢复'), false)
  await page.getByRole('button', { name: '查询原字段核准请求', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('[aria-label="固定字段清单"]') !== null || document.body.textContent.includes('原操作查询：NOT_FOUND'))
  if (await page.getByText('原操作查询：NOT_FOUND', { exact: true }).count()) {
    await page.locator('[aria-label="原字段核准请求"] summary').click()
    await page.getByRole('textbox', { name: '原字段核准完整JSON', exact: true }).fill(request.postData())
    await page.getByRole('button', { name: '重新发送原字段核准请求', exact: true }).click()
  }
  const recovered = await readManifest(); assert.equal(recovered.revision, '3'); assert.deepEqual(recovered.request, request.postDataJSON())
  assert.equal(await page.getByRole('region', { name: '原字段核准请求', exact: true }).count(), 0)
  checks.push('navigation_cancellation_recovers_same_original_field_request_and_full_manifest')
  await page.goto(origin + '/library/versions/' + sourceVersion + '/rights')
  await page.getByRole('heading', { name: '来源权利审查', exact: true }).waitFor()
  return { checks, historical, revisions: [first.field_revision, second.field_revision, recovered.field_revision] }
}

export async function checkExactRevocation(page, historical) {
  const id = new URL(historical).searchParams.get('retrieval_id')
  const deniedPromise = page.waitForResponse(response => response.request().method() === 'GET' && new URL(response.url()).pathname === '/api/retrievals/exact/' + id)
  await page.goto(historical)
  const denied = await deniedPromise; assert.equal(denied.status(), 403); assert.equal((await denied.json()).code, 'ACCESS_DENIED')
  await page.getByRole('alert').waitFor()
  assert.equal(await page.getByRole('region', { name: '固定等值检索结果', exact: true }).count(), 0)
}
