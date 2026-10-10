package com.ruoyi.medical.runtime;

import com.alibaba.druid.pool.DruidDataSource;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.domain.entity.SysDictType;
import com.ruoyi.generator.domain.GenTable;
import com.ruoyi.generator.mapper.GenTableColumnMapper;
import com.ruoyi.generator.mapper.GenTableMapper;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.domain.SysJobLog;
import com.ruoyi.quartz.mapper.SysJobLogMapper;
import com.ruoyi.quartz.mapper.SysJobMapper;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.domain.SysNotice;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.mapper.SysDictDataMapper;
import com.ruoyi.system.mapper.SysDictTypeMapper;
import com.ruoyi.system.mapper.SysLogininforMapper;
import com.ruoyi.system.mapper.SysNoticeMapper;
import com.ruoyi.system.mapper.SysNoticeReadMapper;
import com.ruoyi.system.mapper.SysOperLogMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.session.LocalCacheScope;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import static org.junit.jupiter.api.Assertions.*;

public final class RuoYiManagementMapperTest {
    private static final List<String> TABLES = List.of("sys_oper_log", "sys_dict_type", "sys_dict_data", "sys_logininfor", "sys_job",
        "sys_job_log", "sys_notice", "sys_notice_read", "gen_table", "gen_table_column");

    @Test void originalSystemQuartzAndGeneratorMappersReadActualMysqlWithoutCreatingRecords() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")); var json = JsonMapper.builder().build();
        var secret = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        var identity = json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-identity.json")));
        var database = new DruidDataSource(); database.setDriverClassName("com.mysql.cj.jdbc.Driver");
        database.setUrl("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        database.setUsername("medical_runtime"); database.setPassword(secret.required("database_password").textValue());
        database.setMaxActive(2); database.setFailFast(true); database.setInitExceptionThrow(true);
        database.setDefaultReadOnly(true);
        var proof = json.createObjectNode(); proof.put("scope", "ACTUAL_ORIGINAL_MANAGEMENT_MAPPERS_READ_ONLY");
        proof.put("max_heap_bytes", Runtime.getRuntime().maxMemory());
        try {
            database.init();
            var jdbc = new JdbcTemplate(database);
            assertTrue(Objects.requireNonNull(jdbc.execute((ConnectionCallback<Boolean>) connection -> connection.isReadOnly())));
            assertEquals(1, jdbc.queryForObject("SELECT @@session.transaction_read_only", Integer.class));
            assertEquals(identity.required("server_uuid").textValue(), jdbc.queryForObject("SELECT @@server_uuid", String.class));
            assertEquals(identity.required("database").textValue(), jdbc.queryForObject("SELECT DATABASE()", String.class));
            var before = counts(jdbc, json);
            var resources = new ArrayList<Resource>(); var resolver = new PathMatchingResourcePatternResolver();
            for (String folder : List.of("system", "quartz", "generator")) {
                var current = resolver.getResources("classpath*:mapper/" + folder + "/*.xml");
                assertTrue(current.length > 0); resources.addAll(List.of(current));
            }
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setLogImpl(NoLoggingImpl.class);
            configuration.setCacheEnabled(false); configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(database); factory.setConfiguration(configuration);
            factory.setTypeAliasesPackage("com.ruoyi.common.core.domain.entity;com.ruoyi.system.domain;com.ruoyi.quartz.domain;com.ruoyi.generator.domain");
            factory.setMapperLocations(resources.toArray(Resource[]::new)); factory.setFailFast(true);
            var queries = json.createObjectNode();
            try (var session = Objects.requireNonNull(factory.getObject()).openSession()) {
                assertTrue(session.getConnection().isReadOnly());
                // 使用原Mapper和XML执行真实查询，技术检查不创建业务样本或权限。
                queries.put("sys_oper_log", session.getMapper(SysOperLogMapper.class).selectOperLogList(new SysOperLog()).size());
                queries.put("sys_dict_type", session.getMapper(SysDictTypeMapper.class).selectDictTypeList(new SysDictType()).size());
                queries.put("sys_dict_data", session.getMapper(SysDictDataMapper.class).selectDictDataList(new SysDictData()).size());
                queries.put("sys_logininfor", session.getMapper(SysLogininforMapper.class).selectLogininforList(new SysLogininfor()).size());
                queries.put("sys_job", session.getMapper(SysJobMapper.class).selectJobList(new SysJob()).size());
                queries.put("sys_job_log", session.getMapper(SysJobLogMapper.class).selectJobLogList(new SysJobLog()).size());
                queries.put("sys_notice", session.getMapper(SysNoticeMapper.class).selectNoticeList(new SysNotice()).size());
                queries.put("gen_table", session.getMapper(GenTableMapper.class).selectGenTableList(new GenTable()).size());
                var columns = session.getMapper(GenTableColumnMapper.class).selectDbTableColumnsByName("gen_table_column");
                var expectedColumns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                    + "AND table_name='gen_table_column' ORDER BY ordinal_position", String.class);
                assertEquals(expectedColumns, columns.stream().map(column -> column.getColumnName()).toList());
                proof.put("generator_actual_column_count", columns.size());
                var users = jdbc.queryForList("SELECT user_id FROM sys_user WHERE del_flag='0' ORDER BY user_id", Long.class);
                assertFalse(users.isEmpty());
                var noticeReads = session.getMapper(SysNoticeReadMapper.class);
                for (Long user : users) {
                    assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice n WHERE n.status='0' AND NOT EXISTS "
                        + "(SELECT 1 FROM sys_notice_read r WHERE r.notice_id=n.notice_id AND r.user_id=?)", Integer.class, user),
                        noticeReads.selectUnreadCount(user));
                    var notices = noticeReads.selectNoticeListWithReadStatus(user, 100);
                    assertEquals(jdbc.queryForObject("SELECT LEAST(COUNT(*),100) FROM sys_notice WHERE status='0'", Integer.class), notices.size());
                }
                proof.put("actual_notice_user_count", users.size());
                for (var entry : queries.properties()) assertEquals(before.required(entry.getKey()).longValue(), entry.getValue().longValue());
            }
            assertEquals(before, counts(jdbc, json)); proof.set("row_counts", before); proof.set("original_query_counts", queries);
            proof.put("mapper_resource_count", resources.size()); proof.put("customer_records_created", false);
        } finally { database.close(); }
        assertTrue(database.isClosed());
        proof.put("connections_closed", true); proof.put("database_read_only", true); proof.put("customer_success_verified", false);
        Files.write(Path.of(System.getProperty("medical.managementDataEvidence")), json.writeValueAsBytes(proof));
    }

    private static com.fasterxml.jackson.databind.JsonNode counts(JdbcTemplate jdbc, JsonMapper json) {
        var values = json.createObjectNode();
        for (String table : TABLES) values.put(table, Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        for (String table : List.of("sys_user", "sys_role", "sys_menu", "tenant", "tenant_member", "project", "task_head"))
            values.put(table, Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)));
        return values;
    }
}
