# 本地输入校验与 fail-closed 边界审计（2026-10-18）

## 范围和状态

本报告为源码只读审计。写入本报告之外没有修改任何业务代码、全局 protocol/main 或原 42 项任务状态，没有执行浏览器验证。修复需按以下三组分别推进；当前记录的是已确认缺口与最小修改范围，不表示已经修复。行号为审计时位置，后续变更以函数名为准。

## 共性故障链

1. `web/src/api/medical/protocol.ts:330 fail()` 创建 MedicalFailure 并**同步**派发 `medical-failure`，然后抛异常。`web/src/main.js:75` 收到该事件立即 `invalidateMedical()`。所以外层 catch 一个已经经过 fail 的错误，不能撤销已发生的会话清理。
2. `main.js:81 app.config.errorHandler` 同样清理医疗会话。裸 `JSON.parse` 的 SyntaxError、未捕获的文件读取 rejection 即使没有经过 fail，也可能进入这条链。
3. `protocol.schemaIssues()` 可用于正常表单无副作用预检；严格 `validate()` 会调用 fail，不能直接用作本地导入/编辑输入错误提示器。未知 Schema 本身仍是工程错误，不应伪装成用户字段错误。

## 总边界

### 本地显示并拒绝继续的普通输入错误

- 用户尚未发送的 ID、JSON 语法、重复键、有限/安全数值、字段必填/类型/长度、文本规范化错误。
- 用户正在编辑的集合内重复成员/稳定 ID、跨章节/问题引用不一致等可由当前完整草稿确定的语义错误。
- 本地文件读取失败、导入格式错误。不能发送请求、建立新 pending key、清掉旧 pending 或丢弃当前草稿。
- 处理方式：在调用严格 API/恢复函数**之前**返回字段/表单错误，保留实际输入，不静默 trim、修补引用或替换原值。纯预检通过以后，严格层仍执行原验证。

### 必须继续 fail-closed 的权威边界

- 服务端固定响应的 Schema、资源身份、正文/制品摘要、修订、授权/治理水位不一致。
- 保存回执、原操作查回结果与固定对象重新读取不一致。
- 保存的 pending 元数据主体、企业、目标、原 key、原上下文或摘要不一致；元数据损坏不能按普通输入错误忽略。
- 恢复输入通过语法/结构预检后，与原请求 body_sha256、固定依据或原目标不一致：仍须阻止恢复，保留原请求身份，不生成新 key、不把它当作新提交。本轮不得借“局部提示”放宽原完整性核验。
- 当前权限不足、身份/上下文变化、固定对象/版本失效和权限撤销。不要通过全局 catch 吞掉或把严格安全错误全部转换成普通表单提示。

同一验证规则要按来源区分：**未发送草稿中的重复/矛盾值**可由纯预检局部提示；**服务端固定对象或已保存恢复元数据出现同样矛盾**仍须严格拒绝。不要全局替换 fail。

## 第一组：岗位历史 ID / 任务计划

### 1A. 岗位历史 ID（确认可正常点击触发）

- 路径：`web/src/views/medical/enterprise-roles.vue:67–69,182–184`。
- 链：用户输入非空但无效的历史版本标识 → “读取历史岗位版本”按钮仅检查是否为空 → `history()` 的 `schemaIssues(roleReference('Id'), historyVersion)` 不通过 → `fail('SCHEMA_INVALID', ...)`。
- 最小修复：只在该字段显示局部错误并 return；保留当前岗位详情/会话。实际 `readRole` 的响应身份及固定版本验证不动。

### 1B. 任务计划编辑（确认普通草稿操作可触发）

