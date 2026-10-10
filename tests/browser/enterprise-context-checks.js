import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { userInfo } from "node:os";
import { resolve } from "node:path";
import { chromium } from "playwright";

const origin = "http://127.0.0.1:18080";
const owner = JSON.parse(await readFile(resolve(".implementation-work/runtime/owner-credentials.json"), "utf8"));
assert.equal(owner.username, userInfo().username);
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const context = await browser.newContext();
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", error => errors.push(error.name));
  await page.goto(origin + "/auth/login");
  await page.waitForFunction(() => !document.querySelector("button[type=submit]").disabled);
  await page.getByRole("textbox", { name: "用户名", exact: true }).fill(owner.username);
  await page.getByLabel("密码", { exact: true }).fill(owner.password);
  await page.getByRole("button", { name: "登录账户", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/account.html");
  await page.waitForFunction(() => !document.querySelector("[data-logout]").disabled);
  const actual = await page.evaluate(async () => {
    const response = await fetch("/api/session", { cache: "no-store", headers: { "X-Schema-Version": "1.0.0" } });
    if (!response.ok) throw new Error("当前业务会话读取失败");
    return response.json();
  });
  assert.equal(actual.data.user.username, owner.username);
  assert.deepEqual(actual.data.memberships, []);
  assert.equal(actual.data.current_context, null);
  const options = await context.request.get(origin + "/api/session/tenant-selection-options", {
    headers: { "X-Schema-Version": "1.0.0" }
  });
  assert.equal(options.status(), 200, "需要不依赖旧企业水位的本人选择候选入口");
  const candidates = await options.json();
  assert.equal(candidates.data.user_id, actual.data.user.user_id);
  assert.equal(candidates.data.expected_context_revision, "0");
  assert.equal(candidates.data.selected_tenant_id, null);
  assert.deepEqual(candidates.data.memberships, actual.data.memberships);
  assert.deepEqual(Object.keys(candidates.data).sort(), ["schema_version", "user_id", "expected_context_revision", "selected_tenant_id", "memberships"].sort());
  assert.equal(candidates.resource_revision, "0");
  assert.equal(options.headers()["cache-control"], "no-store");
  assert.equal(options.headers()["x-request-id"], candidates.request_id);
  assert.equal(options.headers()["x-schema-version"], "1.0.0");
  const missing = await context.request.get(origin + "/api/session/tenant-selection-options");
  assert.equal(missing.status(), 422);
  assert.equal((await missing.json()).code, "SCHEMA_UNSUPPORTED");
  const query = await context.request.get(origin + "/api/session/tenant-selection-options?tenant_id=" + actual.data.user.user_id, {
    headers: { "X-Schema-Version": "1.0.0" }
  });
  assert.equal(query.status(), 422);
  assert.equal((await query.json()).code, "SCHEMA_INVALID");
  const repeated = await context.request.get(origin + "/api/session/tenant-selection-options", { headers: { "X-Schema-Version": "1.0.0" } });
  assert.equal(repeated.status(), 200);
  assert.deepEqual((await repeated.json()).data, candidates.data);
  console.log(JSON.stringify({ check_id: "IC-04-CONTEXT-OPTIONS-HTTP", result: "PASS", source: "actual_os_owner",
    assertions: ["本人候选和公开选择版本", "原Schema和Envelope字段", "Header及禁止缓存", "未知查询拒绝", "缺少Schema版本拒绝", "重复读取状态相等"] }));
  let selections = 0;
  page.on("request", request => {
    if (request.method() === "POST" && request.url() === origin + "/api/session/tenant-selections") selections += 1;
  });
  assert.equal(await page.getByRole("link", { name: "选择企业空间", exact: true }).count(), 1);
  await page.getByRole("link", { name: "选择企业空间", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/enterprise-context.html");
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.locator("[data-user]").textContent(), actual.data.user.username);
  assert.equal(await page.locator("[data-memberships]").textContent(), "当前账户没有企业成员身份");
  assert.equal(await page.locator("[data-context]").textContent(), "尚未选择企业空间");
  assert.equal(await page.getByLabel("企业空间", { exact: true }).inputValue(), "");
  assert.equal(await page.getByLabel("企业空间", { exact: true }).isDisabled(), true);
  assert.equal(await page.getByRole("button", { name: "确认选择企业", exact: true }).isDisabled(), true);
  assert.equal(await page.getByRole("link", { name: "企业登记申请", exact: true }).getAttribute("href"), "/local-auth/enterprise-registration.html");
  assert.equal(await page.evaluate(() => sessionStorage.length), 0);
  assert.equal(selections, 0);
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ check_id: "IC-04-CONTEXT-BROWSER-READ", result: "PASS", browser_version: browser.version(),
    source: "actual_os_owner", user_id: actual.data.user.user_id, visual_check: "NOT_RUN",
    assertions: ["真实账户页面入口", "实际业务会话及成员读取", "没有成员时禁止选择", "不自动创建企业或许可", "没有持久保存凭据"] }));

  const response = page.waitForResponse(item => item.url() === origin + "/api/session" && item.request().method() === "GET");
  await page.getByRole("button", { name: "重新读取企业身份", exact: true }).click();
  assert.equal((await response).status(), 200);
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.locator("[data-memberships]").textContent(), "当前账户没有企业成员身份");
  await page.reload();
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.locator("[data-context]").textContent(), "尚未选择企业空间");
  assert.equal(selections, 0);
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ check_id: "IC-04-CONTEXT-BROWSER-REFRESH", result: "PASS", source: "actual_os_owner",
    assertions: ["刷新读取实际服务端状态", "页面重载没有创建选择操作", "不保存企业内容"] }));

  await context.setOffline(true);
  const failed = page.waitForEvent("requestfailed", { predicate: request => request.url() === origin + "/api/session/tenant-selection-options" });
  await page.getByRole("button", { name: "重新读取企业身份", exact: true }).click();
  await failed;
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.getByRole("status").textContent(), "连接请求未完成，请恢复连接后重新读取企业身份");
  assert.equal(await page.locator("[data-protected]").isVisible(), false);
  assert.equal(await page.locator("[data-selection] button[type=submit]").isDisabled(), true);
  assert.deepEqual(errors, ["TypeError"]);
  await context.setOffline(false);
  await page.getByRole("button", { name: "重新读取企业身份", exact: true }).click();
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.locator("[data-protected]").isVisible(), true);
  assert.equal(await page.locator("[data-memberships]").textContent(), "当前账户没有企业成员身份");
  assert.deepEqual(errors, ["TypeError"]);
  console.log(JSON.stringify({ check_id: "IC-04-CONTEXT-BROWSER-CONNECTION", result: "PASS", source: "actual_os_owner",
    assertions: ["实际离线请求失败", "遮盖已读取内容", "恢复读取控件", "恢复连接后重新读取本人权限"] }));

  await page.getByRole("link", { name: "返回当前账户", exact: true }).click();
  await page.waitForFunction(() => !document.querySelector("[data-logout]").disabled);
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.waitForURL(origin + "/auth/login");
  await page.goto(origin + "/local-auth/enterprise-context.html");
  await page.waitForURL(origin + "/auth/login");
  assert.equal((await context.request.get(origin + "/api/session", { headers: { "X-Schema-Version": "1.0.0" } })).status(), 401);
  assert.equal((await context.request.get(origin + "/api/session/tenant-selection-options", { headers: { "X-Schema-Version": "1.0.0" } })).status(), 401);
  assert.equal(selections, 0);
  console.log(JSON.stringify({ check_id: "IC-04-CONTEXT-BROWSER-LOGOUT", result: "PASS", source: "actual_os_owner",
    assertions: ["实际注销后服务拒绝业务会话", "页面返回登录入口", "没有选择操作"] }));
  await context.close();
} finally { await browser.close(); }
