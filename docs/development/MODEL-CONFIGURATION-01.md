# MODEL-CONFIGURATION-01 模型配置与固定版本

## 任务身份与依据

本任务属于BE-07，关联C06/C07、CP-01—03、IC-07及BE-12/13/14/28。唯一状态来源为本记录，当前IN_PROGRESS。2026-10-14读取IMP-07、IMP-12、开发索引、LLD-后端模型配置/内部调用定义、007迁移、BudgetProtocol、实际供应商适配器与凭据读取、当前CP公开路径，以及已实施RUN-TOKEN-AUTHORITY-01。完整父工单状态保持IN_PROGRESS。

## 当前事实与验收

当前已有Python供应商适配、价格数学、受保护秘密读取，以及公开预算管理；model_config_revision的完整持久配置、秘密版本和价格依赖、实际连接回执及正式发布仍需实施。已有DeepSeek秘密文件保持受保护状态，不需要重新提供秘密值。

2026-10-14已经核查当前DeepSeek官方[模型列表定义](https://api-docs.deepseek.com/api/list-models/)及[价格定义](https://api-docs.deepseek.com/quick_start/pricing/)完整内容。模型列表包含实际名称、context_window、max_output_tokens、输入/输出媒介、effort及按协议的api_capabilities；当前公开价格区分PEAK/OFF-PEAK，包含工作日、UTC时间和中国公共假日规则。固定价格版本必须完整表达适用条件，预留继续采用完整最高价格上界；结算需要实际供应商回执和适用价格依据，不建立人工假日、费用或批准记录。

当前DeepSeekProvider.list_models已经实现真实模型读取，configure_deepseek.py的verify会保存完整实际返回；后续开发复用该入口。正确环境为.implementation-work/model-gateway-venv，当前已有受保护密钥及供应商连接记录；配置持久化、元数据资格、预算和收费调用继续分别实施。

Given当前模型配置管理许可、明确提供的供应商/端点/地区/模型身份、秘密引用及固定版本、全部限制/data_policy、价格引用、请求参数Schema与协议修订；When保存固定配置；Then同一企业事务保存完整不可变内容与摘要、版本登记、来源关系、原操作、审计和Outbox，CAS更新维护头并重读原版本。缺少输入、未知字段、旧修订、异参和越权均拒绝。

Given完整实际连接回执、独立评测及当前授权发布决定；When发布或新任务采用；Then完整当前依赖与原固定版本共同核验。没有真实许可、价格和用量时，不建立批准价格、模型调用、费用或专业成果。

## 未完成范围与继续条件

完整封闭定义及保存/读取、秘密引用与版本管理、价格依赖、连接/评测/发布、当前任务消费者与合法业务成功仍待实施。配置、预算预留、一次发送、结果保存、未知占用与结算保持完整BE-07责任；缺少业务输入只影响相应实际动作。
