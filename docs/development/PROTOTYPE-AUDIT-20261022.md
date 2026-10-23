# PR-04 文稿保存审计

## 任务身份与依据

任务来源 PROTOTYPE-RESTORATION-20261022，当前 IN_PROGRESS，文档 CURRENT。对应用户要求及 S15 操作审计。检索 PAGE-COMPLETENESS-20261022、文稿实际保存测试与 business-audit.ts，当前页面读取真实保存记录，人员、文稿及版本以技术标识展示，正文在表格后展开。

## 当前实现要求

统一运营分区、项目及日期筛选、结果表格、保存理由和版本信息、实际正文阅读。通过已授权任务目录取得文档类型及所属任务；人员名称来自当前企业实际成员目录，按照成员读取权限提供分页选择，没有目录权限时保留本人名称及明确说明。保持固定版本、来源权限、真实扫描和分页语义。默认显示当天时间范围并读取首个获授权项目，筛选变化清除旧结果。正文使用抽屉连续阅读，技术标识在详情中查看。

## 验证与证据

PR04-01：Given真实保存记录，When选择对应项目及时间，Then记录显示实际人员、操作、文稿及版本，打开固定正文可完整阅读。

PR04-02：When修改筛选、离开页面或更换组织，Then旧记录与正文清除。

2026-10-23实际浏览器完成项目选择、包含实际保存时间的历史筛选、正式任务文稿名称读取、版本详情及固定版本完整正文阅读，接口均返回200。证据 .implementation-work/prototype-audit-business-fixed.log、document-save-audit-checks.json。没有执行视觉检查，任务保持IN_PROGRESS。

2026-10-23实际Chrome继续通过企业成员名称目录、保存人员选择与actor_id请求核查、固定正文阅读，以及切换保存人员后清除旧结果与正文。接口全部返回200，浏览器错误集合为空。证据：.implementation-work/prototype-audit-member-directory.log、document-save-audit-checks.json。跨组织变化、受限成员名称与完整分页分支继续核验，任务保持IN_PROGRESS。

文稿选择目录采用完整任务分页并保持同一watermark及filter_sha256。实际目录含有current_revision为空的任务创建草稿，正式列表、计数及分页统一要求已登记current_revision；创建草稿继续通过原创建恢复流程读取。TaskDirectoryCommittedIT核查本地实际创建草稿、正式任务与分页计数，保存审计业务流程继续验证。历史保存核查使用包含实际保存时间的筛选范围。

## 约束与关联

BusinessAuditService 和原保存记录完整性校验继续生效，不将保存记录解释为核准或签发。所需任务目录以 project.task.read 授权为依据，不能扩大读取范围。全部业务操作审计继续纳入 PR-06，保存审计与其各自保留完整范围。
