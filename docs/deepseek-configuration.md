# 本地 DeepSeek API 配置

日期：2026-10-06；当前适用于 C07 Python 供应商适配器。本地独立登录继续由 Java 服务提供。模型配置、业务预算和模型发布使用 IMP-07 的实施与验收记录。

## 密钥位置

在本地编辑器中打开项目文件 `.implementation-work/runtime/deepseek-api-key`，填写自己的真实 DeepSeek API 密钥，内容使用单行文本，可以保留末尾换行。当前文件已经配置，并通过本地权限及格式检查；父目录权限为0700，文件权限为0600，整个目录已经被 Git 忽略。编辑器保存后需要保持上述权限。

2026-10-06再次执行本地check，实际返回configured=true；本次没有请求模型清单或生成接口，业务发布及费用条件继续按原要求验证。

2026-10-04实际执行check及verify通过，真实/models清单返回deepseek-flash和deepseek-v4-pro，连接回执位于.implementation-work/deepseek-verification-4cd398d4-eb70-4e72-b060-fbfd1b76a614.json，scope为PROVIDER_CONNECTION_ONLY。本次没有提交业务资料或者调用生成接口；实际模型发布、许可、预算、价格及文档业务继续由IMP-07实施和验收。

密钥只由 Python 供应商入口读取。浏览器、专业 Worker、Java 业务记录和普通检查回执保存秘密引用及必要身份信息。文件缺少、内容为空、公共读取权限、链接目录、符号链接或共享链接都会立即拒绝读取。再次运行 prepare 会拒绝覆盖已有文件。

## 本地检查与真实连接

在项目根目录执行：

```bash
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py check
.implementation-work/model-gateway-venv/bin/python scripts/configure_deepseek.py verify
```

check 检查本地文件与密钥格式。verify 通过实际密钥请求官方 `/models`，保存供应商返回的真实模型清单、验证时间、端点及独立验证身份。回执位于 `.implementation-work/deepseek-verification-<verification_id>.json`；输出只显示模型身份及回执位置。

可以使用 `verify --model <model_id>` 核对用户指定模型是否出现在实际清单。模型名称和精确版本沿用供应商真实返回与批准配置；当前不预设旧名称能够继续使用。[官方模型清单](https://api-docs.deepseek.com/api/list-models/)定义元数据接口，[官方价格](https://api-docs.deepseek.com/quick_start/pricing/)提供价格及适用时间规则。

## 调用与费用条件

供应商端点固定为 `https://api.deepseek.com`，SDK 自动重试为零，禁用代理环境配置及 HTTP 重定向。连接时限为5秒，读取时限为60秒，连接核验总时限为10秒；生成总时限由配置明确填写，最大为300秒。

真实连接通过以后，业务生成还需要企业、项目和资料外发许可、固定模型配置与秘密版本、经确认的价格及币种、Token与费用上限、当前授权、原子预算预留和一次发送准入。Java 的完整 CP/IN/IA 接口、预算事务和 S13 页面仍在实施，当前未发布业务生成入口。连接回执不能证明完整 IC-07 或注册业务验收完成。

当前提供文本和函数工具的协议适配。工具模式要求明确关闭内部推理；内部推理内容不进入业务历史、事件和成品。输入中传入其他端点或未发布参数会被拒绝。未知用量保持 null，调用中断交由原调用记录核对；同一收费动作不能通过重发恢复。

## 验证入口

```bash
.implementation-work/model-gateway-venv/bin/python scripts/check_model_gateway.py --execution <unique_execution_id>
```

工程检查覆盖本地秘密存储、SDK 配置及输入拒绝行为。真实供应商响应、模型产物保存、预算与结算、取消和业务采用需要对应的实际外部调用及持久化证据。完整开发记录读取[IMP-07](development/IMP-07.md)，完整任务范围读取[实施任务计划](../实施任务计划.md)。
