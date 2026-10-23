# FORM-DESIGN-20261021 业务表单设计

任务状态来源为本文，当前ACCEPTED，文档CURRENT，last_verified=2026-10-21 23:43（Asia/Shanghai）。用户要求在若依框架基础上改善大部分业务页面的表单设计，当前页面为/model-configurations。基线b0a3591及当前已有未提交修改。

检索medical.scss、模型配置列表与字段、项目、岗位、预算、企业登记及原有设计记录。当前源码盘点以Vue编译器为准，共71项表单与字段文件、97个ElForm；53项业务页面使用session-panel。本任务通过统一样式及共享状态组件覆盖适用页面，模型配置提供完整的分区表单。

验收：FD-01统一页面标题、表单分区、标签、帮助文字、边距、筛选和操作区域；FD-02模型配置具备清晰的列表、新建、历史版本及字段分组；FD-03移动端、明暗主题、键盘焦点、错误和禁用状态正常；FD-04全部受影响源码编译、类型及生产构建通过，真实业务字段和保存规则保持有效。

继续使用Element Plus与若依布局、当前品牌和图标；表单不改变数据字段、批准要求或服务端动作。使用DOM、计算样式和真实交互验证，遵守用户禁止主动视觉功能的限制。关联GLOBAL-ORGANIZATION-20261021及HOME-ADMIN-RUOYI-20261021。

## 当前实现

| 文件 | 当前职责 |
|---|---|
| web/src/assets/styles/medical.scss | 统一页面标题、区块、双列表单、长文本整行、筛选栏、标签、帮助、错误、禁用、焦点、表格与移动布局。 |
| web/src/views/medical/model-configurations.vue | 模型目录与新建主操作、历史版本展开、当前组织权限提示、范围及保存区域。 |
| web/src/views/medical/model-configuration-fields.vue | 供应商与模型、访问凭据、能力与调用限额、价格与数据政策、请求参数协议五个完整分区，保留全部原字段。 |
| web/src/layout/components/Navbar.vue、AppMain.vue | 移动端顶部104px与标签页34px，内容区域使用相应高度。 |
| web/scripts/audit-medical-form-delivery.mjs | 使用Vue编译器盘点并编译实际表单和字段源文件，生成CSV与证据JSON。 |

规范和实际源文件映射位于ui-delivery/workspace-experience-20261021，包含design-system.md、tokens.json、route-design-map.csv、form-source-map.csv及ui-audit.md。复用当前品牌与图标，没有新增图像资产。

## 验证与证据

- node web/scripts/audit-medical-form-delivery.mjs通过71项表单和字段文件、97个ElForm的实际源码编译，并核查模型五个分区；证据workspace-form-inventory.json。
- node --test web/tests/business-navigation/components.test.mjs通过16项实际组件编译，包含组织选择、模型页面与字段。
- npm run typecheck:medical通过，613项固定Schema引用通过；最终证据workspace-experience-typecheck-final.log。
- 真实账号中，首项组织具有配置读取权限，第二项组织没有该项摘要授权；查询及新建按钮按实际组织正确禁用。模型维护表单通过完整源码编译与分区结构验证；当前12项组织均没有model.configure，因此未执行模型编辑交互或保存。
- 真实企业登记页面具有平台授权，六项字段完成空表单校验、错误正常排列及375/820/1440px布局检查；明暗主题正文分别为rgb(48,49,51)与rgb(255,255,255)，背景分别为rgb(255,255,255)与rgb(20,20,20)。业务申请没有创建，未使用虚构业务值。
- 开发交互证据为workspace-experience-browser.json、workspace-experience-green-final.log；4159模块生产构建和最终生产预览通过。生产环境重复验证实际字段校验、错误正常排列、键盘焦点、375/820/1440px、顶部内容位置、明暗主题，业务申请写入0、错误为空。证据workspace-experience-build-final.log、workspace-experience-production.json。源码摘要见workspace-experience-source-sha256.txt。

本任务没有承诺供应商调用、模型批准或模型维护授权。全部设计修改继承现有保存与批准流程。截图、视觉比较及无授权的模型编辑交互未执行，不能据此声称全量业务流程已经验收。

FD-01—04在上述设计实施、源码编译、当前获授权表单及权限状态范围内通过，没有剩余必需实施；模型维护与保存继续依照原业务授权验收。
