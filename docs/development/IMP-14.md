# IMP-14 Harness profile、工具与内部适配

## 任务身份与依据

唯一任务来源为[实施任务计划](../../实施任务计划.md)的IMP-14。依据为LLD-Agent§1—3/4.2/4.3/5/6、AG-T01—03、AG-I04/05及IA/IN接口；完整工具范围AT-01—10保持。全部业务输入使用已授权的SIMULATION，代码和必要集成通过后登记IMPLEMENTED，正式客户验收独立登记。

## 当前依赖核查

已经完整读取官方固定提交的Python SDK、architecture、模型适配器和工具说明，重新核查官方PyPI发行元数据。官方SDK 0.1.5rc1的wheel已经下载到`.implementation-work/harness-wheels/deepseek_harness_sdk-0.1.5rc1-py3-none-any.whl`；其同版本runtime要求macOS 14.0，本地主机为macOS 13.7.8。当前固定源码的SDK与runtime已按官方editable开发方式正常安装，metadata均为0.0.0.dev0；该版本只用于本地开发，正式发布资格继续独立核查。

当前官方源码压缩包固定提交为5badb15009ae1756c3afe0ae0cef1faafc290ccc，压缩包SHA-256为79fe2657c951052e29995f0e094968e193db544b3f3a5bfd7ad36b58689bbd43。完整pnpm依赖安装及正常安装脚本已经通过；官方build-exe-for-python-sdk.ts已经完成完整依赖检查、本机组件、全部host/client和网页构建。官方readClientBuildRecord核验355项当前客户端文件，摘要为f8642a097280e6cf85686cfb449b1a8502474b81143b1c3f6dc93f89c2e5e22c；完整程序及配套资源已经生成。DSH_CLIENT_COMMIT_HASH使用压缩包内的原提交摘要，TMPDIR使用项目内已忽略的.build-work绝对路径。十五项官方固定解释器及库资源已完整取得并逐项核验原摘要；scripts/prepare_harness_downloads.py、harness-download-result.json和harness-downloads.log维护当前结果。当前专业SDK初始化和工程模拟通信检查通过；正式发行资格及完整业务执行继续核验。

官方完整程序、ripgrep、spawn-helper、Office及全部运行资源已经生成并同步到当前SDK runtime；Office文档往返及全部Python依赖检查通过。scripts/check_harness_carrier.py通过公开HarnessClient发送实际JSON-RPC并确认关闭，记录为`.implementation-work/harness-carrier-result.json`，执行耗时16.803秒；完整程序日志为harness-package-verified-assets.log。专业运行配置和模型适配器由professional-executor维护，公开HarnessClient.initialize已经成功加载实际专业配置。

## 当前专业插件与验证

professional-executor/src/index.ts读取完整封闭配置，核查实际十三项插件、模型路由、工具Schema及角色范围，注册agent/created和最终工具guard。当前工程配置为Planner，实际工具仅为read_artifact、load_skill、ask_user_question和submit_plan_candidate。src/tools.ts维护全部七项角色及AT-01—10映射，服务调用重新核查当前授权；工具参数与已保存模型终态中的调用摘要匹配后才能进入执行。agent-design/tool-inputs.schema.json和tool-success.schema.json保持LLD-Agent§3.3/3.4完整定义，并按实际摘要加载；标准引用由json-schema-ref-parser处理。

src/enterprise-model.ts和model-stream.ts执行固定路由、零次附加重试、精确参数、事件身份及顺序、工具片段、用量和ModelResult重读校验。完整响应及保存结果检查通过后才允许工具派发；同动作恢复使用原结果，未知调用明确要求核对。src/bridge.ts通过实际Unix Socket、0600凭据与Socket、Undici及成熟SSE组件通信，控制请求具有独立十秒时限。professional-executor/profiles/medical-agent使用官方支持的本地开发链接方式，SDK提供其已安装的固定共享服务依赖；正常npm与pnpm安装均已完成，当前npm依赖审计为零项漏洞。正式部署制品的完整摘要及资格继续发布任务。

当前Red由professional-initialize-red.log中的未知enterprise-model路由复现。Green为scripts/check_professional_executor.py --execution 20261009-ADAPTER-GREEN-01，回执`.implementation-work/IC-14-PROFESSIONAL-20261009-ADAPTER-GREEN-01/receipt.json`，SHA-256为639e7a3915525b745539c223f6ef59aa58c9c5eeb621ca392bb3f1af4057a797，exit_code=0、inputs_unchanged=true。实际严格构建、六项协议及Unix Socket检查、官方SDK初始化、实际插件/工具/模型清单均通过。工程模拟接口使用SIMULATION输入及实际文件保存、UTF-8分段SSE和原结果重读，重复动作的发送计数为一次；该计数验证专业适配层，不代表C07业务预算或费用账单已经完成。原始结果由professional-initialize-result.json、professional-bridge-result.json及专业运行清单维护。

## 完整开发范围

medical-agent保留官方SDK JSON-RPC入口，独立home、目录、进程和session；全部时限、并发、费用、工具及Schema由固定发布配置提供。只登记角色允许的AT-01—10，不提供Shell、SQL、安装、任意文件操作、公开网络直连或自主创建子任务。全部工具经IN-02逐动作核查，人工ASK保存等待项并结束Activity。

enterprise-model适配器只调用IA-07，供应商秘密由C07管理。模型流、已保存ModelResult、取消传播、进程退出和旧租约拒绝必须具有完整实际运行证据；缺少证明保持未完成。最终插件树和实际工具清单继续IC-14要求。

## 当前状态

profile和TypeScript专业插件已经通过当前工程集成检查。Python宿主及专业Activity、IA/IN业务接口、持久当前授权、调用保存和费用核对、全部角色业务使用、取消及进程退出证明、完整IC-14继续实施；尚未登记完整功能完成，last_verified=2026-10-09。
