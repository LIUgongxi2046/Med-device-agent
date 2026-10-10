# IMP-16 同版正文、候选比较及连续编辑

唯一任务状态由实施任务计划§4.2维护，IMP-16保持IN_PROGRESS。需求依据为BIZ-07、AC-22/38、IC-16、HLD ADR-005和FRONT-I02/I03/I14。last_verified=2026-10-09，源码为未提交工作目录。全部开发输入采用明确标记的SIMULATION资料，期限为2026-10-10上午09:00（Asia/Shanghai）；正式客户验收独立登记。

## 正文读取与保存

Given固定原始DocumentBody、完整目录及字段定义、来源权利、依赖授权与明确容量；When通过BX-03、KN-02、BX-04和DC-02提交修订；Then实际读取完整对象，校验规范字节、全文摘要、全部节点与单元格归属、字段原值、证据定位和专业模板依据。当前Cookie、CSRF、企业上下文和两项授权水位在传输及保存边界重新核验。版本冲突、关系冲突、非法Unicode、容量超限或许可失效立即拒绝。

DocumentUploadController及DocumentUploadService提供当前本人文稿的受限许可和完整制品登记；DocumentUploadPartService复用实际对象服务、共享名额、原分段请求及恢复身份。DocumentBodySemantics校验正文结构；DocumentBodyDomainAuthority核查模板、字段定义、章节、来源、定位、事实和图片身份。完整配置未提供时立即拒绝，客户端不能指定对象地址、替换服务器读取要求或扩大依赖范围。

DocumentRevisionService保存不可变原操作和固定输入，通过实际S3创建独立DOCUMENT_FIELDS及DOCUMENT_EVIDENCE对象。正文、稳定节点及字段定义身份、完整原值、证据关系、事实位置、全部章节、水位、审查失效、头版本CAS、审计和Outbox在当前企业事务保存。原请求重放返回原响应；相同请求键配合不同内容返回IDEMPOTENCY_CONFLICT，旧头版本返回REVISION_CONFLICT。

DocumentReadService提供DC-01同版快照、BX-02完整分页及当前编辑依据查询。每份独立制品必须具有匹配的固定对象、摘要、Schema和当前完整权限。字段与证据投影必须逐项等于同版正文；页容量和授权游标由明确配置限定。正文完整重读通过后才能确认保存。RUN游标由IMP-27实际生产者提供。

完整公开HTTP证据为`.implementation-work/IC-12-PLAN-WRITE-20261009-DOCUMENT-HISTORY-01/receipt.json`，SHA256为9833e5d228e31a34d47c6aed7527bf9c4bcff134227ac779268f5729f5f4deed，exit_code=0、inputs_unchanged=true；DocumentRevisionHttpTest完整检查239.5秒通过。覆盖实际MySQL、Redis、S3的许可重放、分片、制品登记、修订重放、快照与四页全文重读、字段定义身份、证据、章节、检查事件、请求键冲突、版本冲突和撤权拒绝。新增固定历史读取验证原正文、字段和证据制品保持、历史编辑动作为空、非法查询拒绝以及撤权后的历史读取拒绝。原JUnit位于该execution的ruoyi-admin目录，业务结果位于`.implementation-work/document-revision-http-result.json`。输入包含SIMULATION模板、来源、权利及审批记录，formal_customer_acceptance=false。

## 编辑页面与完整内容

Given同版完整正文及当前document.edit动作；When修改目录、段落、文本格式、图片说明、LaTeX公式、表格或字段；Then使用正式Vue、ElementPlus和VueQuill组件保存稳定业务身份。DocumentNodeEditor按实际父子关系和单元格归属显示完整内容；文本格式使用Quill成熟组件，不创建编辑器内核。

document-tables.ts提供完整矩形覆盖检查、稳定行列及单元格身份、新增行列、移除空行列、矩形合并、分离和新增单元格文本。跨越选择矩形、重叠、缺口或移除含正文的行列立即拒绝。合并和分离保留原单元格及文本身份；格式变化增加文本节点时同步更新所属单元格引用。公式修改保留LaTeX原文，已变化的公式清除不再对应的旧公式图片引用。图片复用固定已有版本，替代文字与说明独立编辑。

