# IMP-23 人工重新定位：回复与处理历史集成

2026-10-18：人工重新定位后统一进入 OPEN；原始 annotation 定位及 058 annotation_decision 外键保持不变。

## 读写语义

- 回复与处理接口的父批注投影使用当前有效锚点的真实固定正文版本和摘要。新的处理决定对有效正文执行头版本、问题水位及全篇确认失效操作，但 annotation_decision.document_revision 仍写不可变原始版本，保留 058 的外键及旧记录。
- 处理前分别读取并验证有效位置与原始位置的真实正文及 Selection 制品。最终短事务重检双方正文证明、批注、权限与授权时间边界。
- 已完成处理操作重放时，从不可改回执取得历史正文与批注修订，读取该修订当时有效的历史锚点及真实 Selection；最终事务再核验历史锚点及来源。后续重新定位、回复、解决不会改变原回执。
- 回复保留既有来源感知 snapshotFixed 路径，分别核验请求历史版本、有效版本及不可变原始版本；最终事务重检头版本、原始/有效定位及权限时间边界。

## 完整历史

旧 Reply/Decision 请求和响应契约不变。DecisionService.lifecycle 为独立版本的锚点历史端点提供安全的状态转换序列：kind、previous_revision、revision、previous_status、status。不暴露回复正文、决定理由或审计内容。

历史由不可变决定、annotation_transition 的 INVALIDATE/REANCHOR，以及回复 outbox 的修订见证合并。验证从 OPEN@1 开始每个修订严格连续，直到当前父批注的修订和状态。缺失、重复、错序、错误状态或跨文档转换均失败关闭；不能用放宽末状态检查掩盖缺失历史。连续正文保存可显式记录 REANCHOR_REQUIRED → REANCHOR_REQUIRED。

## 验证范围

新增离线服务回归覆盖新定位下决定/回复、原始决定外键、原始回执重放、完整链中的回复见证、缺失/重复事件。Mapper 测试覆盖有效锚点联接及历史读取的受租户保护 SQL。测试执行结果由主任务统一记录；本说明不声称 MySQL、S3、HTTP 或浏览器真实验收完成。

容量约束：新的回复/决定在父修订达到 10001 时拒绝，已完成操作重放仍优先返回原回执。安全生命周期行最长 165 个 ASCII 字节（最长状态/类型和两个 20 位修订），10,000 行连同数组分隔符的保守上界为 1,660,001 字节；边界测试对完整数组进行实际序列化。锚点历史端点必须在总响应预算中预留这部分，另行约束锚点及转换元数据，不能仅依赖事件数量。

2026-10-18 离线验证更新：统一 Maven 执行产生的 Surefire 报告显示，本集成负责的 45 项测试全部通过（AnnotationDecisionServiceTest 20、AnnotationDecisionMapperTest 3、AnnotationReplyServiceTest 19、AnnotationReplyMapperTest 3）。历史 SQL 的保留字别名已修正，Mapper 测试在失败路径也会释放事务上下文；生命周期额外要求回复见证数等于实际 annotation_reply 数，拒绝父修订未变的孤立回复。该结果不是完整应用或真实数据库验收结论。
