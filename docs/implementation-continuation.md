# 当前开发续接

用户要求优先完成全部已定义功能的代码和集成，期限为2026-10-10上午09:00，时区Asia/Shanghai。全部业务输入使用明确标记的SIMULATION；数据库、对象传输、权限及服务调用继续使用实际组件。正式客户验收独立登记，不等待真实资料，不进行没有新依据的重复检查。目标ACTIVE。

唯一任务状态读取[实施任务计划](../实施任务计划.md)§4.2；全部42项工单、57项业务、17项非功能、18项边界、12项EVAL及30项SEC保持完整。当前16项IN_PROGRESS、1项IMPLEMENTED、25项TODO、零项ACCEPTED。各项主记录和当前证据通过[开发索引](development/index.md)读取，不把辅助检查数量计入功能完成度。

## 当前业务与继续开发

- [IMP-12](development/IMP-12.md)：任务创建、来源选择、计划编辑、确认与退回具有公开接口和页面；C06实际发布准入消费者仍需接入。
- [IMP-16](development/IMP-16.md)：同版正文读取、上传、修订保存、字段与表格编辑、三版本比较、会话草稿、撤销恢复和固定历史读取已进入代码。候选读取/比较/采用、历史目录、完整图片与公式呈现及后续检查继续实施。
- [IMP-07](development/IMP-07.md)：实际模型SDK及明确SIMULATION响应具备完整文本、工具及固定输入检查；C07公开配置、调用台账、费用预留、结算、未知调用核对和业务消费继续实施。
- [IMP-13](development/IMP-13.md)：Workflow固定身份、DAG、调度、等待、Signal、取消证明及实际Temporal传输已经验证。C04持久RunActivities、运行接口、令牌、Outbox、消费者回执、专业Worker和工作区继续连接。
- [IMP-27](development/IMP-27.md)：实际RN-02读取持久运行、步骤、待办及固定预算；RN-03完整事件读取和游标续读通过实际HTTP检查。RN-04取消接口通过实际MySQL、Redis及HTTP检查；取消页面、原请求恢复、strict类型和完整生产构建通过。当前任务、步骤、原操作、Outbox命令及PublicEvent保持同事务取消状态；人工继续、专业执行及完整页面业务继续连接。
- [IMP-14](development/IMP-14.md)：完整官方程序及全部运行资源已生成；medical-agent、enterprise-model、全部工具定义及七项角色范围已进入professional-executor。严格构建、官方SDK专业初始化、实际十三项插件清单、Planner四项工具清单及六项模型/Unix Socket工程集成检查通过。Python宿主、专业Activity、持久授权、C07业务调用及完整取消证明继续连接。
- [IMP-26](development/IMP-26.md)：三个实际成品格式的生成与重读已经通过，保留完整正文、表格、公式、图片和SIMULATION标记。公开导出状态、授权下载、签发同版检查、目录页码、格式规则、页面和成果转存继续连接。
- IMP-09/15/17—25/27—28：继续授权检索、专业生成与独立检查、全部注册文档路径、当前正文审查、再次认证签发、共享事实影响、Gru及评测发布。
- IMP-30—35：按照已有场景定义和已授权模拟配置继续开发；未知业务要求保留明确缺项。
- IMP-36—42：完整构建、本地部署、集成、安全、非功能、恢复、SaaS及私有化交接继续全部已定义要求。

## 当前有效证据

各项回执的完整摘要和适用范围由唯一主记录维护：

- 正文实际保存及历史读取：IC-12-PLAN-WRITE-20261009-DOCUMENT-HISTORY-01。
- 正文前端与完整表格操作：IC-12-PLAN-FRONT-20261009-DOCUMENT-RICH-HISTORY-01及IC-16-DOCUMENT-STRUCTURE-TABLE-EDITOR-01。
- 当前运行身份：IC-13-WORKFLOW-20261009-RUN-IDENTITY-GREEN-02；实际Temporal传输：IC-13-WORKFLOW-20261009-ACTUAL-DELIVERY-02。
- 运行持久读取与实际HTTP：IC-13-RUN-READ-20261009-ACTUAL-HTTP-03。
- 运行完整事件和游标续读：IC-13-RUN-READ-20261009-EVENT-ACTUAL-HTTP-06；完整输入及授权SQL：EVENT-INPUT-GREEN-01和EVENT-AUTHORITY-SQL-03。
- 当前取消接口：IC-13-RUN-READ-20261009-CANCEL-ACTUAL-GREEN-01；取消页面strict与生产构建：IC-27-RUN-FRONT-20261009-CANCEL-UI-03。
- 当前专业SDK、完整工具定义及模型/Unix Socket集成：IC-14-PROFESSIONAL-20261009-ADAPTER-GREEN-01。
- 模拟模型与完整安装包：IC-07-SIMULATION-20261009-SIMULATION-GREEN-01及IC-07-PACKAGE-20261009-SIMULATION-PACKAGE-01。
- 三个实际导出格式、完整投影与安装模块：IC-26-EXPORT-20261009-INSTALLED-FULL-PROJECTIONS-01。

业务数据库、Redis、S3与Temporal由已有本地服务承载。新正文、Workflow和导出代码尚未集中部署；常驻应用的旧制品不能证明当前源码功能。执行必要检查后集中构建与部署。临时内容保存在已忽略的.implementation-work，禁止使用/tmp，禁止Git恢复，文件编辑只使用apply_patch，禁止视觉工具，禁止子代理。
