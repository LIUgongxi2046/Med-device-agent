# 模板与资料集本地输入预检（2026-10-18）

## 修复范围

本次修改 `collections.vue`、`template-create.vue`、`template-definition-editor.vue`，新增局部 `template-collection-input.ts`。不修改全局 protocol/main、后端、冻结 Schema 或既有严格领域校验。

- 导入：实际文件读取失败、损坏 JSON、重复键、不安全数值、缺字段、名称/理由规范、重复资料集来源成员先作无副作用预检。错误显示在当前页面，不清理会话、不写入、不覆盖现有草稿。
- 模板普通表单：各 JSON 段补无损数值预检；完整草稿补计数范围、同一依据版本多摘要冲突预检。不静默改写、舍入或选择一个摘要。
- 结构化模板定义：打开前检查完整数组、字段 Schema 和稳定标识唯一性；确认及采用前再检查。错误保留当前编辑内容；有效内容继续调用原 `checked`/`validate`。
- 所有合法导入继续原 `validateTemplateCreate` 或 `validateCollectionBody`。持久 pending 的原 key、主体、上下文、摘要，以及当前固定修订/依据、真实响应和权限校验保持原严格路径。
- 导入文件读取完成或失败时先检查原会话/代次/页面可见性，防止旧上下文文件回填新表单。

## 运行时回归

命令：`cd web && node --test tests/template-collection-input/input.test.mjs`

17 项通过。使用编译后的真实 Vue setup、真实模板/资料集 Schema 和原严格领域验证，覆盖：

- 两类导入的语法、重复键、不安全整数、缺字段、理由、文件读取失败与文件缺失。
- 资料集重复来源成员、模板依据摘要冲突和超 unsigned 64-bit 计数。
- 合法导入及完整模板表单仍进入原严格领域层。
- 结构化定义的非数组、无效成员、重复稳定标识、不安全数值，及正确修改/采用。
- 原 pending 归属不一致、编辑基线变化继续 fail-closed。
- 会话变化后的迟到文件不覆盖当前草稿或错误信息。

测试不构成浏览器视觉或真实后端联调验收；完整医疗前端 typecheck/build 由协调者统一执行。
