# IMP-16 同版正文、候选比较及连续编辑

唯一任务状态由实施任务计划§4.2维护，IMP-16保持IN_PROGRESS。需求依据为BIZ-07、AC-22/38、IC-16、HLD ADR-005和FRONT-I02/I03/I14。源码为当前未提交工作目录。2026-10-13用户明确授权全部使用SIMULATION资料和业务输入，正式客户验收独立登记。

## FE-16 全文目录、历史目录及正文选区

2026-10-14 EXACT-CURRENT-01重新核查当前组件及共享Schema，开发六项和生产六项全部通过，exit_code=0、inputs_unchanged=true；8901项输入及66项产物。回执`.implementation-work/IC-FRONT-CONTINUE-DOCUMENT-SELECTION-20261014-EXACT-CURRENT-01/receipt.json`的SHA-256为`096aaa5e141b3978cd9a34187ac7e9f35699a2f5c2ab9cf3a267558e4f1b3b96`。检查覆盖全文与固定历史目录、完整文字标记、Unicode范围、图片和合并表格身份及上下文清理，输入标记SIMULATION、视觉NOT_RUN；当前专业消费者及完整业务继续原要求。

`document-body-reader.vue`已经接入当前正文和固定历史正文。完整父子关系和ordinal决定阅读及目录顺序，稳定节点负责导航和焦点；不存在的父节点、重复身份、重复顺序或不可访问目录立即拒绝。只读内容复用`document-node-editor.vue`，保留原文、公式、表格跨度和图片固定身份。文字按原BOLD、ITALIC、UNDERLINE、SUPERSCRIPT及SUBSCRIPT标记完整呈现，同行文字节点保持连续；节点文字和标记均保留在原规范内容中，选区继续按原Unicode位置和完整Node摘要计算。

`document-selection.ts`将原生DOM Range和键盘输入转换为正式Selection。文字逐节点保留UTF-8原文及完整Node的RFC8785摘要，UTF-16严格转换为Unicode code point，代理对中间位置拒绝。组合字符及换行保持原值，跨节点选择按实际正文顺序形成独立segment。图片使用同版artifact_id和sha256；表格采用成熟Element Plus表格及当前tablePositions，完整保留合并单元格覆盖的行、列及单元格身份。当前Java的DocumentRevisionService保存完整Node规范摘要，表格选区沿用同一摘要口径；后续专业消费者仍需实际联调核验。

原文和Selection只保存在页面内存。正文、版本、编辑状态和身份变化清除选区、键盘输入及单元格选择，异步摘要结果需要原generation和正文版本仍有效。未保存正文明确标记，只供提问与修订建议；保存以后重新选择才能进入核验或审查。组件已经提供selected事件，Gru和专业审查消费者需要对应公开接口及完整请求定义，目前不自行提交专业操作。

Given已授权SIMULATION正文、五种文字标记、emoji、组合字符、换行和合并表格；When从原文或键盘选择范围；Then文字标记和位置、原摘录摘要、固定图片、完整表格身份准确，非法Unicode和重复节点拒绝，版本及上下文变化清除全部选择。空文字节点通过完整DocumentBody校验，没有可选择的文字，跨节点Range仍保留两侧完整原文。入口为`tests/browser/document-selection-checks.js`和`web/tests/document-selection/`；严格类型检查通过，开发及生产浏览器完整检查与当前回执由`scripts/check_frontend_continuation.py --suite document-selection --execution`维护。有效Red保存于`.implementation-work/document-selection-red.log`、`.implementation-work/document-selection-empty-valid-red.log`及`.implementation-work/document-selection-marks-red.log`，分别覆盖完整目录、合法空文字节点和完整文字标记的必要断言。当前范围不包含图片像素读取、Gru业务提问、批注写入或正式签发。

当前GREEN-05的开发六项、生产六项实际Chrome断言全部通过，exit_code=0、inputs_unchanged=true；8842项输入及66项产物绑定当前组件、共享Schema和依赖。回执为`.implementation-work/IC-FRONT-CONTINUE-DOCUMENT-SELECTION-20261013-GREEN-05/receipt.json`，SHA-256为`5d399095a944a1bcf183e3ced81557387690ff15e23f26bda51cbcc0a28ffa84`，原输出位于同目录`command/output.log`。五种文字标记、跨节点选区和全文摘要共同通过，视觉保持NOT_RUN，组件输入标记SIMULATION，没有HTTP业务写入；完整IC-16仍需专业消费者和业务集成。

## FE-16 公式与固定原文面板

`document-node-editor.vue`使用KaTeX呈现公式，保留LaTeX原文。编辑人员明确点击预览后呈现当前输入；只读正文呈现同版公式。配置禁止受信任扩展，未知语法立即报告，单次渲染使用独立宏定义。公式输入变化清理原预览。

`document-evidence-panel.vue`按当前正文EvidenceLink打开固定原文。读取采用原source_version、parse_version、chunk_id和Locator摘要，重新核对来源身份、引用摘要与完整Locator；原文面板显示原始文字、固定版本及完整原件入口。关系标签保留支持、冲突和背景原含义。无法取得对应位置立即报告，身份、正文版本、证据关系或页面变化后清理原读取。完整原件入口由[IMP-08](IMP-08.md)维护。

当前组件及接口消费已经通过严格类型检查。公式在实际Chrome开发和生产页面分别通过原LaTeX、数值原文、输入变化清理、受限链接及禁止外部请求断言。证据为`.implementation-work/FRONT-SOURCE-READER-20261013/browser-checks.json`和`formula-production.log`，与七格式阅读共22项通过。固定证据公开接口读取及专业关系确认继续纳入组合业务验收，完整IMP-16保持`IN_PROGRESS`。

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

剩余开发包括受限图片内容、选区的Gru和专业审查接口消费、BX-05/07/08的服务器候选及历史比较、明确部分候选采用、公式及证据面板的实际业务验证、组合页面DOM交互与统一部署验证。源数据许可、模板/字段归属、事实版本与原文定位始终由当前领域消费者核验。完整IC-16保持未完成，全部已定义功能继续开发。
