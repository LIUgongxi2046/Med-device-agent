"use strict";

import { exactKeys, validRevision, validateEnvelope } from "./management-request.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const hash = /^[0-9a-f]{64}$/;
const actionPattern = /^[a-z][a-z0-9_.-]{0,63}$/;
const purposePattern = /^[A-Z][A-Z0-9_]{0,63}$/;
const kinds = ["ENTERPRISE", "PROJECT", "MEMBER", "SOURCE", "TASK", "DOCUMENT", "ARTIFACT", "CONFIGURATION", "TEMPLATE", "RULE", "BUDGET", "EVALUATION", "AUDIT", "REPORT"];
const root = "/api/enterprise-roles";
const content = document.querySelector("[data-role-content]");
const status = document.querySelector("[data-status]");
const refresh = document.querySelector("[data-refresh]");
const publish = document.querySelector("[data-publish]");
const revoke = document.querySelector("[data-revoke]");
const history = document.querySelector("[data-history]");
const channel = new BroadcastChannel("medical.context");
let actor = null, session = null, selected = null, after = null, busy = false, generation = 0, reading = null, closed = false;

function id(value) { if (typeof value !== "string" || !uuid.test(value)) throw new Error("身份编号无效"); }
function text(value, max) {
  if (typeof value !== "string" || value.trim() !== value || value.length === 0 || value.normalize("NFC") !== value
      || Array.from(value).length > max || /[\u0000-\u001f\u007f-\u009f]/.test(value)) throw new Error("批准文本内容无效");
}
function time(value) { if (typeof value !== "string" || !value.endsWith("Z") || !Number.isFinite(Date.parse(value))) throw new Error("有效期限无效"); }
function context(value) {
  exactKeys(value, ["tenant_id", "context_revision", "auth_epoch", "governance_epoch"]); id(value.tenant_id);
  for (const name of ["context_revision", "auth_epoch", "governance_epoch"]) validRevision(value[name]);
}
function array(value, pattern, max = 1000, nullable = false) {
  if (nullable && value === null) return;
  if (!Array.isArray(value) || value.length > max || new Set(value).size !== value.length
      || value.some(item => typeof item !== "string" || !pattern.test(item))) throw new Error("批准数组内容无效");
}
function condition(value) {
  exactKeys(value, ["schema_version", "identity_modes", "purposes", "delegation"]);
  if (value.schema_version !== "1.0.0" || !Array.isArray(value.identity_modes) || value.identity_modes.length === 0
      || value.identity_modes.some(item => !["local", "oidc"].includes(item)) || new Set(value.identity_modes).size !== value.identity_modes.length) throw new Error("岗位登录条件无效");
  array(value.purposes, purposePattern, 1000, true);
  exactKeys(value.delegation, ["actions", "role_ids", "resource_kinds", "project_ids", "resource_ids"]);
  array(value.delegation.actions, actionPattern); array(value.delegation.role_ids, uuid);
  if (!Array.isArray(value.delegation.resource_kinds) || new Set(value.delegation.resource_kinds).size !== value.delegation.resource_kinds.length
      || value.delegation.resource_kinds.some(item => !kinds.includes(item))) throw new Error("委派资源类别无效");
  array(value.delegation.project_ids, uuid, 1000, true); array(value.delegation.resource_ids, uuid, 1000, true);
}
function role(value) {
  exactKeys(value, ["schema_version", "tenant_id", "role_id", "version_id", "name", "definition", "status", "revision", "content_sha256",
    "approved_by", "approved_at", "valid_from", "valid_to", "approval_basis", "reason", "authority_grant", "authority_revision", "head"]);
  for (const name of ["tenant_id", "role_id", "version_id", "approved_by", "authority_grant"]) id(value[name]);
  for (const name of ["revision", "authority_revision"]) validRevision(value[name]);
  exactKeys(value.head, ["current_version", "revision"]); id(value.head.current_version); validRevision(value.head.revision);
  exactKeys(value.approval_basis, ["version_id", "content_sha256"]); id(value.approval_basis.version_id);
  text(value.name, 120); text(value.reason, 2000); time(value.approved_at); time(value.valid_from);
  if (value.valid_to !== null) { time(value.valid_to); if (Date.parse(value.valid_to) <= Date.parse(value.valid_from)) throw new Error("岗位有效期顺序无效"); }
  exactKeys(value.definition, ["schema_version", "role_id", "actions", "condition"]); array(value.definition.actions, actionPattern); condition(value.definition.condition);
  if (value.schema_version !== "1.0.0" || value.definition.schema_version !== "1.0.0" || value.definition.role_id !== value.role_id
      || !hash.test(value.content_sha256) || !hash.test(value.approval_basis.content_sha256) || !["ACTIVE", "REVOKED"].includes(value.status)
      || session === null || value.tenant_id !== session.current_context.tenant_id) throw new Error("岗位版本与当前企业不一致");
}
function clear() {
  session = null; selected = null; after = null; content.hidden = true;
  document.querySelector("[data-roles]").replaceChildren(); document.querySelector("[data-selected] dl").replaceChildren();
  document.querySelector("[data-selected]").hidden = true; document.querySelector("[data-context]").textContent = "";
  for (const form of [publish, revoke, history]) { form.reset(); form.querySelector("fieldset").disabled = true; }
  document.querySelector("[data-revoke-target]").textContent = "需要读取目标岗位版本";
}
function storageKey() { if (actor === null) throw new Error("当前身份尚未确认"); return "medical.enterprise.role:" + actor; }
function pending() {
  if (actor === null) return null;
  const saved = sessionStorage.getItem(storageKey()); if (saved === null) return null;
  const value = JSON.parse(saved); exactKeys(value, ["key", "digest", "revision"]); id(value.key); validRevision(value.revision);
  if (!hash.test(value.digest)) throw new Error("原请求摘要无效"); return value;
}
function showPending() {
  const value = pending(); document.querySelector("[data-pending]").hidden = value === null;
  document.querySelector("[data-pending-id]").textContent = value === null ? "" : "请求编号：" + value.key + "；原企业版本：" + value.revision;
}
async function request(path, options = {}) {
  const response = await fetch(path, { credentials: "same-origin", cache: "no-store", ...(options.method === "POST" ? {} : { signal: reading?.signal }), ...options });
  const value = await response.json();
  if (!response.ok) {
    exactKeys(value, ["schema_version", "code", "message", "request_id", "resource_revision", "allowed_actions", "retryable", "operation_id", "details"]);
    id(value.request_id); if (value.schema_version !== "1.0.0" || typeof value.message !== "string") throw new Error("服务错误响应无效");
    if ([401, 403, 409].includes(response.status)) clear();
    if (response.status === 401) window.location.assign("/auth/login");
    status.textContent = value.message; throw new Error(value.code);
  }
  validateEnvelope(response, value); return { response, value };
}
async function readSession() {
  const { value } = await request("/api/session", { headers: { "X-Schema-Version": "1.0.0" } });
  const data = value.data; exactKeys(data, ["schema_version", "user", "memberships", "current_context", "csrf", "action_summary", "invitation_entry"]);
  exactKeys(data.user, ["user_id", "username", "display_name", "platform_role"]); id(data.user.user_id);
  exactKeys(data.csrf, ["header_name", "token"]);
  if (data.schema_version !== "1.0.0" || data.csrf.header_name !== "X-CSRF-TOKEN" || typeof data.csrf.token !== "string" || data.csrf.token.length === 0
      || !Array.isArray(data.memberships) || !Array.isArray(data.action_summary)) throw new Error("当前会话响应无效");
  if (actor !== null && actor !== data.user.user_id) { clear(); throw new Error("当前账户已经改变，请重新打开岗位页面"); }
  actor = data.user.user_id;
  if (data.current_context !== null) {
    context(data.current_context);
    if (value.resource_revision !== data.current_context.context_revision
        || !data.memberships.some(member => member.tenant_id === data.current_context.tenant_id && member.status === "ACTIVE")) throw new Error("当前企业成员关联无效");
  } else if (value.resource_revision !== null || data.action_summary.length !== 0) throw new Error("未选择企业的动作响应无效");
  for (const action of data.action_summary) exactKeys(action, ["action", "project_id", "resource_kind", "resource_id"]);
  return data;
}
function headers() { if (session?.current_context == null) throw new Error("需要当前企业身份"); return { "X-Schema-Version": "1.0.0", "X-Context-Revision": session.current_context.context_revision }; }
function showRole(value) {
  role(value); selected = value; const section = document.querySelector("[data-selected]"); const list = section.querySelector("dl"); list.replaceChildren();
  for (const [name, text] of [["岗位名称", value.name], ["岗位标识", value.role_id], ["不可变版本", value.version_id], ["当前状态", value.status],
      ["对象版本", value.revision], ["当前发布版本", value.head.revision], ["批准人员", value.approved_by], ["批准时间", value.approved_at],
      ["生效时间", value.valid_from], ["截止时间", value.valid_to ?? "已经批准没有固定截止时间"], ["批准依据", value.approval_basis.version_id],
      ["依据SHA-256", value.approval_basis.content_sha256], ["岗位内容SHA-256", value.content_sha256], ["批准原因", value.reason],
      ["批准动作", value.definition.actions.join("、")], ["完整条件", JSON.stringify(value.definition.condition)], ["管理授权来源", value.authority_grant]]) {
    const dt = document.createElement("dt"), dd = document.createElement("dd"); dt.textContent = name; dd.textContent = text; list.append(dt, dd);
  }
  section.hidden = false; revoke.reset(); revoke.elements.namedItem("expected_revision").value = value.revision; revoke.querySelector("fieldset").disabled = busy;
  document.querySelector("[data-revoke-target]").textContent = value.name + "；岗位：" + value.role_id + "；版本：" + value.version_id;
}
async function readRoles(reset) {
  const before = generation; const url = new URL(root, window.location.origin); url.searchParams.set("limit", "50");
  if (!reset && after !== null) url.searchParams.set("after", after);
  const { value } = await request(url, { headers: headers() }); if (before !== generation || closed) return;
  exactKeys(value.data, ["schema_version", "roles", "next_after"]);
  if (value.resource_revision !== null || value.data.schema_version !== "1.0.0" || !Array.isArray(value.data.roles) || value.data.roles.length > 50) throw new Error("岗位列表无效");
  if (value.data.next_after !== null) id(value.data.next_after);
  const list = document.querySelector("[data-roles]"); if (reset) list.replaceChildren();
  for (const item of value.data.roles) {
    role(item); if (item.version_id !== item.head.current_version) throw new Error("岗位当前发布版本不一致");
    const article = document.createElement("article"), title = document.createElement("p"), button = document.createElement("button");
    title.textContent = item.name + " · " + item.status; button.type = "button"; button.textContent = "读取岗位版本与批准来源";
    button.addEventListener("click", () => readRole(item.role_id, item.version_id)); article.append(title, button); list.append(article);
  }
  if (reset && value.data.roles.length === 0) list.textContent = "当前授权范围没有已发布岗位";
  after = value.data.next_after; document.querySelector("[data-more]").hidden = after === null;
}
async function readRole(roleId, versionId) {
  id(roleId); id(versionId); const before = generation;
  const { value } = await request(root + "/" + roleId + "/versions/" + versionId, { headers: headers() });
  if (before !== generation || closed) return;
  role(value.data); if (value.data.role_id !== roleId || value.data.version_id !== versionId || value.resource_revision !== value.data.revision) throw new Error("岗位详情身份不一致");
  showRole(value.data);
}
async function reload() {
  if (closed || busy) return;
  generation++; reading?.abort(); reading = new AbortController(); clear(); refresh.disabled = true;
  const before = generation;
  try {
    const actual = await readSession(); if (before !== generation || closed) return; showPending();
    if (actual.current_context === null) { status.textContent = "当前账户尚未选择企业，请取得企业成员身份并明确选择企业空间"; return; }
    if (!actual.action_summary.some(action => action.action === "role.manage" && action.project_id === null && action.resource_kind === "ENTERPRISE"
        && action.resource_id === actual.current_context.tenant_id)) { status.textContent = "当前企业没有批准岗位管理权限"; return; }
    session = actual; await readRoles(true); if (before !== generation || closed || session === null) return;
    content.hidden = false; publish.querySelector("fieldset").disabled = false; history.querySelector("fieldset").disabled = false;
    document.querySelector("[data-context]").textContent = "企业：" + actual.current_context.tenant_id + "；当前版本：" + actual.current_context.context_revision;
    status.textContent = "可以读取并管理当前授权范围内的真实岗位";
  } finally { if (before === generation && !closed) refresh.disabled = false; }
}
function field(form, name) { return form.elements.namedItem(name); }
function lines(form, name, pattern) { const values = field(form, name).value === "" ? [] : field(form, name).value.split(/\r?\n/); array(values, pattern); return values; }
function checked(name) { return Array.from(publish.querySelectorAll('[name="' + name + '"]:checked'), input => input.value); }
function nullable(form, all, name, pattern) {
  const values = lines(form, name, pattern); if (field(form, all).checked && values.length > 0) throw new Error("全部范围与指定范围不能同时填写");
  return field(form, all).checked ? null : values;
}
async function write(path, body, decision, roleId, versionId) {
  if (busy || session?.current_context == null) throw new Error("当前岗位操作不可提交");
  busy = true; refresh.disabled = true; const before = generation; const originalSession = session;
  for (const form of [publish, revoke, history]) form.querySelector("fieldset").disabled = true;
  try {
    const actual = await readSession();
    if (actual.current_context === null || JSON.stringify(actual.current_context) !== JSON.stringify(originalSession.current_context)) { clear(); throw new Error("当前企业权限已经变化，请重新读取"); }
    const digest = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify({ path, body })))), byte => byte.toString(16).padStart(2, "0")).join("");
    let saved = pending();
    if (saved === null) { saved = { key: crypto.randomUUID(), digest, revision: actual.current_context.context_revision }; sessionStorage.setItem(storageKey(), JSON.stringify(saved)); }
    if (saved.digest !== digest) throw new Error("内容与原请求不同，请恢复相同内容或核查以后明确结束原请求身份");
    showPending(); status.textContent = "正在保存决定，请使用原请求身份核查结果";
    const { value } = await request(path, { method: "POST", headers: { "Content-Type": "application/json", "X-Schema-Version": "1.0.0",
      "X-Context-Revision": saved.revision, "Idempotency-Key": saved.key, [actual.csrf.header_name]: actual.csrf.token }, body: JSON.stringify(body) });
    if (before !== generation || closed || document.hidden) return;
    const receipt = value.data; exactKeys(receipt, ["schema_version", "operation_id", "decision", "role", "revoked_grants", "terminated_invitations", "current_context"]);
    id(receipt.operation_id); context(receipt.current_context); role(receipt.role); array(receipt.revoked_grants, uuid, Number.MAX_SAFE_INTEGER); array(receipt.terminated_invitations, uuid, Number.MAX_SAFE_INTEGER);
    if (receipt.schema_version !== "1.0.0" || receipt.decision !== decision || receipt.role.role_id !== roleId
        || versionId !== null && receipt.role.version_id !== versionId || value.resource_revision !== receipt.role.revision
        || receipt.role.status !== (decision === "PUBLISH" ? "ACTIVE" : "REVOKED")
        || receipt.current_context.tenant_id !== originalSession.current_context.tenant_id
        || receipt.current_context.context_revision !== (BigInt(saved.revision)+1n).toString()) throw new Error("岗位决定响应身份不一致");
    if (decision === "PUBLISH" && (receipt.role.name !== body.name || JSON.stringify(receipt.role.definition) !== JSON.stringify(body.definition)
        || receipt.role.approved_by !== actor || receipt.role.reason !== body.reason
        || receipt.role.approval_basis.version_id !== body.approval_basis.version_id || receipt.role.approval_basis.content_sha256 !== body.approval_basis.content_sha256
        || Date.parse(receipt.role.valid_from) !== Date.parse(body.valid_from)
        || (receipt.role.valid_to === null) !== (body.valid_to === null)
        || body.valid_to !== null && Date.parse(receipt.role.valid_to) !== Date.parse(body.valid_to))) throw new Error("发布结果与批准内容不一致");
    const current = await readSession();
    if (current.current_context === null || JSON.stringify(current.current_context) !== JSON.stringify(receipt.current_context)) { clear(); throw new Error("保存后的当前权限已经变化，需要重新核查"); }
    session = current;
    const reread = await request(root + "/" + roleId + "/versions/" + receipt.role.version_id, { headers: headers() }); role(reread.value.data);
    if (before !== generation || closed || document.hidden) return;
    if (reread.value.resource_revision !== receipt.role.revision || reread.value.data.role_id !== roleId || reread.value.data.version_id !== receipt.role.version_id
        || reread.value.data.status !== receipt.role.status || reread.value.data.content_sha256 !== receipt.role.content_sha256) throw new Error("岗位保存重读结果不一致");
    sessionStorage.removeItem(storageKey()); showPending(); channel.postMessage("CONTEXT_INVALIDATED");
    await readRoles(true); publish.reset(); showRole(reread.value.data); status.textContent = "岗位决定已经保存，并重新读取当前版本与权限";
  } finally {
    busy = false; if (!closed) refresh.disabled = false;
    if (!closed && before === generation) {
      if (session !== null) { publish.querySelector("fieldset").disabled = false; history.querySelector("fieldset").disabled = false; revoke.querySelector("fieldset").disabled = selected === null; }
    }
  }
}
publish.addEventListener("submit", async event => {
  event.preventDefault(); const roleId = field(publish, "role_id").value; id(roleId); validRevision(field(publish, "expected_head_revision").value);
  const name = field(publish, "name").value, reason = field(publish, "reason").value; text(name, 120); text(reason, 2000);
  const allowed = { schema_version: "1.0.0", identity_modes: checked("identity_mode"), purposes: nullable(publish, "all_purposes", "purposes", purposePattern), delegation: {
    actions: lines(publish, "delegated_actions", actionPattern), role_ids: lines(publish, "delegated_roles", uuid), resource_kinds: checked("resource_kind"),
    project_ids: nullable(publish, "all_projects", "project_ids", uuid), resource_ids: nullable(publish, "all_resources", "resource_ids", uuid) } };
  condition(allowed); const basisId = field(publish, "basis_id").value, basisHash = field(publish, "basis_hash").value; id(basisId);
  if (!hash.test(basisHash)) throw new Error("批准依据摘要无效");
  const from = new Date(field(publish, "valid_from").value).toISOString();
  if (field(publish, "unlimited_validity").checked && field(publish, "valid_to").value !== "") throw new Error("没有固定截止时间时需要清除截止时间");
  const to = field(publish, "unlimited_validity").checked ? null : new Date(field(publish, "valid_to").value).toISOString();
  if (to !== null && Date.parse(to) <= Date.parse(from)) throw new Error("岗位有效期顺序无效");
  const body = { schema_version: "1.0.0", name, definition: { schema_version: "1.0.0", role_id: roleId, actions: lines(publish, "actions", actionPattern), condition: allowed },
    valid_from: from, valid_to: to, approval_basis: { version_id: basisId, content_sha256: basisHash }, reason,
    approval_attested: field(publish, "approval_attested").checked, expected_head_revision: field(publish, "expected_head_revision").value };
  await write(root + "/" + roleId + "/versions", body, "PUBLISH", roleId, null);
});
revoke.addEventListener("submit", async event => {
  event.preventDefault(); if (selected === null) throw new Error("需要读取目标岗位版本");
  const reason = field(revoke, "reason").value; text(reason, 2000); validRevision(field(revoke, "expected_revision").value);
  await write(root + "/" + selected.role_id + "/versions/" + selected.version_id + "/revocations", { schema_version: "1.0.0",
    expected_revision: field(revoke, "expected_revision").value, reason, approval_attested: field(revoke, "approval_attested").checked }, "REVOKE", selected.role_id, selected.version_id);
});
history.addEventListener("submit", async event => { event.preventDefault(); await readRole(field(history, "role_id").value, field(history, "version_id").value); });
document.querySelector("[data-copy]").addEventListener("click", () => {
  if (selected === null || session === null || busy) throw new Error("需要当前批准岗位版本"); publish.reset();
  for (const [name, value] of [["role_id", selected.role_id], ["name", selected.name], ["expected_head_revision", selected.head.revision], ["actions", selected.definition.actions.join("\n")],
      ["basis_id", selected.approval_basis.version_id], ["basis_hash", selected.approval_basis.content_sha256]]) field(publish, name).value = value;
  const allowed = selected.definition.condition;
  for (const input of publish.querySelectorAll("[name=identity_mode]")) input.checked = allowed.identity_modes.includes(input.value);
  for (const [all, name, value] of [["all_purposes", "purposes", allowed.purposes], ["all_projects", "project_ids", allowed.delegation.project_ids], ["all_resources", "resource_ids", allowed.delegation.resource_ids]]) {
    field(publish, all).checked = value === null; field(publish, name).value = value === null ? "" : value.join("\n");
  }
  field(publish, "delegated_actions").value = allowed.delegation.actions.join("\n"); field(publish, "delegated_roles").value = allowed.delegation.role_ids.join("\n");
  for (const input of publish.querySelectorAll("[name=resource_kind]")) input.checked = allowed.delegation.resource_kinds.includes(input.value);
  status.textContent = "已经填写原版本条件，请明确填写本次期限、批准原因和声明";
});
for (const kind of kinds) {
  const label = document.createElement("label"), input = document.createElement("input"); label.className = "attestation"; input.type = "checkbox"; input.name = "resource_kind"; input.value = kind;
  label.append(input, document.createTextNode(kind)); document.querySelector("[data-kinds]").append(label);
}
for (const input of document.querySelectorAll("[data-limit]")) input.addEventListener("input", () => input.setCustomValidity(Array.from(input.value).length > Number(input.dataset.limit) ? "文本超过字符限制" : ""));
document.querySelector("[data-clear]").addEventListener("click", () => {
  if (busy || !document.querySelector("[data-resolution]").checked) throw new Error("需要明确核查原请求结果");
  sessionStorage.removeItem(storageKey()); document.querySelector("[data-resolution]").checked = false; showPending(); status.textContent = "原请求身份已经结束，后续提交将使用新请求身份";
});
refresh.addEventListener("click", reload); document.querySelector("[data-more]").addEventListener("click", () => readRoles(false));
function invalidate() { generation++; reading?.abort(); clear(); refresh.disabled = busy || closed; }
channel.addEventListener("message", event => { if (event.data === "CONTEXT_INVALIDATED") { invalidate(); status.textContent = "企业权限已经变化，请重新读取岗位权限"; } });
document.addEventListener("visibilitychange", () => { if (document.hidden) invalidate(); else reload(); });
window.addEventListener("pagehide", () => { closed = true; invalidate(); channel.close(); });
window.addEventListener("pageshow", event => { if (event.persisted) window.location.reload(); });
window.addEventListener("unhandledrejection", event => {
  if (event.reason?.name === "AbortError" && (closed || document.hidden)) return;
  clear(); refresh.disabled = busy || closed;
  if (event.reason instanceof TypeError) status.textContent = "连接请求未完成，请恢复连接后重新读取岗位权限";
  else if (!closed && event.reason instanceof Error && !["ACCESS_DENIED", "SCOPE_STALE", "CONTEXT_CHANGED"].includes(event.reason.message)) status.textContent = event.reason.message;
});
await reload();
