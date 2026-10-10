# IMP-26 固定成品导出与成果转存

唯一任务来源为[实施任务计划](../../实施任务计划.md)的IMP-26。依据为PRD§8.5.3/8.5.8/8.9.1、LLD-后端EX/KN-18与§5.5、LLD-数据§6.4以及Agent N08/V09。当前开发使用SIMULATION业务输入；生成、解析和转换采用实际库及LibreOffice。

本次检索既有medical-document、DocumentBody、正文结构检查、公开成品接口及ExportManifest设计。C09沿用medical-document，使用python-docx保存DOCX、成熟公式转换库生成OMML、LibreOffice转换PDF及pypdf重新读取。所有输入固定完整正文、模板、字体、转换器、用途和版本；未知条件立即拒绝。

导出包含完整章节、原始文字及数值、文字格式、合并表格、公式、固定图片和说明。FORMAL_BODY保持正式正文；内部证据和字段投影单独保存。独立格式检查及不可覆盖的尝试目录保留原成功文件。受理渠道保持PENDING_REVIEW，视觉版面尚未验证。

## 当前代码与验证

`document/src/medical_document/export.py`的ExportRenderer接收固定ExportInput、完整DocumentBody字节、格式模板及图片。模板、正文、图片、字体和转换程序具有独立摘要；完整节点归属、表格覆盖、格式模板外部资源及字体字符覆盖具有准入检查。DOCX保存稳定节点书签并重新读取全文、表格、公式和图片；PDF读取原DOCX，通过独立LibreOffice profile、固定字体目录及Fontconfig配置转换，再使用pypdf重新读取完整文字。Markdown仅允许INTERNAL_EXCHANGE，使用完整HTML结构保留合并区域、MathML及原LaTeX、固定图片；内部字段与证据投影单独保存。

`tests/test_document_export.py`与`scripts/check_document_export.py`验证三个实际格式、固定精度、文字格式、合并表格、原生OMML公式、图片、SIMULATION标记、完整字段/证据/事实投影、输入变化拒绝和重复写入拒绝。当前代码通过正常依赖安装后，四项实际文件检查通过；回执为`.implementation-work/IC-26-EXPORT-20261009-INSTALLED-FULL-PROJECTIONS-01/receipt.json`，SHA-256为07e7d46df2e427668a94b5eae3c99767f163cd1164d36d8e1ad5d8b75ec5bae2，exit_code=0、inputs_unchanged=true。全部四份安装模块与源码字节相同；实际格式、输入制品及重新读取文件的摘要保存在同目录export-result.json。执行命令为`.implementation-work/document-venv/bin/python scripts/check_document_export.py --execution <新的执行编号> --installed`。

PDF实际字体逐项匹配固定字体文件清单，转换检查同时绑定完整ExportInput。字段、EvidenceLink、FactUsage及节点引用检查共同验证企业和项目归属。原生解析模块源码保持一致，已有十二项七格式检查由`.implementation-work/IC-08-NATIVE-20261009-EXPORT-PACKAGE-REGRESSION-01`维护。

## 剩余开发

EX-01—04、授权下载、导出持久状态、页面、签发同版检查、目录页码和成果转存继续完整要求。转换服务的取消、全部格式规则与受理渠道检查、完整发布制品和输入权限接入尚未完成；当前检查不代表公开导出已经可用。全部代码及必要集成通过后登记IMPLEMENTED，客户验收独立登记。last_verified=2026-10-09。
