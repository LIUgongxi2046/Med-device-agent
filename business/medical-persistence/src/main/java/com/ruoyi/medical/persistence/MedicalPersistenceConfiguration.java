package com.ruoyi.medical.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.parser.JsqlParserGlobal;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.github.pagehelper.PageInterceptor;
import java.util.Objects;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration
@MapperScan(basePackages = "com.ruoyi.medical", annotationClass = Mapper.class,
            sqlSessionFactoryRef = "medicalSqlSessionFactory")
public class MedicalPersistenceConfiguration {
    private static final FailFastSqlParseCache SQL_PARSE_CACHE = new FailFastSqlParseCache();

    @Bean
    @Primary
    public SqlSessionFactory medicalSqlSessionFactory(DataSource dataSource) throws Exception {
        JsqlParserGlobal.setJsqlParseCache(SQL_PARSE_CACHE);
        var configuration = new MybatisConfiguration();
        configuration.setCacheEnabled(false);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.setDefaultExecutorType(ExecutorType.SIMPLE);
        configuration.setLogImpl(NoLoggingImpl.class);
        var plugin = new MybatisPlusInterceptor();
        plugin.addInnerInterceptor(new ProtectedTenantInterceptor());
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        var pagination = new PageInterceptor();
        var properties = new java.util.Properties(); properties.setProperty("helperDialect", "mysql");
        properties.setProperty("reasonable", "false"); properties.setProperty("pageSizeZero", "false");
        pagination.setProperties(properties);
        factory.setPlugins(plugin, pagination);
        factory.setFailFast(true);
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:medical-mappers/**/*.xml");
        if (resources.length == 0) throw new IllegalStateException("业务 Mapper 文件缺失");
        factory.setMapperLocations(resources);
        return Objects.requireNonNull(factory.getObject());
    }
}
