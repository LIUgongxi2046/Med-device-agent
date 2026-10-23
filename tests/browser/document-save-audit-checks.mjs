import assert from 'node:assert/strict'
import { readFileSync, writeFileSync } from 'node:fs'
import { chromium } from 'playwright'

const origin = 'http://127.0.0.1:18083'
const owner = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/owner-credentials.json', import.meta.url)))
const seed = JSON.parse(readFileSync(new URL('../../.implementation-work/runtime/acceptance-workspace-seed.json', import.meta.url)))
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const context = await browser.newContext()
const proof = { verified_at: new Date().toISOString(), checks: [], failures: [], screenshots: false }
const readSaved = process.argv.includes('--read-saved')
if (readSaved) {
  const previous = JSON.parse(readFileSync(new URL('../../.implementation-work/document-save-audit-checks.json', import.meta.url)))
  assert.ok(previous.checks.some(check => check.name === 'ACTUAL_DOCUMENT_REVISION_SAVE' && check.result === 'PASS'))
  proof.previous_save_verified_at = previous.previous_save_verified_at ?? previous.verified_at
  proof.checks.push({ name: 'ACTUAL_DOCUMENT_REVISION_SAVE', result: 'PASS', evidence: 'PREVIOUS_ACTUAL_SAVE_RECEIPT' })
}
let page
try {
  const csrf = await (await context.request.get(origin + '/auth/csrf')).json()
  assert.equal((await context.request.post(origin + '/auth/login', { headers: { Origin: origin, [csrf.header_name]: csrf.token }, form: owner, maxRedirects: 0 })).status(), 302)
  page = await context.newPage()
  await page.addInitScript(() => window.addEventListener('medical-failure', event => console.error('SAVE_ERROR', event.detail?.message, event.detail?.stack)))
  page.on('console', message => { if (message.text().startsWith('SAVE_ERROR')) console.log(message.text()) })
  page.setDefaultTimeout(240000)
  page.on('pageerror', error => proof.failures.push(error.message))
  page.on('response', response => {
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/')) {
      console.log(response.status(), path)
      if (response.status() >= 400) proof.failures.push({ path, status: response.status() })
    }
  })
  if (!readSaved) {
  await page.goto(origin + `/projects/${seed.project_id}/tasks/${seed.task_id}/documents/${seed.document_id}/write`, { waitUntil: 'domcontentloaded' })
  const editor = page.getByRole('button', { name: '编辑正文', exact: true })
  await editor.waitFor()
  page.setDefaultTimeout(20000)
  await page.evaluate(() => {
    const config = document.getElementById('app').__vue_app__.config
    const handler = config.errorHandler
    config.errorHandler = (error, instance, info) => {
      console.error('SAVE_ERROR', error.message, error.stack, info)
      return handler(error, instance, info)
    }
  })
  console.log('STEP', 'EDITOR_READ')
  await editor.click()
  console.log('STEP', 'EDIT_MODE')
  await page.locator('.editor-content').getByRole('textbox', { name: '目录或段落标题', exact: true }).first().fill('SIMULATION 验收文稿 · 保存验证 ' + new Date().toISOString())
  console.log('STEP', 'TITLE_CHANGED')
  await page.getByLabel('本次修订理由', { exact: true }).fill('SIMULATION 本地验收：验证正文修订及审计固定版本。')
  console.log('STEP', 'REASON_FILLED')
  const saved = page.waitForResponse(response => response.url().endsWith('/api/documents/' + seed.document_id + '/revisions') && response.request().method() === 'POST'
    || new URL(response.url()).pathname.startsWith('/api/') && response.status() >= 400, { timeout: 240000 })
  await page.getByRole('button', { name: '保存正文修订', exact: true }).click()
  const response = await saved
  assert.equal(response.status(), 201)
  await page.getByText('当前正文包含尚未保存的编辑', { exact: true }).waitFor({ state: 'hidden', timeout: 240000 })
  proof.checks.push({ name: 'ACTUAL_DOCUMENT_REVISION_SAVE', result: 'PASS' })
  await page.evaluate(() => document.getElementById('app').__vue_app__.config.globalProperties.$router.push('/document-save-audit'))
  } else await page.goto(origin + '/document-save-audit', { waitUntil: 'domcontentloaded' })
  await page.getByRole('combobox', { name: '项目', exact: true }).waitFor({ timeout: 120000 })
  await page.getByRole('combobox', { name: '项目', exact: true }).focus()
  await page.getByRole('combobox', { name: '项目', exact: true }).press('ArrowDown')
  await page.getByRole('option').filter({ hasText: '验收测试 · 注册资料示例项目' }).waitFor({ timeout: 120000 })
  await page.getByRole('option').filter({ hasText: '验收测试 · 注册资料示例项目' }).click()
  if(readSaved){
    const previous=new Date(proof.previous_save_verified_at)
    const format=value=>new Intl.DateTimeFormat('sv-SE',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false}).format(value)
    const queryForm=page.locator('.audit-query')
    await queryForm.getByPlaceholder('开始时间',{exact:true}).fill(format(new Date(previous.getTime()-86400000)))
    await queryForm.getByPlaceholder('结束时间',{exact:true}).fill(format(new Date()))
    await queryForm.getByPlaceholder('结束时间',{exact:true}).press('Enter')
  }
  const current=(await(await context.request.get(origin+'/api/session',{headers:{'X-Schema-Version':'1.0.0'}})).json()).data
  const headers={'X-Schema-Version':'1.0.0','X-Context-Revision':current.current_context.context_revision}
  const members=await context.request.get(origin+'/api/enterprise-member-labels',{headers})
  assert.equal(members.status(),200)
  const memberDirectory=(await members.json()).data,person=page.getByRole('combobox',{name:'保存人员',exact:true})
  await person.focus();await person.press('ArrowDown')
  for(const member of memberDirectory.items.filter(item=>item.user_id!==current.user.user_id&&item.display_name))assert.ok(await page.getByRole('option',{name:member.display_name,exact:true}).count()>0)
  await page.getByRole('option',{name:current.user.display_name??current.user.username,exact:true}).click()
  const filtered=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/audit-events'&&response.request().method()==='GET')
  await page.getByRole('button', { name: '查询保存记录', exact: true }).click()
  const filteredReply=await filtered;assert.equal(filteredReply.status(),200);assert.equal(new URL(filteredReply.url()).searchParams.get('actor_id'),current.user.user_id)
  proof.checks.push({name:'ACTUAL_MEMBER_DIRECTORY_AND_PERSON_FILTER',result:'PASS'})
  const records = page.locator('[data-audit-records]')
  await records.getByText('保存正文', { exact: true }).first().waitFor({ timeout: 120000 })
  assert.ok((await records.innerText()).includes('综述资料'))
  await records.getByRole('button', { name: '查看版本', exact: true }).first().click()
  await page.getByText('文稿：' + seed.document_id, { exact: true }).waitFor()
  await records.getByRole('button', { name: '查看版本', exact: true }).first().click()
  await records.getByRole('button', { name: '读取固定版本', exact: true }).first().click()
  await page.getByRole('region', { name: '固定版本只读正文', exact: true }).waitFor({ timeout: 240000 })
  proof.checks.push({ name: 'SAVE_AUDIT_QUERY_AND_FIXED_REVISION_READ', result: 'PASS' })
  await page.getByRole('button',{name:'关闭正文',exact:true}).click()
  await page.getByRole('combobox',{name:'保存人员',exact:true}).focus();await page.getByRole('combobox',{name:'保存人员',exact:true}).press('ArrowDown')
  const other=memberDirectory.items.find(item=>item.user_id!==current.user.user_id&&item.display_name)
  assert.ok(other,'实际企业目录需要其他已登记成员，以核查人员筛选变化')
  await page.getByRole('option',{name:other.display_name,exact:true}).click()
  await records.waitFor({state:'hidden'})
  assert.equal(await page.getByRole('region',{name:'固定版本只读正文',exact:true}).count(),0)
  proof.checks.push({name:'FILTER_CHANGE_CLEARS_FIXED_RESULTS',result:'PASS'})
  assert.deepEqual(proof.failures, [])
  console.log(JSON.stringify(proof))
} catch (failure) {
  proof.failures.push({message:failure.message})
  throw new Error(failure.message.split('\n')[0])
} finally {
  if (page) proof.page = await page.locator('.app-main').innerText()
  writeFileSync(new URL('../../.implementation-work/document-save-audit-checks.json', import.meta.url), JSON.stringify(proof, null, 2), { mode: 0o600 })
  await context.close()
  await browser.close()
}
