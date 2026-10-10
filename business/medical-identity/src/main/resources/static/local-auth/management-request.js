"use strict";

const prefix = "medical.local.management:";
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const revision = /^(?:0|[1-9][0-9]*)$/;
const maximumRevision = 18446744073709551615n;
export function validRevision(value) {
  if (typeof value !== "string" || !revision.test(value) || BigInt(value) > maximumRevision) throw new Error("账户版本无效");
}
export function exactKeys(value, keys) {
  if (value === null || typeof value !== "object" || Array.isArray(value)
      || Object.keys(value).length !== keys.length || keys.some(key => !Object.hasOwn(value, key))) throw new Error("服务响应字段无效");
}
export function validateAccount(value) {
  exactKeys(value, ["user_id", "username", "display_name", "status", "platform_role", "revision", "credential_revision"]);
  if (!uuid.test(value.user_id) || typeof value.username !== "string" || typeof value.display_name !== "string"
      || !["ACTIVE", "DISABLED"].includes(value.status) || !["USER", "PLATFORM_ADMIN"].includes(value.platform_role)) throw new Error("账户响应无效");
  validRevision(value.revision); validRevision(value.credential_revision);
}
export function validateEnvelope(response, value) {
  exactKeys(value, ["schema_version", "request_id", "resource_revision", "data"]);
  if (value.schema_version !== "1.0.0" || !uuid.test(value.request_id) || response.headers.get("X-Schema-Version") !== "1.0.0"
      || response.headers.get("X-Request-Id") !== value.request_id || response.headers.get("Cache-Control") !== "no-store") throw new Error("服务响应身份无效");
}
export function validatePassword(value) {
  if (typeof value !== "string" || Array.from(value).length < 15 || new TextEncoder().encode(value).length > 72) throw new Error("密码需要至少15个字符，UTF-8编码最多72个字节");
}
export function pendingRequest(path, currentRevision) {
  const saved = sessionStorage.getItem(prefix + path);
  if (saved !== null) {
    const value = JSON.parse(saved);
    exactKeys(value, ["key", "revision"]);
    if (!uuid.test(value.key)) throw new Error("保存的请求身份无效");
    if (value.revision !== null) validRevision(value.revision);
    return value;
  }
  if (currentRevision !== null) validRevision(currentRevision);
  const value = { key: crypto.randomUUID(), revision: currentRevision };
  // 仅保存请求身份及公开版本；密码始终保留在当前表单。
  sessionStorage.setItem(prefix + path, JSON.stringify(value));
  return value;
}
export function clearPending(path) { sessionStorage.removeItem(prefix + path); }
export function clearSecrets(form) { form.querySelectorAll("input[type=password]").forEach(input => { input.value = ""; }); }
export async function sendChange(path, body, pending, csrf) {
  const response = await fetch(path, { method: "POST", credentials: "same-origin", cache: "no-store", headers: {
    "Content-Type": "application/json", "X-Schema-Version": "1.0.0", "Idempotency-Key": pending.key, [csrf.header_name]: csrf.token
  }, body: JSON.stringify(body) });
  const value = await response.json();
  if (!response.ok) {
    exactKeys(value, ["schema_version", "code", "message", "request_id", "resource_revision", "allowed_actions", "retryable", "operation_id", "details"]);
    if (value.schema_version !== "1.0.0" || !uuid.test(value.request_id) || typeof value.message !== "string" || typeof value.code !== "string") throw new Error("服务错误响应无效");
    document.querySelector("[data-status], [data-management-status]").textContent = value.message + "；请求身份：" + pending.key;
    return null;
  }
  validateEnvelope(response, value);
  exactKeys(value.data, ["schema_version", "operation_id", "account", "reauthentication_required"]);
  validateAccount(value.data.account);
  if (value.data.schema_version !== "1.0.0" || !uuid.test(value.data.operation_id) || typeof value.data.reauthentication_required !== "boolean"
      || value.resource_revision !== value.data.account.revision) throw new Error("账户变更响应无效");
  return value;
}
window.addEventListener("pagehide", () => document.querySelectorAll("form").forEach(clearSecrets));