- 页面：`web/src/views/medical/task-plan-editor.vue:125–138,210–218`。
- 严格层：`web/src/api/medical/task-plans.ts:96–120 validatePlanInput()`；`preparePlan()` 调用此验证。
- 已有预检：完整 `PlanVersionInput` Schema 与全部目录唯一覆盖。
- 缺失预检：理由首尾空白/控制字符/NFC；章节目标和研究问题的文本规范；重复问题 key；删除章节后遗留的章节/问题依赖；问题目录不属于所选章节；问题自依赖或指向已删除 key。
- 可达例子：理由填写带换行或首尾空白的字符串（Schema 仅长度）；编辑问题 key 为另一条已有 key；移除被其他章节依赖的章节。Schema/目录覆盖可能仍通过，随后严格层 fail 清会话。
- 最小修复：新增纯计划输入 issues，镜像已有草稿语义检查，在 `submit` 调用 prepare 前消费；错误定位到理由、章节、问题。继续保留 strict validatePlanInput、原任务版本/原操作摘要/权限检查。

### 同组无需扩大范围

- `task-create.vue#body()` 已执行 Schema 与 `projectTextIssue(goal)`，不把其响应重复记录检查和固定创建回执核对改成本地错误。
- `task-plan-return.vue` 已在按钮用 `projectTextIssue(reason, 2000)` 禁止无效理由；可补 submit 同等防御性 return，但本审计不宣称发现同等级正常点击复现链。
- 任务创建分页的固定 page-size 断言、计划编辑依据与当前任务修订不符属于固定调用/对象边界，不是本轮普通表单输入错误。

### 组一建议验证

编译真实 setup 的测试覆盖：错误历史 ID 不发送请求且不派发 medical-failure；计划文本/重复 key/删除后悬空引用保留草稿；有效计划仍进入严格层；模拟响应/原 pending 摘要不符仍 fail-closed。

## 第二组：资料集 / 模板创建 / 结构化定义

### 2A. 导入文件（确认可正常选择文件触发）

- `web/src/views/medical/collections.vue:246 importBody()`。
- `web/src/views/medical/template-create.vue:178 importDefinition()`。
- 链：`file.raw.text()` → `project-json.ts#readBoundedJson()` → `validateCollectionBody()` / `validateTemplateCreate()`。
- `readBoundedJson` 使用严格 fail；无效 JSON、重复键、不安全整数等正常坏文件会导致会话清理。读文件 rejection 同样需要局部处理。
- 资料集导入还会因缺字段、理由/名称不规范、重复 source_version 进入 `collections.ts:66–76` 严格验证；模板导入会因缺字段、理由、计数或依据问题进入 `template-create.ts:34–56` 严格验证。
- 最小修复：读文件、语法/重复键/安全数字、Schema 与草稿语义在导入边界纯预检，不覆盖现有表单直到全部成功。`file.raw` 缺失也应局部报错，但真实浏览器选文件通常提供 raw，不把这一防御分支当主要复现。

### 2B. 模板普通表单的额外语义

- `template-create.vue#body()` 已有字段/JSON/Schema 局部校验，但最后仍调用 `validateTemplateCreate()`。
- `template-create.ts#templateBases()` 检查同一个 version_id 对应不同 content_sha256，当前未发送草稿可由手填不同部分产生矛盾而触发严格 fail。
- 最小修复：在未发送完整草稿预检阶段提示冲突的依据引用，不自动选择摘要；服务端或 pending 固定依据发生冲突仍严格拒绝。
- `projectJsonIssue()` 只检查语法和重复键，不能据此认为 `readBoundedJson()` 的安全数值检查不会再失败。正常表单各 JSON 段也应覆盖安全数字预检。

### 2C. 结构化定义编辑（确认可正常编辑触发）

- `web/src/views/medical/template-definition-editor.vue:102 checked()`、`:114 open()`、`:132 confirmItem()`、`:160 apply()`。
- `open()` 直接 `checked(readBoundedJson(props.value))`：父模板表单手填非数组、损坏 JSON、错误成员即可触发。
- `confirmItem()` 在新增/编辑稳定 ID 后调用 `checked(next)`，重复 ID 由严格 fail 处理；整数/JSON 输入语法合法但不安全时仍会在 readBoundedJson 失败。
- 最小修复：打开对话框与确认本项使用纯解析/数组/Schema/唯一 ID 检查，局部显示错误且保留草稿；采用前复查同一纯结果。关闭/取消与真实上下文失效处理保留，不能忽略 baseline 已变化。

