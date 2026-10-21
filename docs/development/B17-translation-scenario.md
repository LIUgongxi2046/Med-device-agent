# B17 固定翻译场景

POST /api/translations/specifications 与 GET /api/translations/tasks/{taskId}/runs/{runId}/results/{actionId} 为真实作者/结果入口。schema_version、scenario、selection 结构与 B16 相同；完整八字段和真实验收样本证据仍走 B14 专业审核、发布及任务准入。selection 固定 source_language/target_language (BCP47)、terminology(source/target)、semantic_review_instruction、真实 semantic_review_rule(version_id/content_sha256)、layout_policy=ORDERED_TEXT_BLOCKS_EXACT_LINE_BREAKS、按顺序 segments。每段固定 key/section_id/source/rights_revision/evidence/literal/protected_tokens。完整原文 locator 必须和 literal 的 UTF-8 hash 完全一致。

N04 收到真实固定规格以及额外来源的 raw/parsed GET、hash 和 locator 原文，生成的译文正常保存为候选正文。N05 的独立 session 收到同一固定规格与另一次真实原件实读，不接受 N04 自报核对完成。每章逐段映射实际保存 TEXT 节点，核对段数/顺序、语言方向、十进制数字 token 顺序及精度、固定术语次数、受保护引用/标识符次数、换行、空行和缩进结构、正文 EvidenceLink。这里只支持有序文本块保真，不冒充复杂表格/图片/页面排版无损翻译；非文本布局明确 UNKNOWN。

语义/目标语言不是正则检测冒充完成：每个完整段落必须有真实候选 ClaimRecord 和独立 N05 SUPPORTED 判断，固定核准翻译审查 RuleVersion 必须精确匹配 hash、包含全部源依据且逐目标节点有独立 PASS。批准的 semantic_review_instruction 和语言/术语要求作为真实 typed context 交给 N05；规则未获准、没有全文支持、语言/语义不通过、引用/数字/版式改变均 blocked。机器审核始终不能替代 B08/B09 专业人工译文确认或 B10 签发。

typed result 保存于 REVIEW_FINDINGS 与 REVIEW_COVERAGE 的真实不可变 receipt provenance。submitSaved 使用实际已保存 findings 再跑确定性保真与独立语义结果校验，两个 provenance 都必须相等；blocked 令 coverage_complete=false 并经既有 N06/审查链进入 WAITING_INPUT。结果读验证原有完整权限/审计/outbox/hash/dependency closure，标记 historical_machine_result、人审必需和非法律批准。

复用既有 148/114，无需 155 migration；没有造客户资料、批准、provider receipt 或 seed。两个新增测试文件仅延期源码，未运行测试/构建/编译 (无 javac 或 Java parser)/服务/provider/网络/迁移/push。真实获准翻译规范、术语与客户样本仍须业务方正常提供，业务验收 NOT_RUN。待统一验证跨租户、源撤权、source literal 变更、语言相同、丢术语/数字/引用、节点重排、独立模型失败、N04/N05 同 session、review rule 未批准/无源依据、provenance 篡改、重复提交和完整人工审查。
