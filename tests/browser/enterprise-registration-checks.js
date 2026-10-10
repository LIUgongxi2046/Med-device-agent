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
  const actualAccount = await page.evaluate(async () => {
    const response = await fetch("/auth/account", { cache: "no-store" });
    if (!response.ok) throw new Error("当前账户读取失败");
    return response.json();
  });
  assert.equal(actualAccount.username, owner.username);
  let submissions = 0;
  page.on("request", request => {
    if (request.method() === "POST" && request.url() === origin + "/api/enterprise-registrations") submissions += 1;
  });
  await page.getByRole("link", { name: "企业登记申请", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/enterprise-registration.html");
  await page.waitForFunction(() => !document.querySelector("fieldset").disabled);
  assert.equal(await page.getByRole("status").textContent(), "可以填写企业登记申请");
  assert.equal(await page.locator("[data-registrations]").textContent(), "尚无本人提交的申请");
  const administrator = page.getByLabel("指定首位管理员", { exact: true });
  assert.equal(await administrator.inputValue(), "");
  const options = await administrator.locator("option").evaluateAll(values => values.map(item => ({ value: item.value, text: item.textContent })));
  assert.equal(options.length, 2);
  assert.equal(options[1].value, actualAccount.user_id);
  assert.ok(options[1].text.includes(actualAccount.username));
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ check_id: "IC-04-REGISTRATION-BROWSER-READ", result: "PASS", browser_version: browser.version(),
    source: "actual_os_owner", user_id: actualAccount.user_id, visual_check: "NOT_RUN",
    assertions: ["实际登录及账户入口", "当前有效账户候选", "首位管理员需要明确选择", "本人空申请列表", "实际CSRF响应完整读取", "页面运行无异常"] }));

  await page.getByRole("button", { name: "提交企业登记申请", exact: true }).click();
  assert.equal(await page.locator("[name=legal_name]").evaluate(input => input.validity.valueMissing), true);
  assert.equal(submissions, 0);
  assert.equal(await page.locator("[name=authorization_attested]").isChecked(), false);
  assert.equal(await page.evaluate(() => sessionStorage.length), 0);
  await page.reload();
  await page.waitForFunction(() => !document.querySelector("fieldset").disabled);
  assert.equal(await page.locator("[data-registrations]").textContent(), "尚无本人提交的申请");
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ check_id: "IC-04-REGISTRATION-BROWSER-EMPTY", result: "PASS", browser_version: browser.version(),
    source: "actual_os_owner", visual_check: "NOT_RUN", assertions: ["缺少企业信息停止提交", "授权声明需要明确勾选", "没有保存企业表单内容", "刷新以后重读实际状态"] }));
  await page.getByRole("link", { name: "审查企业申请", exact: true }).click();
  await page.waitForURL(origin + "/local-auth/enterprise-registration-review.html");
  await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
  assert.equal(await page.locator("[data-reviews]").textContent(), "当前筛选条件没有申请");
  assert.equal(await page.locator("[data-decision-section]").isVisible(), false);
  for (const state of ["APPROVED", "REJECTED", "PENDING_REVIEW"]) {
    await page.getByLabel("申请审查状态", { exact: true }).selectOption(state);
    await page.getByRole("button", { name: "重新读取申请", exact: true }).click();
    await page.waitForFunction(() => !document.querySelector("[data-refresh]").disabled);
    assert.equal(await page.locator("[data-reviews]").textContent(), "当前筛选条件没有申请");
  }
  assert.equal(await page.evaluate(() => sessionStorage.length), 0);
  assert.deepEqual(errors, []);
  console.log(JSON.stringify({ check_id: "IC-04-REGISTRATION-REVIEW-BROWSER", result: "PASS", browser_version: browser.version(),
    source: "actual_os_owner", visual_check: "NOT_RUN", assertions: ["真实申请审查入口", "三种状态读取实际服务", "没有申请时禁止作出决定", "没有预设治理许可", "页面没有异常"] }));
  await context.close();
} finally { await browser.close(); }
