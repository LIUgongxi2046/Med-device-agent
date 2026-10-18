# IMP-23 / RV-06 人工重新定位实现边界

状态：2026-10-18 正常新生命周期代码已接入，147项定向Java离线测试通过；未宣称真实数据库/S3/HTTP/浏览器验收完成。

## 原需求与确认

- 实施任务计划 IMP-23：真实已保存正文版本、过期选区拒绝、显式人工重新定位及历史保留。
- LLD-前端 S11：只有人工确认新位置后退出 REANCHOR_REQUIRED，历史批注和引用原文保留。
- LLD-后端 RV-06：原因、定位证据、版本及人工确认，不自动匹配相似文字。
- PRD AC-11：修改形成新版本，原版本及意见可查。
- 用户于2026-10-18明确确认：人工重新定位后统一 OPEN（待处理），保留历史；不是恢复原来的已解决状态。

## 持久化及接口

059 新增不可变 annotation_anchor、独立 annotation_effective_anchor 指针及不可变 annotation_transition。原 annotation 的正文版本、节点、Selection、Selection制品、引用摘要不改；058决定外键及原决定不重写。升级只复制原始锚点事实，不伪造历史动作。

- POST /api/annotation-threads/{id}/reanchors：真正人工确认、原因、目标 Selection、标注/文稿头双CAS、旧锚点ID及指针CAS。
- GET /api/documents/{id}/annotation-anchors?document_revision=…：原批注及有效位置分别投影；新正文能看到待定位意见。
- GET /api/annotation-threads/{id}/anchors：全部不可变锚点和连续 lifecycle，包括回复、决定、正文失效、重新定位。
- 原 annotation-threads 列表继续提供原版本内容；原共享契约未改。新增协议独立 schema/OpenAPI。

正文保存事务逐条追加 INVALIDATE，标注修订增加1且状态变成 REANCHOR_REQUIRED（重复保存也有独立事件），新正文问题水位增加相应数量。人工 REANCHOR 后 OPEN，标注、指针、文稿头及问题水位推进；证据水位保持，相关全篇确认失效。

## 证明与并发

原始、旧有效、新目标及重放涉及的历史锚点分别通过固定正文和真实Selection对象读取链；核验实际字节、哈希、登记、来源/依赖和同文档所有权。每个写阶段及最终事务重查上下文、许可、来源证明、时间边界、正文头、旧锚点、标注及回复并发。生产路径使用真实对象写入/回读；测试中的存储替身不冒充S3验收。

独立 RV-06-REANCHOR-1 操作先保存不可变写意图，再进行对象I/O和原子提交。已完成重放保存原 OPEN 状态/时间/版本/锚点，后续回复、解决或再次定位不覆盖回执，不重复推进水位。

决策历史完整校验从 OPEN@1 起的每个修订，不能通过放松“最后状态”检查容忍缺事件。独立锚点链与 REANCHOR/INVALIDATE 事件也必须一致。

## 有界完整读取

文档最多100批注（创建前文档级预留）；线程最多100锚点；每个新锚点预留64KiB，原批注也有64KiB上限；生命周期最多10000事件，成功写入前检查。完整新协议响应上限16MiB，拒绝超限而不截断。原版列表上限仍为8MiB。

## 明确未覆盖的升级状态

在059之前已经保存过新正文、但旧批注仍为 OPEN/RESOLVED 的遗留记录不会被迁移伪造为曾经 INVALIDATE。本轮独立显式核对入口保留原历史，由当前有权限的操作者确认并记录当前时间的真实失效事件，再进入人工重新定位。见 IMP-23-LEGACY-ANCHOR-RECONCILIATION-IMPLEMENTATION.md；真实运行验收仍未执行。

## 验收边界

2026-10-18定向Java测试共147项通过，0失败/错误/跳过：Annotation*Test、DocumentSelectionSemanticsTest、DocumentRevisionAnnotationTailTest。涵盖真实Schema/Selection语义、完整状态链、历史来源撤销、多次定位后的原回执重放、双CAS、正文保存实际事务尾部及回滚替身；事务尾部测试不是完整上传到保存的外部集成验收。测试日志为 .implementation-work/reanchor-final-focused.log，逐项结果见 medical-persistence/target/surefire-reports。真实迁移和数据库事务、S3、HTTP、浏览器仍未验收。确认、签发、问题处理、完整IMP-23和全项目完成不在本切片完成声明内。
