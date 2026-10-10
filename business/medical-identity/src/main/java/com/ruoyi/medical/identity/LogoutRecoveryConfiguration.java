package com.ruoyi.medical.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "oidc")
@EnableScheduling
public class LogoutRecoveryConfiguration { }
