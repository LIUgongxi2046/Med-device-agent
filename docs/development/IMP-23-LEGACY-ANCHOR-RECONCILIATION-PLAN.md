# 升级前旧位置批注：显式兼容核对

状态：独立后端/前端代码及离线组合回归已完成，真实运行验收未执行；实现边界详见 IMP-23-LEGACY-ANCHOR-RECONCILIATION-IMPLEMENTATION.md。

## 场景

059只回填真实原始定位。升级前已出现“批注OPEN/RESOLVED仍属于V1、当前正文已是V2”的数据时，没有真实INVALIDATE记录，不能假造早期作者/时间。当前标准重新定位入口要求REANCHOR_REQUIRED，本轮通过显式当前操作者核对入口处理该场景，不修改旧事实。

## 修复路径

新增独立POST /api/annotation-threads/{id}/anchor-reconciliations，使用自己的Schema/manifest/幂等操作，不改变既有锚点Schema摘要或059历史文件。界面明确“确认标记为待重新定位”，显示原始实际状态，不提前冒充已失效状态。

仅接受当前正文、批注、文档头、pointer修订、原锚点ID、原因及人工确认；只允许origin-only、旧有效版本且OPEN/RESOLVED、ACTIVE项目/当前未签署正文。使用真实review.decide和原始/有效/目标来源证明、完整生命周期校验。

单事务追加当前时刻的INVALIDATE，明确原因为本次旧位置核对，记录当前操作者和数据库时间，不追认历史操作。批注/文档头/问题水位各推进一次，证据水位和锚点pointer不变，相关确认失效，回执/审计/Outbox幂等保存。之后刷新完整快照、列表、历史，再使用既有人工选择新位置并重新OPEN的流程。

完成后的重试先返回原回执，并重新验证当前权限/来源和原证据；不重新应用fresh eligibility，不重复增加水位，不更换操作身份。

## 必测

旧OPEN及RESOLVED均可核对后重新定位为OPEN；原引用/作者/时间/Selection/制品和058决定不变；当前真实操作者/时间；后续回复/决定/再次定位后的原回执；各CAS、撤权、来源撤销、时间失效、事务回滚；signed/inactive/current-position/already-reconciled/损坏历史拒绝；前端刷新、恢复、取消及写锁。

不宣称真实数据库/对象服务/HTTP/浏览器验证。此文档保留设计与验收要求；执行结果以实现记录及验证日志为准。
