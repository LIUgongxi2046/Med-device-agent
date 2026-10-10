"use strict";

import { exactKeys, validRevision, validateEnvelope, validateAccount } from "./management-request.js";

const path = "/api/enterprise-registrations";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const hashPattern = /^[0-9a-f]{64}$/;
const status = document.querySelector("[data-status]");
const form = document.querySelector("[data-registration]");
const fields = form.querySelector("fieldset");
const nextButton = document.querySelector("[data-next]");
const requests = new AbortController();
let cursor = null;
let busy = false;
let denied = false;

for (const input of form.querySelectorAll("[data-code-point-limit]")) {
  input.addEventListener("input", () => {
    const limit = Number(input.dataset.codePointLimit);
    if (!Number.isSafeInteger(limit) || limit < 1) throw new Error("表单字符限制无效");
    input.setCustomValidity(Array.from(input.value).length > limit ? "输入内容最多允许" + limit + "个字符" : "");
  });
}

function clearRestricted() {
  form.reset(); form.elements.namedItem("first_admin_user_id").replaceChildren();
  document.querySelector("[data-registrations]").replaceChildren();
  document.querySelector("[data-detail] dl").replaceChildren(); document.querySelector("[data-detail]").hidden = true;
}

async function request(url, options = {}) {
  const response = await fetch(url, { credentials: "same-origin", cache: "no-store", signal: requests.signal, ...options });
  if (response.status === 401 || response.status === 403) {
    denied = true;
    clearRestricted();
    fields.disabled = true; nextButton.disabled = true;
    status.textContent = "当前账户无法执行该操作，请重新登录并核验权限";
  }
  const value = await response.json();
  if (!response.ok) {
    exactKeys(value, ["schema_version", "code", "message", "request_id", "resource_revision", "allowed_actions", "retryable", "operation_id", "details"]);
    if (value.schema_version !== "1.0.0" || typeof value.message !== "string" || !uuid.test(value.request_id)) throw new Error("服务错误响应无效");
    status.textContent = value.message;
    throw new Error(value.code);
  }
  return { response, value };
}

const { value: account } = await request("/auth/account");
exactKeys(account, ["user_id", "username", "display_name", "platform_role", "revision"]);
if (!uuid.test(account.user_id) || account.platform_role !== "PLATFORM_ADMIN") throw new Error("企业登记需要当前平台管理员权限");
validRevision(account.revision);
const storageKey = "medical.enterprise.registration:" + account.user_id;

async function requireCurrent() {
  const { value } = await request("/auth/account");
  exactKeys(value, ["user_id", "username", "display_name", "platform_role", "revision"]);
  if (value.user_id !== account.user_id || value.platform_role !== "PLATFORM_ADMIN") {
    denied = true;
    clearRestricted();
    fields.disabled = true; status.textContent = "当前账户身份已变化，请重新打开企业登记页面";
    throw new Error("企业登记账户身份已变化");
  }
}

async function securityToken() {
  const { value } = await request("/auth/csrf");
  exactKeys(value, ["header_name", "parameter_name", "token"]);
  if (typeof value.header_name !== "string" || typeof value.parameter_name !== "string" || typeof value.token !== "string"
      || value.header_name.length === 0 || value.parameter_name.length === 0 || value.token.length === 0) throw new Error("安全会话响应无效");
  return value;
}

function validateDetail(value) {
  exactKeys(value, ["schema_version", "registration_id", "operation_id", "application", "first_admin_binding", "first_admin_revision", "submitted_by", "created_at", "status", "revision", "content_sha256"]);
  exactKeys(value.application, ["schema_version", "legal_name", "jurisdiction", "legal_identifier", "first_admin_user_id", "authorization_basis", "authorization_attested"]);
  exactKeys(value.first_admin_binding, ["mode", "user_id", "issuer", "subject"]);
  if (value.schema_version !== "1.0.0" || value.application.schema_version !== "1.0.0" || value.status !== "PENDING_REVIEW"
      || value.revision !== "1" || value.submitted_by !== account.user_id || !uuid.test(value.registration_id) || !uuid.test(value.operation_id)
      || !uuid.test(value.application.first_admin_user_id) || value.first_admin_binding.mode !== "local"
      || value.first_admin_binding.user_id !== value.application.first_admin_user_id || value.first_admin_binding.issuer !== null
      || value.first_admin_binding.subject !== null || value.application.authorization_attested !== true
      || !hashPattern.test(value.content_sha256) || !Number.isFinite(Date.parse(value.created_at))) throw new Error("企业登记响应无效");
  for (const key of ["legal_name", "jurisdiction", "legal_identifier", "authorization_basis"]) {
    if (typeof value.application[key] !== "string" || value.application[key].length === 0) throw new Error("企业登记字段无效");
  }
  validRevision(value.first_admin_revision);
}

function showDetail(value) {
  validateDetail(value);
  const section = document.querySelector("[data-detail]");
  const list = section.querySelector("dl"); list.replaceChildren();
  for (const [name, text] of [["申请编号", value.registration_id], ["企业法定名称", value.application.legal_name],
      ["登记地区", value.application.jurisdiction], ["企业登记标识", value.application.legal_identifier],
      ["指定管理员身份", value.application.first_admin_user_id], ["授权依据", value.application.authorization_basis],
      ["申请状态", "待审查"], ["提交时间", value.created_at], ["内容摘要", value.content_sha256]]) {
    const term = document.createElement("dt"); term.textContent = name;
    const description = document.createElement("dd"); description.textContent = text; list.append(term, description);
  }
  section.hidden = false;
}

