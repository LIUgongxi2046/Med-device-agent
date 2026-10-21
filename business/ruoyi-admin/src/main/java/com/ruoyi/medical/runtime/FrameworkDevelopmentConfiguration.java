package com.ruoyi.medical.runtime;

import com.ruoyi.generator.config.GenConfig;
import com.ruoyi.generator.controller.GenController;
import com.ruoyi.generator.service.GenTableServiceImpl;
import com.ruoyi.generator.service.GenTableColumnServiceImpl;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Explicit development opt-in; never activates generator against the business datasource. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "medical.framework.generator.enabled", havingValue = "true")
@Import({GenController.class, GenConfig.class, FrameworkGeneratorMetadataService.class, GenTableColumnServiceImpl.class})
@MapperScan(basePackages = "com.ruoyi.generator.mapper", sqlSessionFactoryRef = "frameworkGeneratorSqlSessionFactory")
public class FrameworkDevelopmentConfiguration {
    public FrameworkDevelopmentConfiguration(Environment environment) {
        if (!"local".equals(environment.getProperty("medical.identity.mode"))
                || !"DEVELOPMENT".equals(environment.getProperty("medical.framework.environment"))) {
            throw new IllegalStateException("FRAMEWORK_GENERATOR_DEVELOPMENT_ONLY");
        }
    }

    @Bean(name = "frameworkDevelopmentDataSource", defaultCandidate = false, initMethod = "init", destroyMethod = "close")
    com.alibaba.druid.pool.DruidDataSource developmentDataSource(Environment environment,
            @Qualifier("masterDataSource") DataSource master) {
        String url = environment.getRequiredProperty("medical.framework.generator.datasource.url");
        String username = environment.getRequiredProperty("medical.framework.generator.datasource.username");
        if (!url.matches("jdbc:mysql://[^/?#]+/framework_dev_[a-z0-9_]+(?:\\?.*)?")
                || !(master instanceof com.alibaba.druid.pool.DruidDataSource business)
                || username.equals(business.getUsername()))
            throw new IllegalStateException("FRAMEWORK_GENERATOR_ISOLATED_DATABASE_REQUIRED");
        var source = new com.alibaba.druid.pool.DruidDataSource();
        source.setUrl(url); source.setUsername(username);
        source.setPassword(environment.getRequiredProperty("medical.framework.generator.datasource.password"));
        source.setInitialSize(0); source.setMaxActive(4);
        return source;
    }

    @Bean(defaultCandidate = false)
    SqlSessionFactory frameworkGeneratorSqlSessionFactory(
            @Qualifier("frameworkDevelopmentDataSource") DataSource dataSource,
            @Qualifier("masterDataSource") DataSource master) throws Exception {
        if (dataSource == master || !(dataSource instanceof com.alibaba.druid.pool.DruidDataSource development)
                || !(master instanceof com.alibaba.druid.pool.DruidDataSource business)
                || development.getUrl() == null || business.getUrl() == null
                || development.getUrl().split("\\?", 2)[0].equalsIgnoreCase(business.getUrl().split("\\?", 2)[0])) throw new IllegalStateException("FRAMEWORK_GENERATOR_SEPARATE_DATASOURCE_REQUIRED");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("com.ruoyi.generator.domain");
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/generator/*.xml"));
        var pagination = new com.github.pagehelper.PageInterceptor();
        var properties = new java.util.Properties(); properties.setProperty("helperDialect", "mysql");
        properties.setProperty("reasonable", "false"); properties.setProperty("pageSizeZero", "false");
        pagination.setProperties(properties); factory.setPlugins(pagination);
        factory.setFailFast(true);
        return java.util.Objects.requireNonNull(factory.getObject());
    }
}
