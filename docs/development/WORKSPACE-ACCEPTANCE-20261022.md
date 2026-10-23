# WORKSPACE-ACCEPTANCE-20261022 本地工作站功能验收

本记录为用户2026-10-22五项要求的唯一整体任务状态来源，当前IN_PROGRESS。代码基线main b0a3591及当前全部未提交修改；本地前端18083、后端18080，沿用medical_local及受保护本地凭据。用户授权本地功能实施、测试数据写入、已有高保真设计比较和全部页面功能核查。

## 当前要求与验收

| 任务 | 验收要求 | 记录及当前状态 |
| --- | --- | --- |
| WA-01 | 组织与账号绑定；普通账户自动进入有效成员组织，超管保留一个默认组织；重新登录保持绑定 | ACCOUNT-WORKSPACE-20261022.md，ACCEPTED |
| WA-02 | 二级菜单不显示icon，一级分类继续使用现有图标与完整导航 | BUSINESS-MENU-20261021.md，ACCEPTED |
| WA-03 | 本地全部功能具有明确标识的验收测试数据，记录业务关联、来源及适用范围 | ACCEPTANCE-DATA-20261022.md，IN_PROGRESS |
| WA-04 | 企业资料上传、模板审查、规则管理、文稿保存审计与高保真交互逐项比较，补充缺少的功能 | PAGE-COMPLETENESS-20261022.md，ACCEPTED；四项本地核心操作通过，后续处理边界另列 |
| WA-05 | 其余全部入口及相关详情页面核查真实实现、接口、权限和运行结果，形成逐页证据 | PAGE-COMPLETENESS-20261022.md，ACCEPTED；47项入口及源码核查完成，完整业务缺项另列 |

依据为用户当前要求、PRD、设计稿.html、ui-delivery/final-product-design.md、screen-design-map.csv、现有实施计划、开发记录和当前源码。已经检索GLOBAL-ORGANIZATION-20261021、BUSINESS-MENU-20261021、NAVIGATION-SESSION-20261022及当前剩余功能清单；历史状态需要由本次实际检查确认。

组织绑定只使用有效成员关系，不生成业务授权。测试数据明确使用验收用途和SIMULATION标识，不代表真实客户资料、专业批准或真实供应商成果；已有数据保留。页面比较采用设计源码、规范、DOM与实际操作，遵守当前视觉工具限制。外部供应商凭据、客户接口与真实专家决定依照实际可用输入核查，缺少时登记具体限制并继续其他任务。

执行依赖：组织绑定的数据与服务完成后验证账号登录；菜单可以独立修改；数据登记完成后进行需要非空记录的页面业务验收。S008负责实施与逐任务记录，S009负责真实服务和浏览器验证。本任务使用持续交付方式，逐项实施及验收，整体完成依据上述全部要求的实际结果。

## 当前交付与验证

WA-01的超管、普通成员、未分配成员和独立停用成员专项均通过。WA-02的二级图标数量为0。WA-03已经登记主验收项目、任务、运行、正文、模板、模型配置、规则、预算、实际资料上传、普通账号及若依管理资料；完整评测、正式发布和专业批准资料仍缺少有效输入。

WA-04已经补充企业项目上传表格与拖放上传、已授权模板目录、日期范围审计表单与记录表格。实际上传完成命令、规则退回和公开素材规则核准通过；公开素材模板创建201、人工核准200、原操作完成核查及核准后基线读取通过，模板为fdfe03b4-145e-47e9-85c4-db0b77b5ff40，证据为.implementation-work/public-template-approval-confirmed.log。正文实际保存返回201，审计查询、固定版本快照及完整正文读取通过，证据为.implementation-work/document-save-audit-checks.json。WA-05已经逐个核查47个菜单入口，并形成113条路由记录及182个Vue文件的源码编译清单；尚未把入口可访问登记为全业务完成。

实际运行事件使用单处Header写入。工作区运行面板仅在展开时读取和连接事件流；文稿导出准备按当前文稿范围的明确权限读取。正文与会话的固定JSON复制使用现有规范化函数，冻结后的内容继续用于保存依据。文稿请求等待上限为120秒，服务端传输期限和授权检查继续执行。本地JVM使用128MiB初始堆内存及1024MiB最大堆内存，配置记录进入本地部署登记。SQL解析对象采用Fury序列化，登记固定JSQLParser依赖中的类型，缓存仍为32MiB有界容量，保存五分钟；每次读取返回独立解析对象，当前组织检查继续执行。SqlParseCacheTest与TenantAccessTest共11项通过，证据为.implementation-work/acceptance-sql-cache-verification.log，当前后端制品标识见受保护部署登记。

