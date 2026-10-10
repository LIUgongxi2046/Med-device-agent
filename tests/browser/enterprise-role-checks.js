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
  const context = await browser.newContext(); const page = await context.newPage(); const errors = []; let writes = 0;
  page.on("pageerror", error => errors.push(error.name));
  page.on("request", request => { if (request.method() === "POST" && request.url().includes("/api/enterprise-roles")) writes++; });
  await page.goto(origin + "/auth/login");
  await page.waitForFunction(() => !document.querySelector("button[type=submit]").disabled);
  await page.getByRole("textbox", { name: "用户名", exact: true }).fill(owner.username);
  await page.getByLabel("密码", { exact: true }).fill(owner.password);
  await page.getByRole("button", { name: "登录账户", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/account.html");
  await page.waitForFunction(() => !document.querySelector("[data-logout]").disabled);
  assert.equal(await page.getByRole("link", { name: "管理企业岗位", exact: true }).count(), 1);
  await page.getByRole("link", { name: "管理企业岗位", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/enterprise-roles.html");
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.getByRole("status").textContent(), "当前账户尚未选择企业，请取得企业成员身份并明确选择企业空间");
  assert.equal(await page.locator("[data-role-content]").isVisible(), false);
  assert.equal(await page.locator("[data-publish] fieldset").evaluate(element => element.disabled), true);
  assert.equal(await page.locator("[data-revoke] fieldset").evaluate(element => element.disabled), true);
  assert.equal(await page.locator("[data-publish] button[type=submit]").isDisabled(), true);
  assert.equal(await page.locator("[data-revoke] button[type=submit]").isDisabled(), true);
  assert.equal(await page.getByRole("link", { name: "选择企业空间", exact: true }).getAttribute("href"), "/local-auth/enterprise-context.html");
  assert.equal(await page.evaluate(() => sessionStorage.length), 0); assert.equal(writes, 0); assert.deepEqual(errors, []);
  console.log(JSON.stringify({ check_id: "IC-04-ROLE-BROWSER-ADMISSION", result: "PASS", source: "actual_os_owner", browser_version: browser.version(), visual_check: "NOT_RUN",
    assertions: ["实际账户入口", "真实当前企业状态", "没有企业禁止岗位读写", "没有创建客户岗位或许可", "没有保存企业内容或凭据"] }));
  await context.setOffline(true);
  const failure = page.waitForEvent("requestfailed", { predicate: request => request.url() === origin + "/api/session" });
  await page.getByRole("button", { name: "重新读取岗位权限", exact: true }).click(); await failure;
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.getByRole("status").textContent(), "连接请求未完成，请恢复连接后重新读取岗位权限");
  assert.equal(await page.locator("[data-role-content]").isVisible(), false); assert.deepEqual(errors, ["TypeError"]);
  await context.setOffline(false);
  await page.getByRole("button", { name: "重新读取岗位权限", exact: true }).click();
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.getByRole("status").textContent(), "当前账户尚未选择企业，请取得企业成员身份并明确选择企业空间");
  assert.equal(writes, 0); assert.equal(await page.evaluate(() => sessionStorage.length), 0);
  console.log(JSON.stringify({ check_id: "IC-04-ROLE-BROWSER-CONNECTION", result: "PASS", source: "actual_os_owner",
    assertions: ["实际网络中断", "禁止操作并清除可见内容", "恢复后重新读取当前身份", "没有自动提交"] }));
  await page.getByRole("link", { name: "返回当前账户", exact: true }).click();
  await page.waitForFunction(() => !document.querySelector("[data-logout]").disabled);
  await page.getByRole("button", { name: "退出登录", exact: true }).click(); await page.waitForURL(origin + "/auth/login");
  await page.goto(origin + "/local-auth/enterprise-roles.html"); await page.waitForURL(origin + "/auth/login");
  assert.equal((await context.request.get(origin + "/api/enterprise-roles", { headers: { "X-Schema-Version": "1.0.0", "X-Context-Revision": "0" } })).status(), 401);
  assert.equal(writes, 0);
  console.log(JSON.stringify({ check_id: "IC-04-ROLE-BROWSER-LOGOUT", result: "PASS", source: "actual_os_owner",
    assertions: ["实际注销以后接口拒绝", "页面返回登录入口", "没有企业岗位写入"] }));
  await context.close();
} finally { await browser.close(); }
