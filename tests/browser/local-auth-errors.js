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
  const submit = page.getByRole("button", { name: "登录账户", exact: true });
  await page.waitForFunction(() => !document.querySelector("button[type=submit]").disabled);
  await page.getByRole("textbox", { name: "用户名", exact: true }).fill(owner.username);
  await page.getByLabel("密码", { exact: true }).fill(owner.password);
  // 浏览器断开实际连接，恢复后继续请求相同的真实服务。
  await context.setOffline(true);
  const failed = page.waitForEvent("requestfailed", { predicate: request => request.url() === origin + "/auth/login" });
  await submit.click();
  await failed;
  await page.waitForFunction(() => !document.querySelector("button[type=submit]").disabled, null, { timeout: 5000 });
  assert.equal(await page.getByRole("status").textContent(), "连接请求未完成，请检查网络后重新提交");
  assert.deepEqual(errors, ["TypeError"]);
  assert.equal(await page.getByLabel("密码", { exact: true }).inputValue() === owner.password, true);
  await context.setOffline(false);
  const login = page.waitForResponse(response => response.url() === origin + "/auth/login" && response.request().method() === "POST");
  await submit.click();
  assert.equal((await login).status(), 302);
  await page.waitForURL(origin + "/local-auth/account.html");
  await page.waitForFunction(() => !document.querySelector("[data-logout]").disabled);
  assert.equal(await page.locator("[data-username]").textContent(), owner.username);
  console.log(JSON.stringify({ check_id: "IC-04-AUTH-CONNECTION", result: "PASS", browser_version: browser.version(),
    source: "actual_os_owner", visual_check: "NOT_RUN", assertions: ["实际离线请求失败", "显示明确连接错误", "恢复提交控件", "恢复连接后实际登录成功"] }));

  const credential = page.locator("[data-credential]");
  await credential.getByLabel("当前密码", { exact: true }).fill(owner.password);
  await credential.getByLabel("新密码", { exact: true }).fill(owner.password);
  const changed = (owner.password.startsWith("a") ? "b" : "a") + owner.password.slice(1);
  await credential.getByLabel("确认新密码", { exact: true }).fill(changed);
  let submitted = 0;
  page.on("request", request => { if (request.url() === origin + "/api/local-account/credential-revisions") submitted += 1; });
  await credential.getByRole("button", { name: "确认变更本人密码", exact: true }).click();
  await page.waitForFunction(() => document.querySelector("[data-status]").textContent === "两次输入的新密码需要一致", null, { timeout: 5000 });
  assert.equal(submitted, 0);
  assert.deepEqual(errors, ["TypeError"]);
  await credential.getByRole("button", { name: "清除待提交请求", exact: true }).click();
  assert.equal(await credential.getByLabel("当前密码", { exact: true }).inputValue(), "");
  assert.equal(await credential.getByLabel("新密码", { exact: true }).inputValue(), "");
  assert.equal(await credential.getByLabel("确认新密码", { exact: true }).inputValue(), "");
  console.log(JSON.stringify({ check_id: "IC-04-CREDENTIAL-VALIDATION", result: "PASS", source: "actual_owner_input_mutation",
    assertions: ["密码确认不一致立即提示", "拒绝提交请求", "没有未处理校验异常", "清除全部秘密输入"] }));
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.waitForURL(origin + "/auth/login");
  assert.equal((await context.request.get(origin + "/auth/account")).status(), 401);
} finally {
  await browser.close();
}
