package com.ruoyi.medical.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameworkFormProxyTest {
    @Test
    void createsControllerMethodSecurityProxy() {
        ProxyFactory factory = new ProxyFactory();
        factory.setTargetClass(FrameworkFormController.class);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        assertTrue(AopUtils.isCglibProxy(factory.getProxy()));
    }
}
