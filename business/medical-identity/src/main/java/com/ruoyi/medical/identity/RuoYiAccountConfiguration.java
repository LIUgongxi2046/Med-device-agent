package com.ruoyi.medical.identity;

import com.github.pagehelper.PageInterceptor;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.config.RedisConfig;
import com.ruoyi.framework.aspectj.DataScopeAspect;
import com.ruoyi.framework.web.service.SysPasswordService;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.framework.web.service.UserDetailsServiceImpl;
import com.ruoyi.system.service.impl.SysConfigServiceImpl;
import com.ruoyi.system.service.impl.SysDeptServiceImpl;
import com.ruoyi.system.service.impl.SysUserServiceImpl;
import com.ruoyi.system.service.impl.SysRoleServiceImpl;
import com.ruoyi.system.service.impl.SysMenuServiceImpl;
import com.ruoyi.system.service.impl.SysPostServiceImpl;
import com.ruoyi.system.service.impl.SysDictTypeServiceImpl;
import com.ruoyi.system.service.impl.SysDictDataServiceImpl;
import com.ruoyi.system.service.impl.SysNoticeServiceImpl;
import com.ruoyi.system.service.impl.SysNoticeReadServiceImpl;
import com.ruoyi.system.service.impl.SysOperLogServiceImpl;
import com.ruoyi.system.service.impl.SysLogininforServiceImpl;
import com.ruoyi.system.service.impl.SysUserOnlineServiceImpl;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Import({SysUserServiceImpl.class, SysDeptServiceImpl.class, SysConfigServiceImpl.class, RedisCache.class,
        RedisConfig.class, SysPasswordService.class, SpringUtils.class, UserDetailsServiceImpl.class,
        SysPermissionService.class, PermissionService.class, SysRoleServiceImpl.class, SysMenuServiceImpl.class,
        SysPostServiceImpl.class, SysDictTypeServiceImpl.class, SysDictDataServiceImpl.class,
        SysNoticeServiceImpl.class, SysNoticeReadServiceImpl.class, SysOperLogServiceImpl.class,
        SysLogininforServiceImpl.class, SysUserOnlineServiceImpl.class,
        DataScopeAspect.class})
@MapperScan(basePackages = {"com.ruoyi.system.mapper", "com.ruoyi.medical.identity.mapper"}, sqlSessionFactoryRef = "ruoyiAccountSqlSessionFactory")
public class RuoYiAccountConfiguration {
    @Bean
    SqlSessionFactory ruoyiAccountSqlSessionFactory(@Qualifier("masterDataSource") DataSource dataSource) throws Exception {
        // 原用户服务与平台身份扩展共用框架分页；企业业务继续使用独立的租户拦截器。
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setCacheEnabled(false);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setLogImpl(NoLoggingImpl.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setTypeAliasesPackage("com.ruoyi.common.core.domain.entity;com.ruoyi.system.domain");
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath*:mapper/system/*.xml");
        if (resources.length == 0) throw new IllegalStateException("RUOYI_ACCOUNT_MAPPERS_REQUIRED");
        var identityResources = resolver.getResources("classpath*:local-identity-mappers/*.xml");
        if (identityResources.length == 0) throw new IllegalStateException("LOCAL_IDENTITY_MAPPERS_REQUIRED");
        factory.setMapperLocations(Stream.concat(Stream.of(resources), Stream.of(identityResources)).toArray(org.springframework.core.io.Resource[]::new));
        var pagination = new PageInterceptor();
        var properties = new Properties(); properties.setProperty("helperDialect", "mysql");
        properties.setProperty("reasonable", "false"); properties.setProperty("pageSizeZero", "false");
        pagination.setProperties(properties); factory.setPlugins(pagination);
        factory.setFailFast(true);
        return Objects.requireNonNull(factory.getObject());
    }
}
