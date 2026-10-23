# PR-06 页面验收运行支持

## 任务身份与依据

任务来源 PROTOTYPE-RESTORATION-20261022，当前 IN_PROGRESS，文档 CURRENT。检索 NAVIGATION-SESSION-20261022、SqlParseCacheTest、FailFastSqlParseCache 和真实进程线程记录。后端首次读取期间，实际请求等待 Fury JITContext 编译锁，健康请求曾超过10秒；线程证据为 .implementation-work/prototype-runtime-threads.log。

## 实现与约束

使用固定 Fury 0.9.0 提供的配置关闭动态生成和异步编译，继续使用同一依赖的序列化实现。完整类型登记、32MiB容量、五分钟有效期、每次独立AST副本、损坏立即报告错误及当前企业检查保持有效。缓存不保存查询结果和授权。

## 验证

PR06-RUNTIME-01：实际Mapper SQL在多个线程分别修改读取副本后，其他读取保持完整原结构。

PR06-RUNTIME-02：原缓存五项独立副本、类型和损坏检查，以及真实后端启动、登录和页面读取验证。

2026-10-23 使用固定JDK17及Maven3.9.16完成后端构建。ProjectDirectoryCommittedIT在真实数据库及当前会话授权范围执行，1项通过，没有失败、错误或跳过；证据.implementation-work/project-profile-release-backend.log。相关领域的TenantAccessTest 6项、VersionResourceMappingIT 2项、SqlParseCacheTest 6项、TemplateAcceptanceSchemaIT 1项、KnowledgeFeedbackProjectIT 2项、TaskDirectoryCommittedIT 1项、ConfigurationDirectoryUnicodeIT 1项，共19项通过，证据.implementation-work/rule-directory-backend.log。

当前运行制品79bdddbefa062b3c791dd0651d74361956c46d1b26c94118b802e67bc56c8ed2、PID51261，既有证书及来源服务配置有效。前端正式构建通过4235项模块，证据.implementation-work/project-profile-release-build.log；646项固定Schema和492项调用检查及严格类型检查通过，证据.implementation-work/project-profile-release-types.log。构建制品中的592项静态文件与web/dist逐字节一致，18080/18083、MySQL SELECT 1及Redis PONG核验正常，证据.implementation-work/project-profile-runtime-release.json。

实际超管与普通账户的项目目录、完整属性、实际来源统计和游标权限复验通过，证据.implementation-work/project-profile-api-release.log。项目、规则、模板和保存审计的标题、控件参数及既有深色主题复验通过，证据.implementation-work/project-profile-tokens-release.log。项目属性实际保存、完整重读及未保存输入处理通过。其他页面继续按照各任务记录核验，整体状态保持IN_PROGRESS。
