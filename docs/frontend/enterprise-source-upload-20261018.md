# 企业资料上传入口

## 已实现
- 新页面 `web/src/views/medical/enterprise-source-uploads.vue`，root 已集成路由及 SPA 映射 `/sources/uploads`，**name 必须为 `MedicalEnterpriseSourceUploads`**（当前路由读取隔离使用该名称）。复用现有 `listProjects`，ACTIVE、每页50条，只展示当前页，失败清空游标，撤销会话/离开路由隔离迟到响应。
- 用户选择真实获授权项目后进入 `/projects/{projectId}/uploads?ownership=ENTERPRISE`。现有项目上传入口及请求结构保留。企业模式的新声明默认并锁定 ENTERPRISE；处理请求的 `project_id` 仍为真实项目 UUID，不能填 null。
- 原上传恢复沿用原key、完整原声明与摘要。未查明写入结果不创建替代身份；企业入口不会改写旧项目/会话上传的归属。
- 企业资料仍走原来源声明、PENDING_REVIEW 与人工核准，不生成授权或自动核准。

## 服务端依据及真实边界
- `source-uploads.schema.json` Create.project_id 必须 Id；`SourceUploadService.requireAuthority` 要求上传项目 ACTIVE，同时 ENTERPRISE 的 source.write scope 使用 ownerProject=null。
- `SourceCompletionRegistration` 第61–66行明确将 ENTERPRISE ownerProject/targetProjectId 置空，第103行以同一 ownerProject 建立 pendingRights。
- `SourceGovernanceMapper.xml` insertSubmissionSource、insertSubmissionSourceVersionRegistry、insertSubmissionRightsRegistry 均写入 targetProjectId。来源与rights版本真实归属为空项目。
- `SourceUploadStatusService` 第54–56行重验 source.ownerProjectId、source registered project、current rights registered project、head ownerProjectId 全部一致。审批门禁保持现有服务端来源规则。
- 前端 `readSourceUpload` 将回读完整声明摘要与原 journal.declaration_sha256 对比，企业 ownership 属于这个摘要。当前 status DTO 未暴露 owner_project_id，前端不会虚构已独立验证该字段；归属的完整关系核验依靠上述服务端检查。

## 验证
`node --test web/tests/source-file-upload/enterprise-runtime.test.mjs`：4/4 通过。实际编译 Vue SFC setup 后运行，覆盖项目原payload、企业payload保留真实处理项目、未知登记结果保留pending、会话切换中旧文件摘要丢弃、项目分页错误旧游标拒绝、撤权迟到响应隔离。

未运行全局typecheck/build、Maven；未进行真实HTTP/S3/数据库或浏览器验收。路由及 SPA 映射已由 root 集成，独立页面链接由准备页面负责人接入。
