# PR-06 文档任务与创建流程

当前状态IN_PROGRESS，文档CURRENT，任务来源PROTOTYPE-RESTORATION-20261022。依据PRD的FR-04、BR-17、BR-19、AC-07、AC-27、8.2.1能力发布配置和8.5申请资料，采用product-workspace.js的S06/S07及当前若依导航。

TS-01：Given当前项目的获授权任务，When进入任务列表或选择阶段、人员及时间，Then按照完整服务端筛选展示文档与版本、状态、负责人、截止时间、批注及操作，保存完整分页范围和版本水位。撰写与核验包含原型中的待补资料、运行和退回；审查与签发包含审查、签发等待和已签发。实际其他状态保留独立入口，不丢失取消、失败及计划确认信息。缺少实际截止时间时显示尚未设置，不能产生日期。

TS-02：Given实际项目及明确文档目标，When进入创建页面，Then显示创建任务、撰写与核验、审查与签发三个横向流程阶段，创建前禁止进入后续阶段。页面按原型并排组织输入和实际章节计划；章节来自所选核准模板及实际适用判断。

TS-03：Given当前组织、项目及任务创建许可，When选择Agent配置，Then目录只提供实际已发布且获授权的版本，固定原始发布身份、摘要、范围和依赖。任务提交继续经过TaskCreateReleaseAdmission、AgentReleaseStore及AgentReleaseScopeAuthority完整校验。没有可用发布时显示具体服务状态和准备入口，不能通过手工批准记录或替代运行结果满足条件。

TS-04：Given实际责任人员、核准模板及发布配置，When选择来源和核对任务输入，Then保留实际人员资格、资料使用权利、固定版本、明确空范围、网络批准策略和原请求恢复。成员姓名读取继续要求企业成员读取许可；人员候选继续采用实际task-creation-owners资格范围。

已读取tasks.vue、task-create.vue、task-creation-selection.ts、template-applicability.ts、AgentReleaseStore、AgentReleaseScopeAuthority、TaskCreateService和TaskCreationSelectionService。当前任务创建具备完整原请求恢复、兼容模板查询、责任人员资格与资料范围接口；业务名称、发布配置目录、横向流程和并排章节计划继续实施。任务阶段筛选需要完整服务端范围，不能仅过滤当前页面。

实际创建、依赖变化、权限变化、空范围及原请求恢复均需要独立业务验证。当前TS-01至TS-04保持IN_PROGRESS，没有执行视觉检查。

任务创建的发布配置目录已增加Controller、Schema和Mapper代码，以及AgentReleaseScopeAuthority的资格查询方法；当前尚未完成编译验证、实际接口验证和前端接入。本地当前组织没有已发布配置，已发布配置的正向业务流程尚未验证。
