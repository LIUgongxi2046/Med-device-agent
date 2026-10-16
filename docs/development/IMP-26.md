# IMP-26 固定成品导出与成果转存

唯一任务来源为[实施任务计划](../../实施任务计划.md)的IMP-26。依据为PRD§8.5.3/8.5.8/8.9.1、LLD-后端EX/KN-18与§5.5、LLD-数据§6.4以及Agent N08/V09。当前开发使用SIMULATION业务输入；生成、解析和转换采用实际库及LibreOffice。

本次检索既有medical-document、DocumentBody、正文结构检查、公开成品接口及ExportManifest设计。C09沿用medical-document，使用python-docx保存DOCX、成熟公式转换库生成OMML、LibreOffice转换PDF及pypdf重新读取。所有输入固定完整正文、模板、字体、转换器、用途和版本；未知条件立即拒绝。

导出包含完整章节、原始文字及数值、文字格式、合并表格、公式、固定图片和说明。FORMAL_BODY保持正式正文；内部证据和字段投影单独保存。独立格式检查及不可覆盖的尝试目录保留原成功文件。受理渠道保持PENDING_REVIEW，视觉版面尚未验证。

## 当前代码与验证

2026-10-13EX-01及EX-04使用原请求元数据，创建导出和指定格式重试保留原幂等键。提交后读取实际Operation及ExportView；恢复查询按固定文稿、导出及原格式身份核查结果，完成后清除元数据。NOT_FOUND保留原参数和原键；EXPIRED要求明确开始新的请求；PROCESSING保持等待。恢复查询只读取原操作和导出状态，排队格式继续提供明确执行入口。格式重试以新尝试编号核查原格式及递增次数。

document-export.vue在页面、会话或文稿版本变化时清理输入、记录、下载链接及当前请求；创建、重试、执行、下载的响应只有在当前generation及会话有效时更新界面。原请求元数据检查由[IMP-05](IMP-05.md)维护；公开导出、未知结果恢复及成果转存仍需完成完整SIMULATION业务集成。

2026-10-13前端下载取得完整ArrayBuffer后，对照固定格式核验记录重新检查文件长度和SHA-256，通过后才生成下载Blob。失败格式、缺少文件制品、缺少核验制品和内容变化立即拒绝。Object URL在页面、文稿或企业身份变化后释放。导出页面分别呈现文件内容、渠道和版面检查状态；`NOT_RUN`保持尚未检查。

页面写入采用本次原session与generation；响应到达后重新核对当前会话、页面和可见性，后续排队格式在每次执行前再次核对。原页面变化后停止后续自动执行，已受理操作由当前导出历史重新读取。下载读取使用AbortSignal，旧响应不能进入当前文稿。

`document/src/medical_document/export.py`的ExportRenderer接收固定ExportInput、完整DocumentBody字节、格式模板及图片。模板、正文、图片、字体和转换程序具有独立摘要；完整节点归属、表格覆盖、格式模板外部资源及字体字符覆盖具有准入检查。DOCX保存稳定节点书签并重新读取全文、表格、公式和图片；PDF读取原DOCX，通过独立LibreOffice profile、固定字体目录及Fontconfig配置转换，再使用pypdf重新读取完整文字。Markdown仅允许INTERNAL_EXCHANGE，使用完整HTML结构保留合并区域、MathML及原LaTeX、固定图片；内部字段与证据投影单独保存。

`tests/test_document_export.py`与`scripts/check_document_export.py`验证三个实际格式、固定精度、文字格式、合并表格、原生OMML公式、图片、SIMULATION标记、完整字段/证据/事实投影、输入变化拒绝和重复写入拒绝。当前代码通过正常依赖安装后，四项实际文件检查通过；回执为`.implementation-work/IC-26-EXPORT-20261009-INSTALLED-FULL-PROJECTIONS-01/receipt.json`，SHA-256为07e7d46df2e427668a94b5eae3c99767f163cd1164d36d8e1ad5d8b75ec5bae2，exit_code=0、inputs_unchanged=true。全部四份安装模块与源码字节相同；实际格式、输入制品及重新读取文件的摘要保存在同目录export-result.json。执行命令为`.implementation-work/document-venv/bin/python scripts/check_document_export.py --execution <新的执行编号> --installed`。

PDF实际字体逐项匹配固定字体文件清单，转换检查同时绑定完整ExportInput。字段、EvidenceLink、FactUsage及节点引用检查共同验证企业和项目归属。原生解析模块源码保持一致，已有十二项七格式检查由`.implementation-work/IC-08-NATIVE-20261009-EXPORT-PACKAGE-REGRESSION-01`维护。

S04已经增加任务成果目录及`GET /api/artifacts`读取接口。接口按项目、成果类型和固定游标读取成果，并对每项成果复查当前`artifact.read`；页面显示成果编号、运行编号、文稿版本、对象摘要、媒体类型和建立时间。`POST /api/artifacts/{id}/source-submissions`接收目标归属、类别、标题、完整原始证据、权利范围、维护人、适用项目和审核人，复查来源读取、目标写入及共享许可后建立唯一待核验资料。来源边、引用保护、共享决定、待审权利和操作回执在同一事务保存；同一成果与目标范围的重复请求返回已有资料身份，原成果保持原状态。S04页面提供提交表单和新资料版本入口。

EX-01—04已经提供创建、状态读取、固定文件下载和失败格式重试。创建固定文稿版本、审查摘要、模板、格式配置、字体清单、转换程序及渠道版本；正式正文要求当前确认与同版签发。每个格式使用独立尝试身份，执行过程写入RUNNING、VERIFYING、SUCCEEDED或FAILED，成功后登记文件制品、核验制品、来源关系及对象版本。下载同时复查文稿与两个制品的当前权限，Range读取继续验证原对象摘要。页面读取服务端导出依据和当前版本导出记录，刷新页面后可以继续查看排队项、失败项及已完成文件；提交后执行各格式，显示核验结果，支持状态刷新、排队项继续执行、失败项新尝试及原文件下载。

`document/src/medical_document/export_command.py`连接公开执行服务与ExportRenderer。命令读取固定正文、DOCX格式文件、字体及图片制品，DOCX和PDF重新读取核验，Markdown保留内部投影；输出文件与核验JSON分别保存为制品。Java映射及封闭Schema检查、完整Java构建和前端严格类型检查通过。实际三格式核验回执为`.implementation-work/IC-26-EXPORT-20261011-PUBLIC-EXECUTION-01/receipt.json`，SHA-256为`40501bd07fce41a6653bda303b60fbe2ba3f8ea2759d7343e52cfa757a48cb5c`，exit_code=0、inputs_unchanged=true。

## 剩余开发

目录页码、附件顺序、受理渠道核准、视觉版面检查、签发创建入口和服务重启后的自动续办仍需完成。成果转存客户权限、真实来源图及并发提交仍需业务验收。全部代码及必要集成通过后登记IMPLEMENTED，客户验收独立登记。last_verified=2026-10-11。