### 同组不能降级

- collections 导入修订参数与当前固定 base_version/expected_revision、待恢复 pending 归属不一致，须继续阻止使用，不能静默替换基线。
- `readPendingTemplateCreate/readPendingCollection` 的持久元数据核验、prepare 的原 key/body/context 一致性、保存回执/重新读取核验保持严格。
- collections 普通表单已对名称、理由和成员重复做局部检查；template-approval 入口 ID 已有 entryError，不重复扩写安全路径。

### 组二建议验证

坏 JSON、重复键、超安全整数、缺字段、重复成员/稳定 ID、同 version_id 多摘要均不清会话/不发请求/不破坏现有输入；有效文件可导入并进入原严格提交；固定恢复基线和原 key/摘要不符仍拒绝。

## 第三组：来源权利 / EXACT 字段与恢复 JSON

### 3A. 来源权利恢复 JSON

- `web/src/views/medical/source-rights.vue:231–241 recoverOriginal()`。
- 链：裸 `JSON.parse(originalJson)` → `rightsInputIssues(body)` 不通过直接 fail。
- 最小修复：语法、重复键、安全数值、结构/普通输入语义局部报错，保留恢复文本和 pending；通过后再执行原请求摘要及权限核验。
- 当前普通权利预览已有 `inputIssues`；不能将整个 rights preview 标成无输入预检。
- `metadata.body_sha256 !== requestBodyDigest(body)` 及 `submitSourceRights` 对原主体/上下文/key 的核验必须保留，不属于本次可删除的错误。

### 3B. EXACT 固定版本读取和完整核准

- `web/src/views/medical/source-exact-fields.vue:159 readFields()` 将手填 `fixedRevision` 直接传给 `source-exact.ts#readExactFields` 的严格 ID validate。非空错误 UUID 可正常点击提交。
- `source-exact-fields.vue:228 submit()` → `send()` → `source-exact-domain.ts:62 verifyExactPublication()`。
- 按钮只排除空理由/空修订等；普通输入仍可产生非数字修订、理由首尾空白/NFC、别名与原值相同、别名理由不规范、重复字段原值/位置/别名等错误。
- `addField()` 调用 `verifyExactField()`；应在可编辑字段来源处补纯预检，不因直接选择原文就假定所有字面量/字段约束已满足。
- 最小修复：固定版本字段提示；核准/字段新增的纯语义 issues；不改 `verifyExactManifest/verifyExactReceipt/verifyExactPage` 等权威结果核验。

### 3C. EXACT 恢复 JSON

- `source-exact-fields.vue:244 resend()`：裸 JSON.parse → `verifyExactPublication` → 原 body digest 核查。
- 最小修复：先纯解析及 PublicationInput/字段语义预检；局部错误保留文本和原 metadata。摘要不符仍严禁重发，不换 key、不清原记录。
- 原字段 manifest 与当前 parse/index/approval/binding/rights 固定依据不一致、当前来源和原元数据归属不符、读取回执不一致，全部保持 fail-closed。

### 组三建议验证

错误固定版本 ID、核准修订/理由/别名、重复字段、恢复 JSON 语法/结构错误均不清会话；有效输入才进入严格核准；原摘要/原 key/主体/固定来源不符、EXPIRED 恢复及响应伪造仍按既有严格规则阻止。

## 修复执行约束

- 不修改 `protocol.ts` 和 `main.js` 的全局安全语义。
- 不通过 catch 已派发的 MedicalFailure 来伪装局部错误；纯预检必须在 strict API 调用前完成。
- 不自动重新生成幂等键，不删除 pending，不修补/替换用户的原输入或固定依据。
- 三组可以独立修改各自组件与局部 helper/test；若需共享 JSON 纯预检 helper，先约定一个文件所有者，避免并发覆盖。
- 先完成具体可复现入口的组件 setup 回归，再由 root 聚合 typecheck/build。focused tests 不等于浏览器业务验收。
