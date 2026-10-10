"use strict";

import { pendingRequest, sendChange, validatePassword, clearPending, clearSecrets, validateEnvelope, validateAccount, exactKeys } from "./management-request.js";

const status = document.querySelector("[data-management-status]");
const container = document.querySelector("[data-accounts]");
const next = document.querySelector("[data-next]");
const identityResponse = await fetch("/auth/account", { credentials: "same-origin", cache: "no-store" });
if (identityResponse.status === 401 || identityResponse.status === 403) window.location.replace("/auth/login");
if (!identityResponse.ok) throw new Error("本人账户读取失败");
const identity = await identityResponse.json();
if (identity.platform_role !== "PLATFORM_ADMIN") throw new Error("需要平台管理员权限");
const csrfResponse = await fetch("/auth/csrf", { credentials: "same-origin", cache: "no-store" });
if (!csrfResponse.ok) throw new Error("安全会话读取失败");
const csrf = await csrfResponse.json();
let cursor = null;
function element(tag, text) { const value = document.createElement(tag); if (text !== undefined) value.textContent = text; return value; }
function field(form, text, name, value, type = "text") {
  const label = element("label", text); const input = element("input"); input.name = name; input.type = type; input.required = true;
  if (type === "password") { input.autocomplete = "new-password"; input.minLength = 15; } else { input.value = value; input.maxLength = 128; }
  label.append(input); form.append(label); return input;
}
function actionForm(section, account, title, suffix, bodyFactory, setup) {
  const form = element("form"); form.append(element("h3", title)); setup(form);
  const submit = element("button", "确认" + title); submit.type = "submit"; form.append(submit);
  const abandon = element("button", "清除待提交请求"); abandon.type = "button"; form.append(abandon);
  const path = "/api/local-accounts/" + account.user_id + "/" + suffix;
  abandon.addEventListener("click", () => { clearPending(path); clearSecrets(form); status.textContent = "已清除待提交请求"; });
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!window.confirm("确认对账户“" + account.username + "”执行“" + title + "”？")) return;
    const pending = pendingRequest(path, account.revision);
    const body = bodyFactory(new FormData(form)); body.expected_revision = pending.revision;
    submit.disabled = true; status.textContent = "正在提交；结果未确认时请保留原输入和请求身份";
    const result = await sendChange(path, body, pending, csrf);
    submit.disabled = false;
    if (result === null) return;
    if (result.data.account.user_id !== account.user_id) throw new Error("变更结果的目标账户无效");
    clearPending(path); clearSecrets(form);
    if (result.data.reauthentication_required) { window.location.replace("/auth/login"); return; }
    container.replaceChildren(); cursor = null; await load(); status.textContent = "账户变更已经确认";
  });
  section.append(form);
}
function render(account) {
  const section = element("section"); section.className = "account-entry";
  section.append(element("h2", account.display_name), element("p", "用户名：" + account.username + "；状态：" + account.status + "；版本：" + account.revision));
  actionForm(section, account, "修改显示名称及平台角色", "profile-revisions", values => ({ display_name: values.get("display_name"), platform_role: values.get("platform_role") }), form => {
    field(form, "显示名称", "display_name", account.display_name);
    const label = element("label", "平台角色"); const select = element("select"); select.name = "platform_role";
    for (const [value, text] of [["USER", "普通用户"], ["PLATFORM_ADMIN", "平台管理员"]]) { const option = element("option", text); option.value = value; select.append(option); }
    select.value = account.platform_role; label.append(select); form.append(label);
  });
  const decision = account.status === "ACTIVE" ? "DISABLE" : "ACTIVATE";
  actionForm(section, account, decision === "DISABLE" ? "停用账户" : "启用账户", "state-decisions", () => ({ decision }), () => {});
  if (account.user_id !== identity.user_id) actionForm(section, account, "重设账户密码", "credential-revisions", values => {
    const password = values.get("password"); validatePassword(password); return { password };
  }, form => field(form, "新密码", "password", null, "password"));
  container.append(section);
}
async function load() {
  const response = await fetch("/api/local-accounts?limit=50" + (cursor === null ? "" : "&cursor=" + encodeURIComponent(cursor)), {
    credentials: "same-origin", cache: "no-store", headers: { "X-Schema-Version": "1.0.0" }
  });
  if (!response.ok) throw new Error("系统账户读取失败");
  const value = await response.json(); validateEnvelope(response, value);
  exactKeys(value.data, ["schema_version", "items", "next_cursor"]);
  if (value.data.schema_version !== "1.0.0" || !Array.isArray(value.data.items) || value.data.items.length > 50 || value.resource_revision !== null) throw new Error("账户列表响应无效");
  value.data.items.forEach(account => { validateAccount(account); render(account); });
  cursor = value.data.next_cursor; next.hidden = cursor === null; next.disabled = false;
}
next.addEventListener("click", async () => { next.disabled = true; await load(); });
const create = document.querySelector("[data-create]");
create.addEventListener("submit", async event => {
  event.preventDefault(); const values = new FormData(create); const password = values.get("password"); validatePassword(password);
  if (!window.confirm("确认创建获授权人员账户“" + values.get("username") + "”？")) return;
  const path = "/api/local-accounts"; const pending = pendingRequest(path, null);
  const result = await sendChange(path, { username: values.get("username"), display_name: values.get("display_name"), password, platform_role: values.get("platform_role") }, pending, csrf);
  if (result === null) return;
  clearPending(path); clearSecrets(create); create.reset(); container.replaceChildren(); cursor = null; await load(); status.textContent = "账户创建已经确认";
});
create.querySelector("[data-abandon-create]").addEventListener("click", () => { clearPending("/api/local-accounts"); clearSecrets(create); status.textContent = "已清除待提交请求"; });
window.addEventListener("unhandledrejection", () => {
  status.textContent = "操作未确认成功，请核查当前账户及原请求身份";
  document.querySelectorAll("button").forEach(button => { button.disabled = false; });
});
await load(); status.textContent = "";
