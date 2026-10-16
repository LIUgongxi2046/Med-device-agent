# IMP-39 非功能、兼容与恢复验证

状态：IN_PROGRESS。更新日期：2026-10-13。依据为前端实施计划FE-39、NFR-01—17、LLD-前端§5.2/6及用户本轮SIMULATION授权。完整任务状态由实施任务计划§4.2维护。

## 七格式阅读检查

Given固定DOCX、PDF、TXT、Markdown及明确标记SIMULATION的CSV、XLSX、PPTX；When在实际Chrome的开发与生产页面中选择原件、阅读内容并清理；Then完整原件摘要一致，Office内容进入独立sandbox iframe，CSV换行及XLSX公式原文保持，PPTX文字和表格可读取，清理后内容节点全部移除。

代码为`tests/browser/source-reader-checks.js`、`web/tests/source-reader/`、`source-file-preview.vue`和`content-rendering.ts`。`node tests/browser/source-reader-checks.js`实际通过开发11项、生产11项，其中每种环境包含10份原件及一组公式检查，记录在`.implementation-work/FRONT-SOURCE-READER-20261013/browser-checks.json`及`formula-production.log`。公式检查保留LaTeX和12.500原文，输入变化后清理原预览，受限命令无法生成可执行链接。全部检查没有浏览器异常或外部HTTP请求。`node scripts/check_source_reader_frontend.mjs`通过10份固定原件的字节汇集、完整摘要、重复范围、内容变化、不完整读取和清理拒绝检查，记录在同目录`byte-checks.json`及`seven-format-bytes.log`。

七份D0原件沿用登记的`LOCAL_PARSER_CHECK`范围；三份模拟文件来自`.implementation-work/document-tests/simulation-tasks.csv`、`.xlsx`、`.pptx`，原生成规则为`tests/test_document_native.py`。每次读取核验当前文件摘要，检查结果保留输入种类，未将模拟资料登记为客户正式资料。

## 当前完成范围与后续条件

原请求元数据的实际开发及生产浏览器检查由[IMP-05](IMP-05.md)维护。当前实际会话输入清理通过开发四项、生产四项，生产HTML、Script和CSS字节与构建文件一致；原始输出为`.implementation-work/FRONT-20261013-CURRENT/source-context-verified.log`，结果为`.implementation-work/FRONT-SOURCE-CONTEXT-20261013/checks.json`，对应代码及完整身份条件由[IMP-04](IMP-04.md)维护。

本地七格式阅读及内容清理已经验证。PDF原件Blob完整摘要已经验证，PDF原生阅读器的版面检查为`NOT_RUN`；所有视觉检查保持`NOT_RUN`。其他浏览器、键盘焦点、中文输入法、批准负载、SLO、完整事件恢复、并发保存、真实导出版面及RTO/RPO继续保留原验收条件。上传处理公开接口、专业资料采用和正式客户验收分别读取[IMP-08](IMP-08.md)、[IMP-16](IMP-16.md)、[IMP-26](IMP-26.md)及[IMP-37](IMP-37.md)。
