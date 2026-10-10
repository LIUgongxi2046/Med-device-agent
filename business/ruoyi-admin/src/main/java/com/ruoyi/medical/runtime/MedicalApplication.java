package com.ruoyi.medical.runtime;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.ruoyi.medical",
        exclude = {DataSourceAutoConfiguration.class, DruidDataSourceAutoConfigure.class})
public class MedicalApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(MedicalApplication.class);
        application.setDefaultProperties(java.util.Map.of("spring.config.name", "application-medical-local"));
        application.run(args);
    }

    @Bean(name = "masterDataSource", initMethod = "init", destroyMethod = "close")
    @ConfigurationProperties("spring.datasource.druid.master")
    DruidDataSource masterDataSource() { return new DruidDataSource(); }
}
