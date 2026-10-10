"use strict";

const form = document.querySelector("[data-auth-form]");
const status = form.querySelector("[data-status]");
const submit = form.querySelector("button[type=submit]");
const csrfResponse = await fetch("/auth/csrf", { credentials: "same-origin", cache: "no-store" });
if (!csrfResponse.ok) throw new Error("安全会话建立失败，请刷新页面");
const csrf = await csrfResponse.json();
if (typeof csrf.token !== "string" || typeof csrf.parameter_name !== "string") throw new Error("安全会话响应无效");
const tokenInput = document.createElement("input");
tokenInput.type = "hidden";
tokenInput.name = csrf.parameter_name;
tokenInput.value = csrf.token;
form.append(tokenInput);
submit.disabled = false;
status.textContent = "";

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const confirmation = document.getElementById("password-confirm");
  if (confirmation && confirmation.value !== form.elements.password.value) {
    status.textContent = "两次输入的密码需要一致";
    status.dataset.error = "true";
    return;
  }
  submit.disabled = true;
  status.textContent = "正在提交请求";
  status.dataset.error = "false";
  let responseReceived = false;
  try {
    const response = await fetch(form.action, {
      method: "POST", credentials: "same-origin", cache: "no-store",
      body: new URLSearchParams(new FormData(form))
    });
    responseReceived = true;
    if (!response.ok) {
      status.textContent = response.status === 401 ? "登录失败，请检查用户名、密码或账户状态" : "操作失败，请刷新页面后重新提交";
      status.dataset.error = "true";
      return;
    }
    form.querySelectorAll("input[type=password]").forEach(input => { input.value = ""; });
    window.location.assign(response.url);
  } finally {
    // 网络异常继续抛出，同时恢复用户操作并显示当前结果。
    if (!responseReceived) {
      status.textContent = "连接请求未完成，请检查网络后重新提交";
      status.dataset.error = "true";
    }
    submit.disabled = false;
  }
});
window.addEventListener("pagehide", () => form.querySelectorAll("input[type=password]").forEach(input => { input.value = ""; }));
