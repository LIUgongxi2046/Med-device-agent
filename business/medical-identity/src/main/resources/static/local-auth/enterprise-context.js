"use strict";

import { exactKeys, validRevision, validateEnvelope } from "./management-request.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const path = "/api/session/tenant-selections";
const prefix = "medical.context.selection:";
const invalidation = "CONTEXT_INVALIDATED";
const channel = new BroadcastChannel("medical.context");
const status = document.querySelector("[data-status]");
const refresh = document.querySelector("[data-refresh]");
const protectedContent = document.querySelector("[data-protected]");
const form = document.querySelector("[data-selection]");
const select = form.elements.namedItem("tenant_id");
const submit = form.querySelector("[type=submit]");
const pendingContent = document.querySelector("[data-pending]");
const confirmation = document.querySelector("[data-confirm-resolution]");
const clear = document.querySelector("[data-clear-pending]");
let state = null;
let candidates = null;
let securityCsrf = null;
let actor = null;
let generation = 0;
let readController = null;
let writing = false;
let closed = false;

function requireId(value) {
  if (typeof value !== "string" || !uuid.test(value)) throw new Error("服务端身份编号无效");
}
function requireText(value, maximum, nullable = false) {
  if (nullable && value === null) return;
  if (typeof value !== "string" || Array.from(value).length < 1 || Array.from(value).length > maximum) throw new Error("服务端文本字段无效");
}
function requireTime(value) {
  if (typeof value !== "string" || !value.endsWith("Z") || !Number.isFinite(Date.parse(value))) throw new Error("成员有效期限无效");
}
function validateContext(value) {
  exactKeys(value, ["tenant_id", "context_revision", "auth_epoch", "governance_epoch"]);
  requireId(value.tenant_id);
  for (const field of ["context_revision", "auth_epoch", "governance_epoch"]) validRevision(value[field]);
}
function validateMembers(memberships) {
  if (!Array.isArray(memberships)) throw new Error("成员列表无效");
  const seen = new Set();
  for (const member of memberships) {
    exactKeys(member, ["tenant_id", "member_id", "status", "valid_from", "valid_to", "revision"]);
    requireId(member.tenant_id); requireId(member.member_id); validRevision(member.revision); requireTime(member.valid_from);
    if (member.valid_to !== null) requireTime(member.valid_to);
    if (!["INVITED", "ACTIVE"].includes(member.status) || seen.has(member.tenant_id)
        || (member.valid_to !== null && Date.parse(member.valid_to) <= Date.parse(member.valid_from))) throw new Error("成员状态或关联无效");
    seen.add(member.tenant_id);
  }
}
function validateOptions(response, envelope) {
  validateEnvelope(response, envelope);
  const value = envelope.data;
  exactKeys(value, ["schema_version", "user_id", "expected_context_revision", "selected_tenant_id", "memberships"]);
  requireId(value.user_id); validRevision(value.expected_context_revision); validateMembers(value.memberships);
  if (value.selected_tenant_id !== null) requireId(value.selected_tenant_id);
  if (value.schema_version !== "1.0.0" || envelope.resource_revision !== value.expected_context_revision
      || (value.selected_tenant_id === null) !== (value.expected_context_revision === "0")) throw new Error("企业选择候选状态无效");
  return value;
}
function validateSession(response, envelope) {
  validateEnvelope(response, envelope);
  const value = envelope.data;
  exactKeys(value, ["schema_version", "user", "memberships", "current_context", "csrf", "action_summary", "invitation_entry"]);
  if (value.schema_version !== "1.0.0") throw new Error("业务会话版本无效");
  exactKeys(value.user, ["user_id", "username", "display_name", "platform_role"]);
  requireId(value.user.user_id); requireText(value.user.username, 128, true); requireText(value.user.display_name, 128, true);
  if (!["USER", "PLATFORM_ADMIN", null].includes(value.user.platform_role)) throw new Error("平台角色无效");
  exactKeys(value.csrf, ["header_name", "token"]);
  if (value.csrf.header_name !== "X-CSRF-TOKEN" || typeof value.csrf.token !== "string" || value.csrf.token.length === 0) throw new Error("安全会话无效");
  exactKeys(value.invitation_entry, ["method", "path_template"]);
  if (value.invitation_entry.method !== "POST" || value.invitation_entry.path_template !== "/api/member-invitations/{id}/acceptances") throw new Error("邀请入口无效");
  validateMembers(value.memberships);
  if (!Array.isArray(value.action_summary)) throw new Error("动作列表无效");
  if (value.current_context !== null) {
    validateContext(value.current_context);
    if (envelope.resource_revision !== value.current_context.context_revision
        || !value.memberships.some(member => member.tenant_id === value.current_context.tenant_id && member.status === "ACTIVE")) throw new Error("当前企业与成员身份不一致");
  } else if (envelope.resource_revision !== null || value.action_summary.length !== 0) throw new Error("未选择企业的权限状态无效");
  for (const action of value.action_summary) {
    exactKeys(action, ["action", "project_id", "resource_kind", "resource_id"]);
    requireText(action.action, 64); requireText(action.resource_kind, 32);
    if (action.project_id !== null) requireId(action.project_id);
    if (action.resource_id !== null) requireId(action.resource_id);
  }
  return value;
}
function validateError(response, value) {
  exactKeys(value, ["schema_version", "code", "message", "request_id", "resource_revision", "allowed_actions", "retryable", "operation_id", "details"]);
  requireId(value.request_id); requireText(value.code, 128); requireText(value.message, 4096);
  if (value.schema_version !== "1.0.0" || response.headers.get("X-Schema-Version") !== "1.0.0"
      || response.headers.get("X-Request-Id") !== value.request_id || !Array.isArray(value.allowed_actions)
      || typeof value.retryable !== "boolean") throw new Error("业务错误响应无效");
  if (value.resource_revision !== null) validRevision(value.resource_revision);
  if (value.operation_id !== null) requireId(value.operation_id);
  return value;
}
function storedPending() {
  if (actor === null) return null;
  const saved = sessionStorage.getItem(prefix + actor);
  if (saved === null) return null;
  const value = JSON.parse(saved);
  exactKeys(value, ["key", "revision", "digest"]);
  requireId(value.key); validRevision(value.revision);
  if (typeof value.digest !== "string" || !/^[0-9a-f]{64}$/.test(value.digest)) throw new Error("原请求摘要无效");
  return value;
}
function showPending() {
  const pending = storedPending();
  pendingContent.hidden = pending === null;
  document.querySelector("[data-request-key]").textContent = pending === null ? "" : pending.key;
  document.querySelector("[data-request-revision]").textContent = pending === null ? "" : pending.revision;
  confirmation.checked = false;
  clear.disabled = true;
}
function clearVisible() {
  state = null;
  candidates = null; securityCsrf = null;
  protectedContent.hidden = true;
  select.replaceChildren(new Option("请选择企业空间", ""));
  select.disabled = true; submit.disabled = true;
  for (const attribute of ["data-user", "data-context", "data-revision", "data-auth-epoch", "data-governance-epoch", "data-memberships", "data-actions"]) {
    document.querySelector("[" + attribute + "]").replaceChildren();
  }
  pendingContent.hidden = true;
  document.querySelector("[data-request-key]").textContent = "";
  document.querySelector("[data-request-revision]").textContent = "";
  confirmation.checked = false; clear.disabled = true;
  document.querySelector("[data-registration]").hidden = true;
  document.querySelector("[data-action-section]").hidden = true;
}
function synchronizeControls() {
  const allowed = candidates !== null && securityCsrf !== null && !writing && !document.hidden
    && candidates.expected_context_revision !== "18446744073709551615" && candidates.memberships.some(member => member.status === "ACTIVE");
  select.disabled = !allowed;
  submit.disabled = !allowed || select.value === "";
  clear.disabled = candidates === null || writing || !confirmation.checked || storedPending() === null;
  refresh.disabled = writing || closed;
}
function displayOptions(options) {
  candidates = options;
  document.querySelector("[data-user]").textContent = options.user_id;
  document.querySelector("[data-context]").textContent = options.selected_tenant_id === null ? "尚未选择企业空间" : "需要确认企业选择：" + options.selected_tenant_id;
  document.querySelector("[data-revision]").textContent = options.expected_context_revision;
  document.querySelector("[data-auth-epoch]").textContent = "需要重新确认企业选择";
  document.querySelector("[data-governance-epoch]").textContent = "需要重新确认企业选择";
  const memberships = document.querySelector("[data-memberships]");
  memberships.replaceChildren(); select.replaceChildren(new Option("请选择企业空间", ""));
  if (options.memberships.length === 0) memberships.textContent = "当前账户没有企业成员身份";
  for (const member of options.memberships) {
    const entry = document.createElement("p"); entry.className = "member-entry";
    entry.textContent = "企业：" + member.tenant_id + "；成员：" + member.member_id + "；状态：" + (member.status === "ACTIVE" ? "有效" : "等待接受邀请")
      + "；版本：" + member.revision + "；有效期：" + member.valid_from + " 至 " + (member.valid_to ?? "没有终止期限");
    memberships.append(entry);
    if (member.status === "ACTIVE") select.append(new Option(member.tenant_id, member.tenant_id));
  }
  protectedContent.hidden = false; showPending(); synchronizeControls();
}
function display(value, options) {
  displayOptions(options); state = value;
  document.querySelector("[data-user]").textContent = value.user.username ?? value.user.display_name ?? value.user.user_id;
  const context = value.current_context;
  document.querySelector("[data-context]").textContent = context === null ? "尚未选择企业空间" : context.tenant_id;
  document.querySelector("[data-revision]").textContent = context === null ? "0" : context.context_revision;
  document.querySelector("[data-auth-epoch]").textContent = context === null ? "尚未选择企业空间" : context.auth_epoch;
  document.querySelector("[data-governance-epoch]").textContent = context === null ? "尚未选择企业空间" : context.governance_epoch;
  const actions = document.querySelector("[data-actions]");
  if (value.action_summary.length === 0) actions.textContent = "当前企业没有可用动作";
  for (const action of value.action_summary) {
    const entry = document.createElement("p"); entry.className = "action-entry";
    entry.textContent = action.action + "；资源类型：" + action.resource_kind + "；项目：" + (action.project_id ?? "企业范围")
      + "；资源：" + (action.resource_id ?? "范围内资源");
    actions.append(entry);
  }
  document.querySelector("[data-registration]").hidden = value.user.platform_role !== "PLATFORM_ADMIN";
  document.querySelector("[data-action-section]").hidden = false;
  protectedContent.hidden = false; showPending(); synchronizeControls();
}
async function readOptions(controller, current) {
  const response = await fetch("/api/session/tenant-selection-options", { credentials: "same-origin", cache: "no-store", signal: controller.signal,
    headers: { "X-Schema-Version": "1.0.0" } });
  if (current !== generation || closed) return null;
  if (response.status === 401) {
    identityInvalidated("登录会话已经失效，请重新登录", true); window.location.replace("/auth/login"); return null;
  }
  const envelope = await response.json();
  if (current !== generation || closed) return null;
  if (!response.ok) {
    const error = validateError(response, envelope);
    identityInvalidated(error.message + "；请求编号：" + error.request_id, response.status === 403); return null;
  }
  const options = validateOptions(response, envelope);
  if (actor !== null && actor !== options.user_id) {
    sessionStorage.removeItem(prefix + actor); identityInvalidated("当前账户已经变化，请重新进入企业空间"); closed = true; return null;
  }
  actor = options.user_id;
  return options;
}
function identityInvalidated(message, forgetPending = false) {
  generation += 1;
  if (readController !== null) readController.abort();
  if (forgetPending && actor !== null) sessionStorage.removeItem(prefix + actor);
  clearVisible(); status.textContent = message; synchronizeControls();
}
async function readSession() {
  if (closed || writing) return null;
  const current = ++generation;
  if (readController !== null) readController.abort();
  const controller = new AbortController(); readController = controller;
  clearVisible(); refresh.disabled = true; status.textContent = "正在读取企业身份";
  try {
    let options = await readOptions(controller, current);
    if (options === null) return null;
    const csrfResponse = await fetch("/auth/csrf", { credentials: "same-origin", cache: "no-store", signal: controller.signal });
    if (current !== generation || closed) return null;
    if (!csrfResponse.ok) throw new Error("安全会话读取失败");
    const csrf = await csrfResponse.json();
    exactKeys(csrf, ["header_name", "parameter_name", "token"]);
    if (csrf.header_name !== "X-CSRF-TOKEN" || csrf.parameter_name !== "_csrf" || typeof csrf.token !== "string" || csrf.token.length === 0) throw new Error("安全会话响应无效");
    if (current !== generation || closed) return null;
    securityCsrf = csrf;
    const response = await fetch("/api/session", { credentials: "same-origin", cache: "no-store", signal: controller.signal,
      headers: { "X-Schema-Version": "1.0.0" } });
    if (current !== generation || closed) return null;
    if (response.status === 401) {
      identityInvalidated("登录会话已经失效，请重新登录", true); window.location.replace("/auth/login"); return null;
    }
    const envelope = await response.json();
    if (current !== generation || closed) return null;
    if (!response.ok) {
      const error = validateError(response, envelope);
      if ((response.status === 409 && ["SCOPE_STALE", "CONTEXT_CHANGED"].includes(error.code))
          || response.status === 403 && error.code === "ACCESS_DENIED") {
        // 旧企业准入失败仍保持失败状态；独立本人候选接口供明确重新选择使用。
        options = await readOptions(controller, current);
        if (options === null || document.hidden) return null;
        displayOptions(options);
        status.textContent = error.message + "；请明确重新选择企业空间；请求编号：" + error.request_id;
        return null;
      }
      identityInvalidated(error.message + "；请求编号：" + error.request_id, response.status === 403);
      return null;
    }
    const value = validateSession(response, envelope);
    if (value.user.user_id !== options.user_id || (value.current_context?.tenant_id ?? null) !== options.selected_tenant_id
        || (value.current_context?.context_revision ?? "0") !== options.expected_context_revision
        || value.memberships.length !== options.memberships.length
        || value.memberships.some((member, index) => Object.keys(member).some(field => member[field] !== options.memberships[index][field]))) {
      identityInvalidated("企业候选与业务上下文已经变化，请重新读取"); return null;
    }
    if (document.hidden) return null;
    display(value, options);
    status.textContent = value.memberships.length === 0 ? "当前账户需要取得企业成员授权" : "企业身份已经读取，请明确选择企业空间";
    return value;
  } finally {
    if (current === generation && !closed) refresh.disabled = writing;
  }
}
async function digest(body) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify(body)));
  return Array.from(new Uint8Array(bytes), value => value.toString(16).padStart(2, "0")).join("");
}
form.addEventListener("submit", async event => {
  event.preventDefault();
  if (writing || candidates === null || securityCsrf === null || document.hidden || !form.reportValidity()) return;
  const target = select.value;
  requireId(target);
  if (!candidates.memberships.some(member => member.tenant_id === target && member.status === "ACTIVE")) throw new Error("目标企业没有当前有效成员身份");
  writing = true; synchronizeControls();
  const original = candidates;
  const csrf = securityCsrf;
  const current = generation;
  try {
    let pending = storedPending();
    const revision = pending === null ? original.expected_context_revision : pending.revision;
    if (BigInt(revision) === 18446744073709551615n) throw new Error("上下文版本达到上限，禁止提交选择");
    const body = { tenant_id: target, expected_context_revision: revision };
    const sha256 = await digest(body);
    if (current !== generation || closed) return;
    if (pending === null) {
      pending = { key: crypto.randomUUID(), revision, digest: sha256 };
      sessionStorage.setItem(prefix + actor, JSON.stringify(pending));
    } else if (pending.digest !== sha256) {
      status.textContent = "待确认请求的企业或原版本不同，请核查原选择结果";
      showPending(); return;
    }
    showPending(); status.textContent = "正在提交企业选择；结果未确认时请保留原请求身份";
    const response = await fetch(path, { method: "POST", credentials: "same-origin", cache: "no-store", headers: {
      "Content-Type": "application/json", "X-Schema-Version": "1.0.0", "X-Context-Revision": revision,
      "Idempotency-Key": pending.key, [csrf.header_name]: csrf.token
    }, body: JSON.stringify(body) });
    if (current !== generation || closed) return;
    if (response.status === 401) {
      identityInvalidated("登录会话已经失效，请重新登录", true); window.location.replace("/auth/login"); return;
    }
    const envelope = await response.json();
    if (current !== generation || closed) return;
    if (!response.ok) {
      const error = validateError(response, envelope);
      identityInvalidated(error.message + "；请求编号：" + error.request_id, response.status === 403);
      return;
    }
    validateEnvelope(response, envelope);
    exactKeys(envelope.data, ["schema_version", "operation_id", "current_context"]);
    requireId(envelope.data.operation_id); validateContext(envelope.data.current_context);
    const selected = envelope.data.current_context;
    if (envelope.data.schema_version !== "1.0.0" || selected.tenant_id !== target || selected.context_revision !== (BigInt(revision) + 1n).toString()
        || envelope.resource_revision !== selected.context_revision) throw new Error("企业选择结果与原请求不一致");
    writing = false;
    const reread = await readSession();
    if (reread === null) return;
    if (reread.current_context === null || Object.keys(selected).some(field => reread.current_context[field] !== selected[field])) {
      identityInvalidated("企业上下文已经变化，请核查原请求结果并重新读取"); return;
    }
    sessionStorage.removeItem(prefix + actor); showPending(); channel.postMessage(invalidation);
    status.textContent = "企业选择已经保存，当前权限已从服务端重新读取";
  } finally { writing = false; synchronizeControls(); }
});
select.addEventListener("change", synchronizeControls);
confirmation.addEventListener("change", synchronizeControls);
clear.addEventListener("click", async () => {
  if (!confirmation.checked || writing || candidates === null || storedPending() === null) return;
  await readSession();
  if (candidates === null) return;
  sessionStorage.removeItem(prefix + actor); showPending();
  status.textContent = "已清除经过核查的请求身份，请明确选择企业空间";
});
refresh.addEventListener("click", readSession);
channel.addEventListener("message", async event => {
  if (event.data !== invalidation) throw new Error("企业上下文通知无效");
  identityInvalidated("其他标签页更新了企业上下文，正在重新核查");
  if (!document.hidden && !writing) await readSession();
});
document.addEventListener("visibilitychange", async () => {
  if (document.hidden) identityInvalidated("重新显示页面时需要核查企业身份");
  else if (!closed && !writing) await readSession();
});
window.addEventListener("pagehide", () => {
  closed = true; identityInvalidated("页面已经离开"); channel.close(); actor = null;
});
window.addEventListener("pageshow", event => { if (event.persisted) window.location.reload(); });
window.addEventListener("unhandledrejection", event => {
  if (closed || event.reason?.name === "AbortError") return;
  identityInvalidated(event.reason instanceof TypeError
    ? "连接请求未完成，请恢复连接后重新读取企业身份"
    : "企业操作未确认，请核查响应及原请求身份");
});
await readSession();