当前前端635项固定Schema引用、452处消费调用检查及类型检查通过；生产构建通过，4164个模块转换完成，使用4GiB构建内存，证据为.implementation-work/acceptance-final-front-build.log。工作台通过实际页面确认1个项目、3项任务、1项运行及1项待办。WA-03仍缺少完整专业评测、正式发布及客户连接输入，整体保持IN_PROGRESS。

47个已授权菜单入口的完整复验通过，页面内容均非空，会话、页面及接口错误记录为空；实际时间为2026-10-22T10:21:16.431Z，证据为.implementation-work/all-pages-acceptance.json及.implementation-work/all-pages-verified-acceptance.log。该结果表示入口渲染、会话及读取核查通过，完整业务动作依照各专项证据判定。

依赖版本批量读取按领域及版本标识分别保持行身份。VersionResourceMappingIT使用已经登记的四项真实素材，以及已核准规则和两项实际依据，验证同类版本同时返回；测试不创建替代查询结果。TemplateAcceptanceSchemaIT、VersionResourceMappingIT、SqlParseCacheTest与TenantAccessTest共14项测试通过，失败、错误和跳过均为0，完整后端构建及最新前端打包通过，证据为.implementation-work/acceptance-final-backend-verification.log。

模板依赖清单从完整请求的规范JSON编码提取，输入属性排列不改变重新校验结果。实际验收模板的完整定义及五项固定依赖在编码前后保持一致，测试堆内存为1024MiB。验收授权准备脚本只为有效PLATFORM_ADMIN主体补充对应权限，实际运行验证通过。

模板完整审查、创建及决定提交采用120秒独立请求等待上限。取消、当前权限、完整依赖及原操作核查继续执行；实际模板基线200、核准200及操作完成均已验证。前端类型、635项固定Schema引用和452处消费调用检查通过，证据为.implementation-work/acceptance-template-front-typecheck.log。

工作站环境的默认请求等待上限为60秒，文稿及模板继续采用各自120秒上限。原管理响应与传输错误均检查请求对应的当前会话，已经离开的页面只报告取消状态。类型及全部固定Schema消费检查通过，证据为.implementation-work/acceptance-final-front-typecheck.log。

真实业务页面及若依管理页面连续切换、三次快速切换专项通过，页面错误为空，证据为.implementation-work/navigation-final-session-verification.log。后端验收复现入口为bash scripts/verify_acceptance_backend.sh；本地资料集验证使用明确选择的TemplateAcceptanceSchemaIT、VersionResourceMappingIT，SQL缓存及组织隔离使用对应测试类。

最终运行核查通过，时间为2026-10-22T10:40:25.580471+00:00。后端PID40953，制品SHA-256为6043db32a9550260a828f9ef2632fdbd7d178b0356e374f93d0e51f4c8da59be；552份生产前端文件与运行Jar逐项一致，18080及18083的真实HTTP入口、MySQL SELECT 1与Redis PING全部通过。证据为.implementation-work/acceptance-final-runtime-health.json。

用户当前浏览器使用liuhaonan实际表单登录通过，保持在/index；AX读取确认超级管理员、47项可用功能、当前默认工作组织，以及1个项目、3项任务、1项运行和1项待办。首页没有会话读取错误，浏览器未使用截图。

正文保存同时需要document.write与document.check，服务端在保存入口核查这两项权限，文稿快照仅在两项许可都有效时声明document.edit。后续检查任务继续在同一保存事务登记，保存本身不产生检查通过或专业批准。主验收超管的document.check与audit.read按本任务权限需求登记，其他普通账号权限分别验证。

本地MySQL连接启用100项预编译语句缓存，单条缓存SQL最长32768字符，并使用服务端预编译。原查询结果、权限判定与组织条件继续实时读取，使用范围及行为依据[Connector/J性能配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-performance-extensions.html)和[预编译配置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-prepared-statements.html)。不支持的预编译立即报告错误。
