# ACCEPTANCE-DATA-20261022 本地功能验收数据

任务状态来源为本文，关联WORKSPACE-ACCEPTANCE-20261022 WA-03，当前IN_PROGRESS，文档CURRENT。用户明确授权全部功能注入测试数据，数据使用验收用途和SIMULATION标识；原有数据保留。专业批准、客户事实及外部供应商调用结果不能由测试数据代替。

已读取sample_dataset.json、实际本地数据库、ConfigurationWorkflowSimulationData、SimulationTaskData、SimulationDocumentData及当前接口。初始数据库存在24个组织、36个项目、60项任务、14个模板版本、2项来源及2个文稿版本；规则、模型配置、评测结果与若依部门、岗位、公告、任务配置等目录没有记录。实际盘点脚本为scripts/inspect_acceptance_workspace.py，结果为.implementation-work/acceptance-workspace-inventory.json。

数据创建包含可追溯登记、真实对象存储读取、跨对象关联和当前登录账户读取权限。使用现有正式服务及已定义数据结构，覆盖非空目录、草稿、待处理和结果重读；没有外部配置的能力保持明确待配置状态。

## 当前已登记资料

主验收工作组织为53f448c6-c5a5-4e91-894b-30be5232d66d，项目为d783e1d9-33d7-4532-bdbe-1de108698e17，任务为0ea64430-30e7-4694-90ec-ff98cc5daf2d，文稿为123d7bbf-a1f5-465a-a00c-83da28e1ba76。名称、正文和理由明确包含验收用途及SIMULATION。完整关联登记保存在受保护的.implementation-work/runtime/acceptance-workspace-seed.json。

| 功能范围 | 数据与验证 |
| --- | --- |
| 项目、任务、运行与文稿 | 当前主验收项目、已确认章节计划、固定资料范围、任务正文和运行记录；实际保存修订及其审计记录、固定版本完整正文读取通过；使用真实MySQL与对象存储，不调用外部模型 |
| 模板目录 | 主验收组织中的固定模板元数据、目录节点、字段定义及完整定义读取入口；公开字体、完整许可、自拟DOCX版式及编辑说明已通过实际对象存储登记，真实创建、完整审查及人工核准全部通过 |
| 模型配置 | 两项实际模型配置草稿，主验收组织与独立配置组织均有记录；草稿不包含模型连接成功声明 |
| 规则管理 | 实际草稿、退回决定和公开素材模板采用的编号字段存在性规则核准；每项理由明确标识SIMULATION用途，专业适用性独立核查 |
| 预算 | 验收预算账户、不可变预算版本和任务预算关联；账务用途与额度占用按实际服务验证 |
| 企业资料上传 | 自编TXT、真实来源许可声明、分段字节、完成命令和实际状态重读；处理工作进程未配置，记录保持实际待处理状态 |
| 账号与成员 | liuhaonan超管、acceptance_member普通成员、acceptance_unassigned未分配账号、acceptance_revoked已停用成员；无组织与已停用访问拒绝通过 |
| 若依管理 | 实际部门、岗位、参数、公告及账号目录；操作日志和登录日志来自实际执行 |
| 代码生成 | 单独framework_dev_acceptance数据库、独立数据库用户；sys_dept表结构通过真实接口导入，生成元数据、列定义及代码预览通过，业务数据库没有作为生成目标 |
| 资料文件夹 | 真实创建并重新读取SIMULATION企业验收资料文件夹，归属当前企业 |
| 项目保存策略 | 仅适用于主验收项目，实际登记2099至2100年的未来策略，当前状态SCHEDULED；通过真实写入及详情摘要核验，没有执行数据清理 |

本次盘点的medical_local共有1029项迁移、568张基础表、42项项目记录、70项任务、18个模板版本、3个规则版本、2个模型配置、11个预算修订、4个文稿修订、1项文稿检查意向、4个本地账号、11项部门、5项岗位、10项参数及4项公告。实际模板核准决定1项、规则决定2项；专业评测结果及正式能力验收记录均为0。这些数量包含原有资料，不能作为本次新增数量。数量与时点以acceptance-workspace-inventory.json的verified_at为准。

## 尚未具有完整验收数据的功能

正式评测结果、独立专业检查、正式候选发布与客户连接仍需要各自有效输入。专家资格、完整评分、模型服务与客户实际连接尚未具备时，相应结果保持待验证状态。

实时在线会话、监控统计、缓存和操作日志采用当前实际状态，不预置为运行事实。定时任务需要已注册处理程序；当前没有可用注册项，不能通过写入任意调用字符串填充目录。反馈与用量的高保真页面尚缺前端接入，其现有后端数据不能作为页面已经完成的证据。

资料文件夹及项目未来保存策略专项通过，证据为.implementation-work/acceptance-support-data-verification.log及受保护的runtime/acceptance-support-data.json；原请求身份保留，重新运行脚本核查原策略操作，不重复创建策略。

隔离代码生成资料专项通过，证据为.implementation-work/generator-data-checks.json。测试读取导入结果、全部列定义及真实生成预览；没有执行源码文件写入。

公开模板素材使用[Noto CJK字体](https://github.com/notofonts/noto-cjk/blob/main/Sans/README.md)及其[SIL Open Font License 1.1](https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE)。原始字体及完整许可保留；自拟A4版式使用python-docx生成并重新读取，字体使用fontTools核查名称与实际文字字形。字体和DOCX的ObjectRef固定实际二进制对象身份，许可及说明使用正式TextContent结构。四项制品均为SIMULATION，S3对象版本、完整摘要和登记结果见受保护的runtime/acceptance-template-assets.json；没有登记监管批准或客户事实。

公开素材模板fdfe03b4-145e-47e9-85c4-db0b77b5ff40已经通过真实创建及人工核准，状态APPROVED；编号字段存在性规则b153b97d-340a-47ab-8a3c-09effb6735e9同样通过真实核准。实际决定、原操作身份及核查结果见受保护的runtime/acceptance-template-workflow.json；浏览器专项证据为.implementation-work/public-template-approval-confirmed.log。

因此WA-03继续保持IN_PROGRESS，尚未登记全部功能数据注入完成。
