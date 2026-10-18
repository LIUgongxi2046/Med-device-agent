# 专业执行宿主组件（尚未完成运行闭环）

此包包含独立受限子进程、私有工作目录、会话/租约心跳、期限、进程组停止、实际Temporal Activity适配以及mTLS控制/本地Unix Socket桥接实现。它是后续专业执行器的组件，不是已经可部署运行的完整Agent。

宿主不持有供应商密钥。专业调用必须通过已批准的企业模型桥及真实后端准入。配置与实际runtime/profile文件需要固定身份；版本或摘要不匹配时停止。可选harness依赖版本是本次实际验证过的官方包，不代表自动批准替换原部署manifest中的冻结运行时。

离线验证使用真实短生命子进程与明确fixture，不生成专业输出。实际测试命令（已安装相应依赖后，从仓库根）：

    PYTHONPATH=professional-runtime/src python -m unittest discover -s professional-runtime/tests -p test_host.py -v
    PYTHONPATH=professional-runtime/src python -m unittest discover -s professional-runtime/tests -p test_temporal_adapter.py -v
    PYTHONPATH=professional-runtime/src python -m unittest discover -s professional-runtime/tests -p test_bridge_routes.py -v

完整发现还会运行test_bridge.py。当前执行环境创建AF_UNIX socket返回EPERM，因此该组10项实际socket测试受阻，未改为skip或伪装通过。不能把无socket路由测试当作其替代。

待完成：真实独立运行/node/input/context生产者、完整后端control/bridge/专业结果登记、隔离supervisor原始闭合证明、八项RunActivities及真实环境联调。进程组停止本身不证明供应商取消或全部隔离后代终止。不会以公开success字段或本地fixture创建专业成功证明。
