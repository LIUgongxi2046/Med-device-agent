# IMP-23 旧位置显式核对前端

## 已实现边界

跨版本位置面板保留原实际 OPEN / RESOLVED 状态，只有完整历史证明当前有效位置仍为原始位置、指针为 1、原锚点 ID 等于批注 ID、原位置属于旧正文，且生命周期没有 INVALIDATE / REANCHOR 时，才展示旧位置核对表单。必须填写原因并勾选确认，按钮明确为“确认标记为待重新定位”。不自动推断历史失效时间，不提前显示 REANCHOR_REQUIRED。

新请求要求当前、未签发、无未保存编辑的正文，以及真实 `annotation.read` / `review.decide` 授权摘要。服务端负责 ACTIVE 项目、来源授权及事务内完整校验，前端不是授权替代物。独立 `annotation-anchor-reconciliations:1.0.0` Schema/API 与 `RV-06-ANCHOR-RECONCILE-1` 操作，不改变既有锚点 Schema/manifest。

核对只记录当前操作者、数据库时间和原因；原始批注意见、作者、时间、Selection、制品及锚点指针不变。完成后核验原回执、完成操作、原始锚点摘要及完整生命周期中的 INVALIDATE，再独立读取实际当前文稿快照及当前正文的位置列表。面板刷新父级快照、列表和历史后才能进入既有人工选择新位置流程；成功人工重新定位总是 OPEN。

## 恢复与取消

待恢复记录只持久化身份、CAS、水位和摘要，包括原始锚点摘要及原状态。原因、引用、正文、Selection 和凭据不进入 web storage。原因和完整请求仅保留在标签页内存，与人工重新定位草稿分开。请求准备后表单冻结原原因和确认；重载后必须重新输入完全相同的原因。

未决核对、普通批注写入及重新定位互斥。停止等待释放共享写锁并取消后续异步派发，保留原操作身份。上下文变化与摘要计算期间取消均阻止后续请求。历史、签发、脏正文或非当前视图只有 FOUND 的原操作可继续；未知结果、失败、过期不会生成替代 key。

旧回执重放不重复要求 fresh eligibility：原目标正文及请求摘要保持不变，原来源/权限和历史证据重新验证。后续回复、决定、再次定位和另存 V3 不使旧回执冒充当前状态。settlement 单独读取实际当前快照/正文，当前读回失败仍保留原 pending，不能因正文变化丢弃原 key。

## 离线验证

`tests/unit/reconciliation-frontend-checks.mjs`：纯 domain、mock transport 和无 DOM 的真实 Vue setup。覆盖旧 OPEN / RESOLVED 经显式核对后使用既有重新定位 API 成为 OPEN；完整来源与原始证据保持；原因/确认/CAS/权限/签发/当前视图门禁；不确定结果精确恢复；旧回执在后续重新定位、回复、决定及真实不同 V3 正文后的核验；当前快照读取失败保留 pending；取消、共享锁及异步身份变化；父级快照、列表、历史刷新后开放人工重新定位。

原有 `reanchor-frontend-checks.mjs` 15 组、`annotation-frontend-checks.mjs` 20 组及 `document-selection-checks.mjs` 9 组继续保留。结果文件位于 `.implementation-work/reconciliation-frontend-unit/result.json`。

这些验证不代表真实 HTTP、浏览器、数据库、对象存储或线上验收；未启动服务、浏览器、socket、云任务或 push。
