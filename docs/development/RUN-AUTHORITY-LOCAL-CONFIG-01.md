# RUN-AUTHORITY-LOCAL-CONFIG-01 本地服务证书与签发配置

## 任务身份与依据

本任务属于BE-04/36，关联RUN-TOKEN-AUTHORITY-01及IMP-04/13/36。任务状态来源为本记录，当前IMPLEMENTED。2026-10-14读取受保护配置定义、ServiceCertificateTrustStore、RunTokenPolicyStore、RunTokenSigningKeyStore、当前应用配置及原证书生成工具。用户已授权继续后端开发及处理本地连接配置；本任务配置本地服务身份和密码组件。

当前本地配置及应用读取范围IMPLEMENTED，文档CURRENT，last_verified=2026-10-14；生产TLS、正式Worker消费和证书维护验收继续BE-13/36。本地配置不改变客户许可及完整父工单状态。

## 验收行为

Given本地受控目录和当前操作系统所有者；When生成实际CA、服务证书、签名CRL与RSA签发密钥；Then受保护配置与文件完整绑定，私钥只有当前所有者能够读取，公钥Policy与私钥SPKI一致。Given明确期限和容量；When当前C02配置启动；Then实际读取三个完整配置且当前应用加载对应代码与配置，原公开身份检查继续通过。

## 当前范围

服务URI使用本地服务登记身份，Token发行者与受众明确提供。证书、CRL、令牌及配置容量均使用命令的明确参数；不采用客户默认值。生成结果不产生客户企业、角色批准、模型许可、运行、令牌、费用或专业成果。正式证书维护、生产TLS及Worker使用分别继续BE-13/36要求。

## 验证与证据

生成工具为scripts/prepare_run_authority_configuration.py，独立固定依赖为cryptography50.0.2、boto3 1.43.108和PyYAML6.0.3；配置绑定由scripts/activate_run_authority_configuration.py维护。实际CA、客户端证书、当前签名CRL和2048位RSA私钥已经生成；服务URI=urn:medical-local:service:workflow-worker，issuer=urn:medical-local:c02，audience=medical-business-internal。明确命令参数为证书24小时、CRL12小时、Token最大300秒/4096字节、配置容量131072字节，适用本地受控环境；当前客户保存期及生产策略继续原范围。

实际配置回执`.implementation-work/runtime/run-authority-configuration-c36257df-cafe-4283-b68a-a02c846c48d5.json`，SHA-256为ce89eb7ad07b2d4c7f1710456d78134ed518c09541e392cc049833564387d3d7。目录`.implementation-work/runtime/run-authority/c39f20bb-ece6-4f48-a7b8-bbd5d351d960`权限0700，七份配置/PEM文件0600；秘密值只保存在这些文件。配置修订、证书/公钥摘要和具体到期时间读取该受保护回执。

绑定回执`.implementation-work/runtime/application-profiles/3b27d21f-2ebf-4676-9d20-302834c57770/run-authority-profile.json`，SHA-256为a27d606d6a6c675bde6467972dd30902355668379934a8f2a8021daa0121ba62。新附加YAML摘要5c5905c9f1864f6f986c951a5dd338cc95134aea4a770a6e26cd4a8367863371；原附加配置的其他完整内容经PyYAML重新读取比较保持一致。C02明确enabled=true及实际配置目录、当前所有者和容量；来源四项Worker及权利审批继续关闭。

当前应用PID45581、部署530d8708-5360-424a-b33b-c05422a143ed，启动86.467秒；实际读取三份受保护配置并完成当前公钥/私钥绑定和CA/CRL核验。当前运行回执`.implementation-work/source-processing-runtime-checks/e2f15653-3b04-4a61-83d6-70d778971e20/receipt.json`，SHA-256为5b768e97392d84536ec45662275dff35c41ac7d4966dd52abeca711815a4d5fa，原公开身份检查继续通过。内部HTTP、实际mTLS请求和正式Worker尚未通过，本地配置准备不授予业务许可。
