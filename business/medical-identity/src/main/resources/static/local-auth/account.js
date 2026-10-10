"use strict";

import { pendingRequest, sendChange, validatePassword, clearPending, clearSecrets, validateEnvelope, exactKeys } from "./management-request.js";

const status = document.querySelector("[data-status]");
const button = document.querySelector("[data-logout]");
const accountResponse = await fetch("/auth/account", { credentials: "same-origin", cache: "no-store" });
if (accountResponse.status === 401 || accountResponse.status === 403) window.location.replace("/auth/login");
if (!accountResponse.ok) throw new Error("账户读取失败");
const account = await accountResponse.json();
document.querySelector("[data-name]").textContent = account.display_name;
document.querySelector("[data-username]").textContent = account.username;
const roles = { PLATFORM_ADMIN: "平台管理员", USER: "普通用户" };
if (!Object.hasOwn(roles, account.platform_role)) throw new Error("账户角色无效");
document.querySelector("[data-role]").textContent = roles[account.platform_role];
document.querySelector("[data-management]").hidden = account.platform_role !== "PLATFORM_ADMIN";
document.querySelector("[data-enterprise-registration]").hidden = account.platform_role !== "PLATFORM_ADMIN";
const bindingResponse = await fetch("/api/session/identity-binding", {
  credentials: "same-origin", cache: "no-store", headers: { "X-Schema-Version": "1.0.0" }
});
if (!bindingResponse.ok) throw new Error("账户身份读取失败");
const binding = await bindingResponse.json();
validateEnvelope(bindingResponse, binding);
exactKeys(binding.data, ["mode", "user_id", "issuer", "subject"]);
if (binding.resource_revision !== null || binding.data.mode !== "local" || binding.data.user_id !== account.user_id
    || binding.data.issuer !== null || binding.data.subject !== null) throw new Error("账户身份与当前登录不一致");
document.querySelector("[data-mode]").textContent = "系统独立账户";
document.querySelector("[data-identity]").textContent = binding.data.user_id;
const csrfResponse = await fetch("/auth/csrf", { credentials: "same-origin", cache: "no-store" });
if (!csrfResponse.ok) throw new Error("安全会话读取失败");
const csrf = await csrfResponse.json();
status.textContent = "";
button.disabled = false;
const credential = document.querySelector("[data-credential]");
const submit = credential.querySelector("[type=submit]");
submit.disabled = false;
const path = "/api/local-account/credential-revisions";
credential.addEventListener("input", event => {
  if (event.target instanceof HTMLInputElement) event.target.setCustomValidity("");
});
credential.querySelector("[data-abandon]").addEventListener("click", () => {
  clearPending(path); clearSecrets(credential);
  credential.querySelectorAll("input").forEach(input => input.setCustomValidity(""));
  status.textContent = "已清除待提交请求";
});
credential.addEventListener("submit", async event => {
  event.preventDefault();
  const values = new FormData(credential);
  const current = values.get("current_password");
  const password = values.get("password");
  validatePassword(current); validatePassword(password);
  if (password !== values.get("confirmation")) {
    const confirmation = credential.elements.namedItem("confirmation");
    status.textContent = "两次输入的新密码需要一致";
    confirmation.setCustomValidity(status.textContent);
    confirmation.reportValidity();
    return;
  }
  const pending = pendingRequest(path, account.revision);
  status.textContent = "正在提交；结果未确认时请保留原请求身份和原输入";
  submit.disabled = true;
  const result = await sendChange(path, { current_password: current, password, expected_revision: pending.revision }, pending, csrf);
  if (result === null) { submit.disabled = false; return; }
  if (!result.data.reauthentication_required || result.data.account.user_id !== account.user_id) throw new Error("密码变更结果与当前账户不一致");
  clearPending(path); clearSecrets(credential);
  window.location.replace("/auth/login");
});
window.addEventListener("unhandledrejection", () => {
  status.textContent = "操作未确认成功，请核查提示并使用原请求重新提交"; submit.disabled = false;
});
button.addEventListener("click", async () => {
  button.disabled = true;
  const response = await fetch("/auth/logout", {
    method: "POST", credentials: "same-origin", headers: { [csrf.header_name]: csrf.token }
  });
  if (!response.ok) {
    status.textContent = "退出登录失败，请重新提交";
    button.disabled = false;
    return;
  }
  window.location.replace("/auth/login");
});
