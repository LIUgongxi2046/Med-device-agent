import assert from "node:assert/strict";
import { randomBytes, randomUUID } from "node:crypto";
import { readFile, open, rename, stat } from "node:fs/promises";
import { userInfo } from "node:os";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const runtime = resolve(root, ".implementation-work/runtime");
const origin = "http://127.0.0.1:18080";
const credentialPath = resolve(runtime, "owner-credentials.json");
const owner = JSON.parse(await readFile(credentialPath, "utf8"));
assert.equal((await stat(credentialPath)).uid, userInfo().uid, "凭据文件必须属于当前操作系统所有者");
assert.equal((await stat(credentialPath)).mode & 0o777, 0o600);
assert.equal((await stat(runtime)).mode & 0o777, 0o700);
const checks = [];
const errors = [];
const browser = await chromium.launch({ channel: "chrome", headless: true });

function responseFor(page, method, path) {
  return page.waitForResponse(response => response.url() === origin + path && response.request().method() === method);
}

async function ready(page, selector) {
  await page.locator(selector).waitFor({ state: "visible" });
  await page.waitForFunction(value => !document.querySelector(value).disabled, selector);
}

function observe(page) {
  // 仅保存公开错误类别，浏览器请求和秘密输入不进入日志。
  page.on("pageerror", error => errors.push(error.name));
}

async function login(page, password) {
  await page.goto(origin + "/auth/login");
  await ready(page, "[data-auth-form] button[type=submit]");
  assert.equal(await page.getByRole("heading", { name: "登录账户", exact: true }).count(), 1);
  await page.getByRole("textbox", { name: "用户名", exact: true }).fill(owner.username);
  await page.getByLabel("密码", { exact: true }).fill(password);
  const result = responseFor(page, "POST", "/auth/login");
  await page.getByRole("button", { name: "登录账户", exact: true }).click();
  assert.equal((await result).status(), 302);
  await page.waitForURL(origin + "/local-auth/account.html");
  await ready(page, "[data-logout]");
  assert.equal(await page.locator("[data-username]").textContent(), owner.username);
  assert.equal(await page.locator("[data-name]").textContent(), owner.display_name);
  assert.equal(await page.locator("[data-role]").textContent(), "平台管理员");
  assert.equal(await page.locator("[data-mode]").textContent(), "系统独立账户");
  const account = await page.context().request.get(origin + "/auth/account");
  assert.equal(account.status(), 200);
  assert.equal(await page.locator("[data-identity]").textContent(), (await account.json()).user_id);
}

async function storageHasNoSecrets(page, secrets) {
  const csrfResponse = await page.context().request.get(origin + "/auth/csrf");
  assert.equal(csrfResponse.status(), 200);
  const csrf = await csrfResponse.json();
  const cookies = await page.context().cookies(origin);
  const session = cookies.filter(cookie => cookie.name === "MEDICAL_SESSION");
  assert.equal(session.length, 1);
  assert.equal(session[0].httpOnly, true);
  assert.equal(session[0].sameSite, "Lax");
  const values = [...secrets, csrf.token, session[0].value];
  const safe = await page.evaluate(values => {
    const stored = [localStorage, sessionStorage].flatMap(storage =>
      Object.keys(storage).map(key => storage.getItem(key)));
    return stored.every(value => values.every(secret => !value.includes(secret)));
  }, values);
  assert.equal(safe, true, "浏览器持久存储不能保存密码、CSRF或会话Cookie");
}

async function actualAccount(context) {
  const response = await context.request.get(origin + "/api/local-accounts?limit=1", {
    headers: { "X-Schema-Version": "1.0.0" }
  });
  assert.equal(response.status(), 200);
  const value = await response.json();
  assert.equal(value.data.items.length, 1);
  assert.equal(value.data.next_cursor, null);
  assert.equal(value.data.items[0].username, owner.username);
  return value.data.items[0];
}

async function confirm(page, button, path) {
  const result = responseFor(page, "POST", path);
  page.once("dialog", async dialog => {
    assert.equal(dialog.type(), "confirm");
    assert.equal(dialog.message().includes(owner.username), true);
    await dialog.accept();
  });
  await button.click();
  return await result;
}

async function syncDirectory() {
  const directory = await open(runtime, "r");
  try { await directory.sync(); } finally { await directory.close(); }
}

