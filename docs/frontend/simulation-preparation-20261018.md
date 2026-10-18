# 模拟准备前端

新增 `simulation-preparation.vue`、`simulation-fixtures.ts`、`simulation-fixture-domain.ts`。路由、导航与 frozen schema 注册由集成方接入；未宣称部署或端到端已通过。

流程：真实模拟配置目录及固定回读 → 已核准来源目录及两份固定来源详情 → 五字段封闭参数 → 用户明确登记 fixture → 再确认登记输入 ARTIFACT。

- fixture 回执的 case/input SHA 是 canonical 内容摘要；063 的 input_ref 为原对象 raw SHA。
- 原来源、rights、case/config、raw SHA 都与原请求校验；目录可见与 DECLARED_ONLY 不是许可。
- fixture 无 lookup/key 协议：保留完整业务绑定，当前原上下文内显式重放。界面上的恢复标识仅是本地标识，不作为服务器 Idempotency-Key。
- input 注册使用独立原 key；lookup POST `/api/simulation-fixtures/{caseId}/input-artifact-lookups` 携带 original_key/original_request，可跨当前上下文显式查询。NOT_FOUND 不自动重发。
- 未知结果保留 sessionStorage 中同 actor/tenant 的完整原参数和摘要；用户切换及隐藏清理屏幕，复现时验证记录完整性。不存原件正文、令牌或权限证明。
- 输入错误局部提示；服务器和回执完整性错误仍走现有 fail-closed。
- 参数改动撤销确认，写锁仅由拥有者在请求结束时释放，卸载/隐藏后迟到结果不安装到页面。

定向测试：`node --test tests/frontend/simulation-preparation-checks.mjs`。编译真实 SFC setup，使用真实 Vue reactivity、封闭 schema 与 domain，mock 网络边界。覆盖局部无副作用错误、两步显式写、未知请求保留、跨上下文 lookup、不自动重放、卸载迟到、raw/canonical 混用拒绝、恢复摘要篡改。

人工验收需真实已上传核准的 UTF-8 TXT JSON（SimulationCase / ExactInput）和模型绑定，以及当前 SIMULATION_EVALUATION 权利 READ/ENTERPRISE_USE、source.read、model.configure 与 artifact.register。此页不生成 fixture 内容或默认许可、不自动运行评测。