async function readPage(reset) {
  await requireCurrent();
  const url = new URL(path, window.location.origin); url.searchParams.set("limit", "50");
  if (!reset && cursor !== null) url.searchParams.set("cursor", cursor);
  const { response, value } = await request(url, { headers: { "X-Schema-Version": "1.0.0" } });
  validateEnvelope(response, value); exactKeys(value.data, ["schema_version", "items", "next_cursor"]);
  if (value.resource_revision !== null || value.data.schema_version !== "1.0.0" || !Array.isArray(value.data.items)
      || value.data.items.length > 50 || value.data.next_cursor !== null && !uuid.test(value.data.next_cursor)) throw new Error("企业登记列表无效");
  const list = document.querySelector("[data-registrations]");
  if (reset) list.replaceChildren();
  for (const item of value.data.items) {
    validateDetail(item);
    const article = document.createElement("article");
    const title = document.createElement("p"); title.textContent = item.application.legal_name + " · 待审查";
    const button = document.createElement("button"); button.type = "button"; button.textContent = "读取申请详情";
    button.addEventListener("click", async () => {
      await requireCurrent();
      const result = await request(path + "/" + item.registration_id, { headers: { "X-Schema-Version": "1.0.0" } });
      validateEnvelope(result.response, result.value);
      if (result.value.data.registration_id !== item.registration_id || result.value.resource_revision !== result.value.data.revision) throw new Error("申请详情身份无效");
      showDetail(result.value.data);
    });
    article.append(title, button); list.append(article);
  }
  if (reset && value.data.items.length === 0) list.textContent = "尚无本人提交的申请";
  cursor = value.data.next_cursor; nextButton.hidden = cursor === null; nextButton.disabled = false;
}

const select = form.elements.namedItem("first_admin_user_id");
let accountCursor = null;
const seen = new Set();
do {
  const url = new URL("/api/local-accounts", window.location.origin); url.searchParams.set("limit", "100");
  if (accountCursor !== null) url.searchParams.set("cursor", accountCursor);
  const { response, value } = await request(url, { headers: { "X-Schema-Version": "1.0.0" } });
  validateEnvelope(response, value); exactKeys(value.data, ["schema_version", "items", "next_cursor"]);
  if (value.data.schema_version !== "1.0.0" || !Array.isArray(value.data.items)) throw new Error("账户候选响应无效");
  for (const item of value.data.items) {
    validateAccount(item);
    if (seen.has(item.user_id)) throw new Error("账户候选身份重复");
    seen.add(item.user_id);
    if (item.status === "ACTIVE") {
      const option = document.createElement("option"); option.value = item.user_id;
      option.textContent = item.display_name + " · " + item.username + " · " + item.user_id; select.append(option);
    }
  }
  const next = value.data.next_cursor;
  if (next !== null && (!uuid.test(next) || next === accountCursor || !seen.has(next))) throw new Error("账户候选分页无效");
  accountCursor = next;
} while (accountCursor !== null);

await readPage(true);
await securityToken();
fields.disabled = false;
status.textContent = sessionStorage.getItem(storageKey) === null ? "可以填写企业登记申请" : "原请求身份已保存，请重新填写相同内容确认结果，或者明确清除原请求";

nextButton.addEventListener("click", async () => {
  nextButton.disabled = true;
  try { await readPage(false); } finally { nextButton.disabled = denied; }
});
form.querySelector("[data-abandon]").addEventListener("click", () => {
  sessionStorage.removeItem(storageKey); status.textContent = "已清除待提交请求身份";
});
form.addEventListener("submit", async event => {
  event.preventDefault();
  if (busy) throw new Error("企业登记请求正在执行");
  busy = true; fields.disabled = true;
  status.textContent = "正在提交申请；连接中断时原请求身份继续保留";
  try {
    await requireCurrent();
    const body = { schema_version: "1.0.0", legal_name: form.elements.namedItem("legal_name").value,
      jurisdiction: form.elements.namedItem("jurisdiction").value, legal_identifier: form.elements.namedItem("legal_identifier").value,
      first_admin_user_id: select.value, authorization_basis: form.elements.namedItem("authorization_basis").value,
      authorization_attested: form.elements.namedItem("authorization_attested").checked };
    const digest = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(JSON.stringify(body)))),
      byte => byte.toString(16).padStart(2, "0")).join("");
    const saved = sessionStorage.getItem(storageKey);
    const pending = saved === null ? { key: crypto.randomUUID(), digest } : JSON.parse(saved);
    exactKeys(pending, ["key", "digest"]);
    if (!uuid.test(pending.key) || !hashPattern.test(pending.digest)) throw new Error("保存的请求身份无效");
    if (pending.digest !== digest) {
      status.textContent = "内容已经变化，请恢复原内容核验结果，或者明确清除原请求身份";
      throw new Error("企业登记请求内容已变化");
    }
    sessionStorage.setItem(storageKey, JSON.stringify(pending));
    const csrf = await securityToken();
    const { response, value } = await request(path, { method: "POST", headers: { "Content-Type": "application/json",
      "X-Schema-Version": "1.0.0", "Idempotency-Key": pending.key, [csrf.header_name]: csrf.token }, body: JSON.stringify(body) });
    validateEnvelope(response, value);
    if (response.status !== 201 || value.resource_revision !== value.data.revision) throw new Error("企业登记保存回执无效");
    validateDetail(value.data);
    sessionStorage.removeItem(storageKey); form.reset(); await readPage(true); showDetail(value.data);
    status.textContent = "申请已经保存，当前状态为待审查";
  } finally { busy = false; fields.disabled = denied; }
});
window.addEventListener("pagehide", () => { requests.abort(); form.reset(); document.querySelector("[data-registrations]").replaceChildren(); document.querySelector("[data-detail] dl").replaceChildren(); });
