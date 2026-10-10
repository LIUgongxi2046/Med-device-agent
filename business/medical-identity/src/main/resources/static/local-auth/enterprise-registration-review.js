"use strict";

import { exactKeys, validRevision, validateEnvelope } from "./management-request.js";

const endpoint = "/api/enterprise-registration-reviews";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const sha = /^[0-9a-f]{64}$/;
const form = document.querySelector("[data-decision]");
const fields = form.querySelector("fieldset");
const filter = document.querySelector("[data-filter]");
const refresh = document.querySelector("[data-refresh]");
const next = document.querySelector("[data-next]");
const message = document.querySelector("[data-status]");
const requests = new AbortController();
let cursor = null;
let selected = null;
let denied = false;
let busy = false;

function clearRestricted() {
  selected = null; form.reset(); fields.disabled = true; filter.disabled = true; refresh.disabled = true; next.disabled = true;
  document.querySelector("[data-reviews]").replaceChildren(); document.querySelector("[data-application] dl").replaceChildren();
  document.querySelector("[data-application]").hidden = true; document.querySelector("[data-decision-section]").hidden = true;
}
async function request(url, options = {}) {
  const response = await fetch(url, { credentials: "same-origin", cache: "no-store", signal: requests.signal, ...options });
  if (response.status === 401 || response.status === 403) { denied = true; clearRestricted(); message.textContent = "当前身份没有操作权限，请重新登录并核验授权"; }
  const value = await response.json();
  if (!response.ok) {
    exactKeys(value, ["schema_version", "code", "message", "request_id", "resource_revision", "allowed_actions", "retryable", "operation_id", "details"]);
    if (value.schema_version !== "1.0.0" || !uuid.test(value.request_id) || typeof value.message !== "string") throw new Error("服务错误响应无效");
    message.textContent = value.message; throw new Error(value.code);
  }
  return { response, value };
}
const { value: account } = await request("/auth/account");
function validateCurrent(value) {
  exactKeys(value, ["user_id", "username", "display_name", "platform_role", "revision"]); validRevision(value.revision);
  if (!uuid.test(value.user_id) || value.platform_role !== "PLATFORM_ADMIN" || value.user_id !== account.user_id) {
    denied = true; clearRestricted(); throw new Error("企业审查需要当前平台管理员身份");
  }
}
validateCurrent(account);
async function requireCurrent() { const { value } = await request("/auth/account"); validateCurrent(value); }
async function csrf() {
  const { value } = await request("/auth/csrf"); exactKeys(value, ["header_name", "parameter_name", "token"]);
  if ([value.header_name, value.parameter_name, value.token].some(item => typeof item !== "string" || item.length === 0)) throw new Error("安全会话响应无效");
  return value;
}
function validateApplication(value) {
  exactKeys(value, ["schema_version", "registration_id", "operation_id", "application", "first_admin_binding", "first_admin_revision", "submitted_by", "created_at", "status", "revision", "content_sha256"]);
  exactKeys(value.application, ["schema_version", "legal_name", "jurisdiction", "legal_identifier", "first_admin_user_id", "authorization_basis", "authorization_attested"]);
  exactKeys(value.first_admin_binding, ["mode", "user_id", "issuer", "subject"]); validRevision(value.first_admin_revision);
  if (value.schema_version !== "1.0.0" || value.application.schema_version !== "1.0.0" || value.status !== "PENDING_REVIEW" || value.revision !== "1"
      || !uuid.test(value.registration_id) || !uuid.test(value.operation_id) || !uuid.test(value.submitted_by) || !sha.test(value.content_sha256)
      || !Number.isFinite(Date.parse(value.created_at)) || !uuid.test(value.application.first_admin_user_id) || value.application.authorization_attested !== true
      || value.first_admin_binding.mode !== "local" || value.first_admin_binding.user_id !== value.application.first_admin_user_id
      || value.first_admin_binding.issuer !== null || value.first_admin_binding.subject !== null) throw new Error("原始企业申请响应无效");
  for (const key of ["legal_name", "jurisdiction", "legal_identifier", "authorization_basis"]) {
    if (typeof value.application[key] !== "string" || value.application[key].length === 0) throw new Error("企业申请字段无效");
  }
}
function validateReceipt(value) {
  exactKeys(value, ["schema_version", "registration_id", "operation_id", "decision", "status", "revision", "registration_content_sha256", "request", "decided_by", "decided_at", "tenant_id", "member_id", "basis", "governance_grants", "content_sha256"]);
  exactKeys(value.request, ["schema_version", "decision", "expected_revision", "reason", "approval_attested", "governance_grants"]);
  if (value.schema_version !== "1.0.0" || value.request.schema_version !== "1.0.0" || value.revision !== "2" || value.request.expected_revision !== "1"
      || !uuid.test(value.registration_id) || !uuid.test(value.operation_id) || !uuid.test(value.decided_by) || !sha.test(value.content_sha256)
      || !sha.test(value.registration_content_sha256) || !Number.isFinite(Date.parse(value.decided_at)) || value.request.approval_attested !== true
      || typeof value.request.reason !== "string" || value.request.reason.length === 0 || value.request.decision !== value.decision
      || !Array.isArray(value.governance_grants) || !Array.isArray(value.request.governance_grants)
      || value.governance_grants.length !== value.request.governance_grants.length || value.governance_grants.length > 3) throw new Error("企业审查结果无效");
  if (value.decision === "APPROVE") {
    exactKeys(value.basis, ["version_id", "content_sha256"]);
    if (value.status !== "APPROVED" || !uuid.test(value.tenant_id) || !uuid.test(value.member_id) || !uuid.test(value.basis.version_id) || !sha.test(value.basis.content_sha256)) throw new Error("企业批准结果无效");
  } else if (value.decision !== "REJECT" || value.status !== "REJECTED" || value.tenant_id !== null || value.member_id !== null || value.basis !== null || value.governance_grants.length !== 0) throw new Error("企业拒绝结果无效");
  const ids = new Set(); const actions = new Set();
  for (let index = 0; index < value.governance_grants.length; index++) {
    const grant = value.governance_grants[index], input = value.request.governance_grants[index];
    exactKeys(grant, ["grant_id", "action", "condition", "valid_from", "valid_to", "revision"]); exactKeys(input, ["action", "condition", "valid_to"]);
    if (!uuid.test(grant.grant_id) || ids.has(grant.grant_id) || actions.has(grant.action) || !["member.manage", "role.manage", "project.create"].includes(grant.action)
        || grant.revision !== "1" || grant.valid_from !== value.decided_at || grant.action !== input.action || grant.valid_to !== input.valid_to
        || JSON.stringify(grant.condition) !== JSON.stringify(input.condition)
        || grant.valid_to !== null && !Number.isFinite(Date.parse(grant.valid_to))) throw new Error("治理授权结果无效");
    ids.add(grant.grant_id); actions.add(grant.action);
  }
  if (value.decision === "APPROVE" && !actions.has("member.manage")) throw new Error("首位管理员治理权限缺失");
}
function validateDetail(value) {
  exactKeys(value, ["schema_version", "application", "review", "status", "revision"]); validateApplication(value.application);
  if (value.schema_version !== "1.0.0") throw new Error("申请审查版本无效");
  if (value.review === null) { if (value.status !== "PENDING_REVIEW" || value.revision !== "1") throw new Error("待审查状态无效"); }
  else {
    validateReceipt(value.review);
    if (value.status !== value.review.status || value.revision !== value.review.revision || value.application.registration_id !== value.review.registration_id
        || value.application.content_sha256 !== value.review.registration_content_sha256) throw new Error("审查结果与原始申请不一致");
  }
}
const states = { PENDING_REVIEW: "待审查", APPROVED: "已批准", REJECTED: "已拒绝" };
function showDetail(value) {
  validateDetail(value); selected = value; form.reset();
  const section = document.querySelector("[data-application]"); const list = section.querySelector("dl"); list.replaceChildren();
  const application = value.application;
  const entries = [["申请编号", application.registration_id], ["企业法定名称", application.application.legal_name], ["登记地区", application.application.jurisdiction],
    ["企业登记标识", application.application.legal_identifier], ["首位管理员身份", application.application.first_admin_user_id], ["申请授权依据", application.application.authorization_basis],
    ["提交人员", application.submitted_by], ["审查状态", states[value.status]], ["申请时间", application.created_at]];
  if (value.review !== null) entries.push(["审查人员", value.review.decided_by], ["审查时间", value.review.decided_at], ["审查依据与原因", value.review.request.reason],
    ["企业空间身份", value.review.tenant_id === null ? "未启用企业" : value.review.tenant_id], ["批准治理动作", value.review.governance_grants.map(item => item.action).join("、")]);
  for (const [name, text] of entries) { const dt = document.createElement("dt"), dd = document.createElement("dd"); dt.textContent = name; dd.textContent = text; list.append(dt, dd); }
  section.hidden = false; document.querySelector("[data-decision-section]").hidden = value.review !== null;
  fields.disabled = denied || value.review !== null; document.querySelector("[data-governance]").hidden = true;
}
async function readPage(reset) {
  await requireCurrent(); const url = new URL(endpoint, window.location.origin); url.searchParams.set("limit", "50"); url.searchParams.set("status", filter.value);
  if (!reset && cursor !== null) url.searchParams.set("cursor", cursor);
  const { response, value } = await request(url, { headers: { "X-Schema-Version": "1.0.0" } }); validateEnvelope(response, value);
  exactKeys(value.data, ["schema_version", "items", "next_cursor"]);
  if (value.resource_revision !== null || value.data.schema_version !== "1.0.0" || !Array.isArray(value.data.items) || value.data.items.length > 50
      || value.data.next_cursor !== null && !uuid.test(value.data.next_cursor)) throw new Error("审查列表响应无效");
  const list = document.querySelector("[data-reviews]"); if (reset) list.replaceChildren();
  for (const item of value.data.items) {
    validateDetail(item); if (item.status !== filter.value) throw new Error("审查筛选结果无效");
    const article = document.createElement("article"), title = document.createElement("p"), button = document.createElement("button");
    title.textContent = item.application.application.legal_name + " · " + states[item.status]; button.type = "button"; button.textContent = "读取申请与审查详情";
    button.addEventListener("click", async () => {
      await requireCurrent(); const result = await request(endpoint + "/" + item.application.registration_id, { headers: { "X-Schema-Version": "1.0.0" } });
      validateEnvelope(result.response, result.value); validateDetail(result.value.data);
      if (result.value.resource_revision !== result.value.data.revision || result.value.data.application.registration_id !== item.application.registration_id) throw new Error("审查详情身份无效");
      showDetail(result.value.data);
    }); article.append(title, button); list.append(article);
  }
  if (reset && value.data.items.length === 0) list.textContent = "当前筛选条件没有申请";
  cursor = value.data.next_cursor; next.hidden = cursor === null;
}
for (const [value, title] of [["ENTERPRISE", "企业"], ["PROJECT", "项目"], ["MEMBER", "成员"], ["SOURCE", "资料"], ["TASK", "任务"], ["DOCUMENT", "文档"],
    ["ARTIFACT", "成品"], ["CONFIGURATION", "配置"], ["TEMPLATE", "模板"], ["RULE", "规则"], ["BUDGET", "预算"], ["EVALUATION", "评测"], ["AUDIT", "审计"], ["REPORT", "报表"]]) {
  const label = document.createElement("label"), input = document.createElement("input"); label.className = "attestation";
  input.type = "checkbox"; input.name = "resource_kind"; input.value = value; label.append(input, document.createTextNode(title)); document.querySelector("[data-resource-kinds]").append(label);
}
for (const input of form.querySelectorAll("[data-code-point-limit]")) input.addEventListener("input", () => input.setCustomValidity(Array.from(input.value).length > Number(input.dataset.codePointLimit) ? "审查原因超过字符限制" : ""));
form.elements.namedItem("decision").addEventListener("change", () => {
  const approved = form.elements.namedItem("decision").value === "APPROVE"; document.querySelector("[data-governance]").hidden = !approved;
  form.elements.namedItem("valid_to").required = approved && !form.elements.namedItem("unlimited_validity").checked;
});
form.elements.namedItem("unlimited_validity").addEventListener("change", () => {
  form.elements.namedItem("valid_to").required = !form.elements.namedItem("unlimited_validity").checked;
});
form.querySelector("[data-role-id]").addEventListener("click", () => {
  const input = form.elements.namedItem("role_ids"); input.value += (input.value.length === 0 ? "" : "\n") + crypto.randomUUID();
});
function storageKey(id) { return "medical.enterprise.review:" + account.user_id + ":" + id; }
form.querySelector("[data-abandon]").addEventListener("click", () => { if (selected === null) throw new Error("需要选择申请"); sessionStorage.removeItem(storageKey(selected.application.registration_id)); message.textContent = "已清除待提交请求身份"; });
function lines(name, pattern) {
  const input = form.elements.namedItem(name); const values = input.value === "" ? [] : input.value.split(/\r?\n/);
  if (values.length > 1000 || new Set(values).size !== values.length || values.some(value => !pattern.test(value))) throw new Error("批准的动作或岗位标识格式无效");
  return values;
}
form.addEventListener("submit", async event => {
  event.preventDefault(); if (busy || selected === null || selected.review !== null) throw new Error("当前申请不能提交审查决定");
  busy = true; fields.disabled = true; message.textContent = "正在保存决定；连接中断时请使用原请求确认结果";
  try {
    await requireCurrent(); const decision = form.elements.namedItem("decision").value;
    const body = { schema_version: "1.0.0", decision, expected_revision: selected.revision, reason: form.elements.namedItem("reason").value,
      approval_attested: form.elements.namedItem("approval_attested").checked, governance_grants: [] };
    if (decision === "APPROVE") {
      const actions = Array.from(form.querySelectorAll("[name=action]:checked"), input => input.value);
      if (!actions.includes("member.manage") || !form.elements.namedItem("local_identity").checked) { message.textContent = "批准需要明确选择成员治理及本地身份许可"; throw new Error("首位管理员治理权限缺失"); }
      const purposes = [...Array.from(form.querySelectorAll("[name=purpose]:checked"), input => input.value),
        ...lines("additional_purposes", /^[A-Z][A-Z0-9_]{0,63}$/)];
      if (new Set(purposes).size !== purposes.length || purposes.length > 1000) throw new Error("批准用途需要唯一并满足数量限制");
      const allPurposes = form.elements.namedItem("all_purposes").checked;
      if (allPurposes && purposes.length > 0) throw new Error("全部用途与指定用途不能同时选择");
      for (const action of actions) {
        const purpose = { "member.manage": "PERMISSION_ADMINISTRATION", "role.manage": "ROLE_ADMINISTRATION", "project.create": "PROJECT_ADMINISTRATION" }[action];
        if (!allPurposes && !purposes.includes(purpose)) { message.textContent = "选择的治理动作需要批准对应用途"; throw new Error("治理用途缺失"); }
      }
      const condition = { schema_version: "1.0.0", identity_modes: ["local"], purposes: allPurposes ? null : purposes, delegation: {
        actions: lines("delegated_actions", /^[a-z][a-z0-9_.-]{0,63}$/), role_ids: lines("role_ids", uuid),
        resource_kinds: Array.from(form.querySelectorAll("[name=resource_kind]:checked"), input => input.value),
        project_ids: form.elements.namedItem("all_projects").checked ? null : [], resource_ids: form.elements.namedItem("all_resources").checked ? null : [] } };
      const validTo = form.elements.namedItem("unlimited_validity").checked ? null : new Date(form.elements.namedItem("valid_to").value).toISOString();
      body.governance_grants = actions.map(action => ({ action, condition, valid_to: validTo }));
    }
    const id = selected.application.registration_id;
    const digest = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify(body)))), byte => byte.toString(16).padStart(2, "0")).join("");
    const saved = sessionStorage.getItem(storageKey(id)); let pending;
    if (saved === null) { pending = { key: crypto.randomUUID(), digest }; sessionStorage.setItem(storageKey(id), JSON.stringify(pending)); }
    else { pending = JSON.parse(saved); exactKeys(pending, ["key", "digest"]); if (!uuid.test(pending.key) || !sha.test(pending.digest) || pending.digest !== digest) { message.textContent = "原请求内容不同，需要明确清除请求身份以后重新提交"; throw new Error("审查请求身份不匹配"); } }
    const token = await csrf(); const result = await request(endpoint + "/" + id + "/decisions", { method: "POST", headers: {
      "Content-Type": "application/json", "X-Schema-Version": "1.0.0", "Idempotency-Key": pending.key, [token.header_name]: token.token }, body: JSON.stringify(body) });
    validateEnvelope(result.response, result.value); validateReceipt(result.value.data);
    if (result.value.resource_revision !== "2" || result.value.data.registration_id !== id || result.value.data.decided_by !== account.user_id || result.value.data.decision !== decision) throw new Error("审查决定响应身份无效");
    sessionStorage.removeItem(storageKey(id)); form.reset();
    const actual = await request(endpoint + "/" + id, { headers: { "X-Schema-Version": "1.0.0" } }); validateEnvelope(actual.response, actual.value);
    if (actual.value.data.application.registration_id !== id || actual.value.resource_revision !== "2") throw new Error("审查保存重读身份无效");
    showDetail(actual.value.data); await readPage(true); message.textContent = "审查决定已经保存并重新读取";
  } finally { busy = false; fields.disabled = denied || selected === null || selected.review !== null; }
});
refresh.addEventListener("click", async () => { refresh.disabled = true; try { await readPage(true); } finally { refresh.disabled = denied; } });
next.addEventListener("click", async () => { next.disabled = true; try { await readPage(false); } finally { next.disabled = denied; } });
window.addEventListener("pagehide", () => { requests.abort(); clearRestricted(); });
await csrf(); await readPage(true); filter.disabled = false; refresh.disabled = false; next.disabled = false;
message.textContent = "可以读取并审查真实企业申请";