document-field-editor.vue保留原值、十进制字符串、单位、型号和条件；尚未完成的输入留在当前受控状态，明确确认后才进入正文。字段定义、节点及证据身份继续采用同版原记录。document-session-drafts.ts保留当前主体、企业、文稿的草稿、原保存操作和未确认字段；恢复前实际重读当前权限。上下文或授权失效清除受限内容，页面离开后的旧输入不能重新进入新上下文。

document-comparison.ts提供原版本、服务器版本和本地编辑的完整节点、字段、证据及事实关系比较。表格身份、顺序、每个单元格及跨度、图片版本、公式图片版本、完整来源定位均显示；双方修改同一对象时必须选择采用内容。确认比较前保持原保存依据，选择完成后校验完整DocumentBody；服务器独立修改与用户选定修改共同保留。

PendingDocumentSave按公开接口顺序处理完整原字节、原许可、分段、完成和修订身份。保存期间继续编辑属于后续generation，原保存成功只确认对应capture。处理中返回原Operation并保留输入，明确恢复动作进行一次查询；网络错误直接传播。正文与请求内容仅保存在受控内存。DocumentEditBuffer使用当前VueUse14.1.0的useManualRefHistory管理明确容量100次的不可变正文历史，撤销和重做保留原请求capture及字段精度；保存后撤销内容通过新修订表达。比较后历史从当前服务器依据重新建立，上下文失效清除全部历史。页面通过标准beforeunload保护未保存内容。

`npm run typecheck:medical`通过全部医疗页面严格类型检查。`node scripts/check_document_frontend.mjs`通过完整编码、UTF8/摘要、不可变原字节、保存期间继续编辑、失效清理、字段精度、三方明确选择、表格合并/分离与文本归属检查。模拟输出明确data_kind=SIMULATION；工程文档同时验证完整字符保留。

当前前端回执为`.implementation-work/IC-12-PLAN-FRONT-20261009-DOCUMENT-RICH-HISTORY-01/receipt.json`，SHA256为089a300c645f295f4369d68db1161360a174cc92c731c70b0fec2fac49637cfa，exit_code=0、inputs_unchanged=true。全部医疗页面strict类型、原生Vite转换、共享Schema加载、完整正文状态及保存期间撤销/重做检查通过。固定历史阅读使用DC-01-HISTORY和同版BX-02，页面异步结果必须具有当前generation；该证据未包含DOM、视觉或部署检查。

浏览器实际生成的完整表格正文经Java正式DocumentBodySemantics核查通过，证据为`.implementation-work/IC-16-DOCUMENT-STRUCTURE-20261009-TABLE-EDITOR-01/receipt.json`，SHA256为f587c14f4d148c5df8099119473294d31ba70da58a505a6ea82e23b2cdefd057，exit_code=0、inputs_unchanged=true。检查确认双方规范字节摘要一致，节点/字段身份、完整单元格覆盖、正文归属和12.500原精度保持。实际生成内容位于`.implementation-work/document-frontend-structure.json`；该证据不表示页面DOM或正式客户验收通过。

## 关联影响与剩余开发

共享VersionResourceAuthority使用一次完整36类领域查询核验全部传递版本和实际实体归属；授权与跨域冲突判断保持全部范围。十九项当前实际MySQL查询、完整上传授权、依赖授权及制品读取检查通过，证据为`.implementation-work/IC-05-UPLOAD-AUTHORIZATION-20261009-DOCUMENT-BATCH-VERSION-02/receipt.json`，SHA256为ec06f078a6b17f50c856b24c222018debb01715c33bc154bef5ef74179805f8a，inputs_unchanged=true。

数据依赖037—039迁移、字段定义外键和同版证据投影由IMP-03维护；当前实际254项迁移、163张企业业务表、200张物理表。任务与完整计划由IMP-12维护；模型运行、专业Activity、当前正文检查、审查签发及导出分别由IMP-07/13—15/23/24/26维护。

剩余开发包括受限图片内容及公式呈现、全文选区、BX-05/07/08的服务器候选及历史比较、历史目录、明确部分候选采用、页面DOM交互与统一部署验证。源数据许可、模板/字段归属、事实版本与原文定位始终由当前领域消费者核验。完整IC-16保持未完成，全部已定义功能继续开发。
