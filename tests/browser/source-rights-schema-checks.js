import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { chromium } from 'playwright'
import { build, createServer, preview } from '../../web/node_modules/vite/dist/node/index.js'

const root = process.cwd()
const output = path.join(root, '.implementation-work/FRONT-SOURCE-RIGHTS-SCHEMA-20261014')
await mkdir(output, { recursive: true })
const developmentOnly = process.argv.includes('--development-only')
process.chdir(path.join(root, 'web'))
const options = { configFile: path.join(root, 'web/vite.config.js'), mode: 'medical', cacheDir: path.join(output, 'vite-cache'),
  server: { host: '127.0.0.1', port: 18094, strictPort: true, open: false },
  build: { outDir: path.join(output, 'production'), emptyOutDir: true,
    rollupOptions: { input: path.join(root, 'web/tests/source-rights/index.html') } } }
if (!developmentOnly) await build(options)
const development = await createServer(options)
const production = developmentOnly ? null : await preview({ ...options, preview: { host: '127.0.0.1', port: 18095, strictPort: true, open: false } })
try {
  await development.listen()
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    const checks = []
    const modes = developmentOnly ? [['development', 'http://127.0.0.1:18094']]
      : [['development', 'http://127.0.0.1:18094'], ['production', 'http://127.0.0.1:18095']]
    for (const [mode, origin] of modes) {
      const page = await browser.newPage()
      try {
        page.setDefaultNavigationTimeout(120000)
        page.setDefaultTimeout(60000)
        page.on('pageerror', error => console.error(error.stack))
        page.on('console', message => { if (message.type() === 'error') console.error(message.text()) })
        await page.goto(origin + '/tests/source-rights/index.html')
        await page.waitForFunction(() => window.sourceRightsChecks !== undefined)
        const basis = { version_id: randomUUID(), content_sha256: 'a'.repeat(64) }
        const proposal = { purpose: 'SOURCE_RIGHTS_REVIEW', allowed_actions: ['READ', 'STORE', 'ENTERPRISE_USE', 'DISTRIBUTE', 'EXTERNAL_PROCESS'],
          providers: ['SIMULATION_PROVIDER'], regions: ['LOCAL'], valid_from: '2026-10-14T01:02:03.123456Z', valid_to: null,
          evidence_manifest: { owner: 'SIMULATION 样例企业', license: 'SIMULATION 资料许可，仅用于本轮开发', environments: ['LOCAL'],
            classifications: ['INTERNAL'], authorization_evidence: [basis] } }
        const approve = { schema_version: '1.0.0', expected_revision: '18446744073709551614', base_rights: basis,
          decision: 'APPROVE', proposal, reviewed_manifest_sha256: 'b'.repeat(64), reason: 'SIMULATION 完整范围检查' }
        const validate = (definition, value) => page.evaluate(({ definition, value }) =>
          window.sourceRightsChecks.validate('urn:medical:source-rights-decisions:1.0.0#/$defs/' + definition, value), { definition, value })
        assert.deepEqual(await validate('DecisionInput', approve), approve)
        checks.push({ mode, name: 'complete_approval_scope_and_precise_revision', result: 'PASS' })
        for (const decision of ['RETURN', 'REVOKE']) {
          const body = { ...approve, decision, proposal: null }
          assert.deepEqual(await validate('DecisionInput', body), body)
          await assert.rejects(validate('DecisionInput', { ...body, proposal }))
        }
        checks.push({ mode, name: 'return_and_revoke_preserve_explicit_null_proposal', result: 'PASS' })
        for (const body of [{ ...approve, proposal: null }, { ...approve, expected_revision: 9007199254740993 },
          { ...approve, reason: '' }, { ...approve, unexpected: true }, { ...approve, proposal: { ...proposal, valid_from: '2026-10-14' } },
          { ...approve, proposal: { ...proposal, evidence_manifest: { ...proposal.evidence_manifest, authorization_evidence: [] } } }]) {
          await assert.rejects(validate('DecisionInput', body))
        }
        checks.push({ mode, name: 'missing_evidence_unknown_fields_invalid_time_and_numeric_revision_rejected', result: 'PASS' })
        const issues = body => page.evaluate(body => window.sourceRightsChecks.rightsInputIssues(body), body)
        assert.deepEqual(await issues(approve), [])
        assert((await issues({ ...approve, proposal: { ...proposal, allowed_actions: ['READ', 'ENTERPRISE_USE'] } })).some(item => item.code === 'STORE_REQUIRED'))
        assert((await issues({ ...approve, proposal: { ...proposal, providers: [] } })).some(item => item.code === 'PROVIDER_REQUIRED'))
        assert((await issues({ ...approve, proposal: { ...proposal, evidence_manifest: { ...proposal.evidence_manifest,
          authorization_evidence: [basis, { ...basis, content_sha256: 'c'.repeat(64) }] } } })).some(item => item.code === 'DUPLICATE_ID'))
        assert((await issues({ ...approve, expected_revision: '18446744073709551615' })).some(item => item.code === 'REVISION_LIMIT'))
        checks.push({ mode, name: 'mandatory_store_external_provider_unique_artifact_and_unsigned_revision_limit', result: 'PASS' })
        const tenant = randomUUID(); const actor = randomUUID(); const target = randomUUID()
        const session = { schema_version: '1.0.0', user: { user_id: actor, username: null, display_name: 'SIMULATION 原权利请求', platform_role: null },
          memberships: [{ tenant_id: tenant, member_id: randomUUID(), status: 'ACTIVE', valid_from: '2026-10-14T00:00:00Z', valid_to: null, revision: '1' }],
          current_context: { tenant_id: tenant, context_revision: '1', auth_epoch: '1', governance_epoch: '1' },
          csrf: { header_name: 'X-CSRF-TOKEN', token: 'SIMULATION_COMPONENT_INPUT' }, action_summary: [],
          invitation_entry: { method: 'POST', path_template: '/api/member-invitations/{id}/acceptances' } }
        const prepare = (body, current = session) => page.evaluate(({ session, target, scope, body }) =>
          window.sourceRightsChecks.prepareRequestMetadata(session, 'KN-RIGHTS-01', target, target, scope, body, new AbortController().signal),
          { session: current, target, scope: basis.version_id, body })
        const pending = await prepare(approve)
        const nextSession = { ...session, current_context: { ...session.current_context, context_revision: '2', governance_epoch: '2' } }
        assert.deepEqual(await prepare(approve, nextSession), pending)
        await assert.rejects(prepare({ ...approve, reason: 'SIMULATION 其他理由' }))
        const saved = await page.evaluate(() => Object.fromEntries(Object.keys(sessionStorage).map(key => [key, sessionStorage.getItem(key)])))
        assert.equal(Object.keys(saved).length, 1)
        const bytes = Object.values(saved)[0]
        assert.equal(bytes.includes(approve.reason), false); assert.equal(bytes.includes(proposal.evidence_manifest.license), false)
        assert.equal(bytes.includes(proposal.evidence_manifest.owner), false)
        assert.equal(bytes.includes(basis.content_sha256), false)
        checks.push({ mode, name: 'same_original_key_and_context_reused_without_persisting_reason_license_or_authorization', result: 'PASS' })
        await page.evaluate(({ session, pending }) => window.sourceRightsChecks.expireRequestMetadata(session, pending), { session, pending })
        await assert.rejects(prepare(approve))
        await page.evaluate(({ session, target }) => {
          const pending = window.sourceRightsChecks.readRequestMetadata(session, 'KN-RIGHTS-01', target)
          window.sourceRightsChecks.clearExpiredRequestMetadata(session, pending)
        }, { session, target })
        const replacement = await prepare(approve, nextSession)
        assert.notEqual(replacement.key, pending.key); assert.equal(replacement.context.context_revision, '2')
        checks.push({ mode, name: 'expired_original_request_never_reissued_and_explicit_new_review_has_new_identity', result: 'PASS' })
      } finally { await page.close() }
    }
    await writeFile(path.join(output, 'checks.json'), JSON.stringify({ scope: 'SOURCE_RIGHTS_SCHEMA', data_kind: 'SIMULATION',
      visual: 'NOT_RUN', checks }, null, 2) + '\n')
    console.log(JSON.stringify({ result: 'PASS', checks: checks.length }))
  } finally { await browser.close() }
} finally {
  await development.close()
  if (production) await new Promise((resolve, reject) => production.httpServer.close(error => error ? reject(error) : resolve()))
}