try {
  const context = await browser.newContext();
  const page = await context.newPage();
  observe(page);
  page.setDefaultTimeout(15000);
  await login(page, owner.password);
  const listed = responseFor(page, "GET", "/api/local-accounts?limit=50");
  await page.getByRole("link", { name: "管理系统账户", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/management.html");
  assert.equal((await listed).status(), 200);
  await page.locator(".account-entry").waitFor({ state: "visible" });
  assert.equal(await page.locator(".account-entry").count(), 1);
  assert.equal(await page.getByRole("button", { name: "确认重设账户密码", exact: true }).count(), 0);
  const before = await actualAccount(context);
  checks.push({ name: "真实登录及账户管理入口", result: "PASS", user_id: before.user_id });

  const entry = page.locator(".account-entry");
  for (const [suffix, title] of [["state-decisions", "停用账户"], ["profile-revisions", "修改显示名称及平台角色"]]) {
    const path = "/api/local-accounts/" + before.user_id + "/" + suffix;
    const form = entry.locator("form").filter({ has: page.getByRole("heading", { name: title, exact: true }) });
    if (suffix === "profile-revisions") await form.getByRole("combobox").selectOption("USER");
    const response = await confirm(page, form.getByRole("button", { name: "确认" + title, exact: true }), path);
    assert.equal(response.status(), 409);
    const error = await response.json();
    assert.equal(error.code, "REVISION_CONFLICT");
    await page.waitForFunction(message => document.querySelector("[data-management-status]").textContent.includes(message), error.message);
    const key = "medical.local.management:" + path;
    const pending = await page.evaluate(value => JSON.parse(sessionStorage.getItem(value)), key);
    assert.equal(pending.revision, before.revision);
    assert.equal(await page.getByRole("status").textContent(), error.message + "；请求身份：" + pending.key);
    assert.deepEqual(await actualAccount(context), before);
    await form.getByRole("button", { name: "清除待提交请求", exact: true }).click();
    assert.equal(await page.evaluate(value => sessionStorage.getItem(value), key), null);
    checks.push({ name: "最后管理员保护及请求清除：" + suffix, result: "PASS", status: 409, request_id: error.request_id });
  }

  const create = page.locator("[data-create]");
  await create.getByRole("textbox", { name: "真实用户名", exact: true }).fill(owner.username);
  await create.getByRole("textbox", { name: "显示名称", exact: true }).fill(owner.display_name);
  await create.getByLabel("初始密码", { exact: true }).fill(owner.password);
  const duplicate = await confirm(page, create.getByRole("button", { name: "确认创建账户", exact: true }), "/api/local-accounts");
  assert.equal(duplicate.status(), 409);
  assert.equal((await duplicate.json()).code, "REVISION_CONFLICT");
  assert.deepEqual(await actualAccount(context), before);
  await storageHasNoSecrets(page, [owner.password]);
  await create.getByRole("button", { name: "清除待提交请求", exact: true }).click();
  assert.equal(await create.locator("input[type=password]").inputValue(), "");
  checks.push({ name: "已有真实用户名拒绝及密码清除", result: "PASS", status: 409 });

  const oldContext = await browser.newContext();
  const oldPage = await oldContext.newPage();
  observe(oldPage);
  await login(oldPage, owner.password);
  const current = await actualAccount(context);
  const accountLoaded = responseFor(page, "GET", "/auth/account");
  await page.getByRole("link", { name: "返回本人账户", exact: true }).click();
  const identity = await (await accountLoaded).json();
  assert.equal(identity.revision, current.revision);
  await ready(page, "[data-credential] button[type=submit]");
  const credential = page.locator("[data-credential]");
  const nextOwner = { ...owner, password: randomBytes(24).toString("hex") };
  const candidate = resolve(runtime, "owner-credentials-browser-" + randomUUID() + ".json");
  const output = await open(candidate, "wx", 0o600);
  try { await output.writeFile(JSON.stringify(nextOwner)); await output.sync(); } finally { await output.close(); }
  await syncDirectory();
  await credential.getByLabel("当前密码", { exact: true }).fill(owner.password);
  await credential.getByLabel("新密码", { exact: true }).fill(nextOwner.password);
  await credential.getByLabel("确认新密码", { exact: true }).fill(nextOwner.password);
  await storageHasNoSecrets(page, [owner.password, nextOwner.password]);
  const changed = responseFor(page, "POST", "/api/local-account/credential-revisions");
  await credential.getByRole("button", { name: "确认变更本人密码", exact: true }).click();
  const changedResponse = await changed;
  assert.equal(changedResponse.status(), 200);
  const headers = changedResponse.headers();
  const key = changedResponse.request().headers()["idempotency-key"];
  assert.match(headers["x-request-id"], /^[0-9a-f-]{36}$/);
  assert.match(key, /^[0-9a-f-]{36}$/);
  assert.equal(headers["x-schema-version"], "1.0.0");
  assert.equal(headers["cache-control"], "no-store");
  await page.waitForURL(origin + "/auth/login");
  const rejected = await oldContext.request.get(origin + "/auth/account");
  assert.equal(rejected.status(), 403);
  assert.equal((await rejected.json()).code, "ACCESS_DENIED");
  await login(page, nextOwner.password);
  const updated = await actualAccount(context);
  assert.equal(updated.user_id, current.user_id);
  assert.equal(updated.credential_revision, String(BigInt(current.credential_revision) + 1n));
  // 页面确认成功并通过新密码认证后更新私密凭据；原响应由数据库重新读取。
  await rename(candidate, credentialPath);
  await syncDirectory();
  await storageHasNoSecrets(page, [owner.password, nextOwner.password]);
  const logout = responseFor(page, "POST", "/auth/logout");
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  assert.equal((await logout).status(), 204);
  await page.waitForURL(origin + "/auth/login");
  assert.equal((await context.request.get(origin + "/auth/account")).status(), 401);
  assert.deepEqual(errors, [], "浏览器模块不能出现未处理异常");
  checks.push({ name: "本人密码变更、旧会话撤销、新密码登录及注销", result: "PASS", user_id: current.user_id,
    idempotency_key: key, request_id: headers["x-request-id"], previous_revision: current.revision,
    previous_credential_revision: current.credential_revision, credential_revision: updated.credential_revision,
    old_session_status: 403, new_login_status: 302, logout_status: 204 });
  const receipt = { schema_version: "1.0.0", source: "actual_os_owner_and_current_application", username: owner.username,
    browser: "Chrome", browser_version: browser.version(), headless: true, visual_check: "NOT_RUN", checks };
  await open(resolve(runtime, "local-browser-receipt.json"), "w", 0o600).then(async file => {
    try { await file.writeFile(JSON.stringify(receipt, null, 2)); } finally { await file.close(); }
  });
  console.log(JSON.stringify({ result: "PASS", browser_version: receipt.browser_version, checks: checks.length }));
} finally {
  await browser.close();
}
