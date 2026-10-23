# PR-01 模板中心与章节配置

## 任务身份与依据

任务来源 PROTOTYPE-RESTORATION-20261022，状态 IN_PROGRESS，文档 CURRENT。对应 S04、FR-05/09、AC-18/45/46/50/57。检索 IMP-11、PAGE-COMPLETENESS-20261022、template-create/approval/definition-editor 与 templates.schema.json，采用原型十三类型导航、章节与生成规则表格、适用范围、版本记录及审核发布操作。

## 当前功能与实施要求

模板目录按文档类型组织，章节配置使用名称、内容要求、处理方式、指定依据、检查规则及适用条件。版本修订从已授权原模板复制完整定义和依据，在当前页面编辑，提交后形成新版本。依据选择器从真实目录读取版本与摘要；条件规则带入真实引擎及输入要求。高级JSON保留在单独展开区域，不作为主要业务操作。

## 决策与约束

保留全部模板字段、格式、字体、许可、附件、原目录身份和未知条件。来源依据及规则采用固定版本；原核准模板保持完整，修改创建新草稿并独立人工审查。读取、修改、核准和项目适用决定分别授权。

## 验证与证据

PR01-01：十三类型可筛选实际模板，选定模板显示章节、处理方式、依据与检查要求。

PR01-02：基于实际已授权模板创建修订，原始全部定义及摘要正确带入，修改章节要求后保存实际新版本并重读。

PR01-03：指定依据与规则从真实目录选择，完整版本与摘要传入保存请求，规则引擎与输入参数保持真实定义。

PR01-04：实际新版本人工决定及核准后读取验证，缺少依赖或权限时明确拒绝。

PR01-05：Given原模板的完整字段定义，When通过业务控件选择章节并修改字段说明，Then保存后读取新版本保留字段限制、证据规则及输出位置。

PR01-06：Given实际完整模板及其已授权字体、版式与规则，When使用业务控件配置模板适用属性、全部依据和输出类型，Then完整新版本保留固定依据及UNKNOWN语义，可以保存、独立人工核准并刷新重读。新增字体与版式来自实际已核准来源模板，不能编造字体许可、版本或摘要。

PR01-07：Given实际模板编辑账户具有读取与修订权限且没有核准权限，When读取完整原模板并保存修订，Then创建真实DRAFT并可以读取详情与继续修订，核准基线与人工决定接口拒绝访问，界面没有核准动作。

PR01-08：Given当前实际授权目录，When使用固定查询身份逐页读取，Then完整游标分页保持版本顺序、没有重复，并能够定位实际新版本。

增加章节、字段限制、枚举、范围依据、适用条件、证据要求、输出位置、表格字段及关联附件控件。2026-10-23 严格类型检查通过，636项固定Schema引用、479项Schema调用检查通过。实际浏览器完成十三类型导航、章节要求修订、字段业务说明修订、新模板保存201、人工核准200及刷新后的已核准状态核查。实际新模板 f43187aa-9313-4b56-a6fa-0cc5c4f1badf，属于明确标记的SIMULATION编辑验证。证据 .implementation-work/prototype-template-business-green-current.log、prototype-template-checks.json、prototype-document-workspace-typecheck.log。没有执行视觉检查。

## 未完成内容

适用属性、全部依据、字体与版式已提供独立业务控件。2026-10-23真实Chrome通过适用条件确认、实际已核准来源模板选择、固定字体与版式采用、DOCX输出配置、新模板保存、人工核准及刷新重读。新版本7c1ecd4f-00b7-48bf-9e23-022ed6a39ae9，原字体与版式摘要保持一致，UNKNOWN语义保留。证据：.implementation-work/template-profile-controls-release.log、template-profile-controls-checks.json。严格类型检查与489项Schema调用检查通过，证据：.implementation-work/template-notice-directory-types.log。

完整分页及独立读取权限入口已经通过真实接口与Chrome核验。普通编辑账户读取已核准原模板，保存新草稿44f19175-51ad-4abf-bdc0-99be5a27a3ab，中文查询后进入模板详情并继续修改；界面没有人工决定，直接核准基线与人工决定请求均为403。实际目录逐页读取7页、7个版本，顺序连续且没有重复；模板与规则中文命中及模型中文空结果均通过。证据：.implementation-work/template-definition-read-release.log、template-definition-read-checks.json、template-editor-without-approval-release.log、template-editor-without-approval-checks.json。全部十三类型的依赖与错误分支未完成验收，任务保持IN_PROGRESS，没有执行视觉检查。

本地SIMULATION工作组织中的普通测试账户登记TEMPLATE_EDITOR_WITHOUT_APPROVAL角色，包含TEMPLATE_MAINTENANCE用途下的template.read/template.write/source.read/rule.read/artifact.read五项授权，没有授予template.approve或转授能力。授权、审计、outbox与组织权限修订在同一事务保存。配置脚本scripts/configure_template_editor_workspace.py，实际凭证.implementation-work/runtime/template-editor-workspace-authorization.json。普通账户身份和默认工作组织绑定保持有效，权限范围及后续实际业务核验均属于当前模板任务。
