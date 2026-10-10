package com.ruoyi.medical.identity;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class LocalAccountPaginationTest {
    private static final String QUERY = "com.ruoyi.medical.identity.mapper.LocalAccountMapper.selectAccounts";
    private static DriverManagerDataSource database;
    private static Map<String, Object> owner;

    @BeforeAll static void actualOwner() throws Exception {
        var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var secrets = JsonMapper.builder().build().readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/service-secrets.json")));
        database = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "medical_runtime", secrets.required("database_password").textValue());
        owner = new JdbcTemplate(database).queryForMap("SELECT user_id,username,display_name,status,platform_role,revision,credential_revision "
                + "FROM platform_local_account WHERE status='ACTIVE' AND platform_role='PLATFORM_ADMIN'");
    }

    @Test void accountQueriesUseOriginalPageHelper() throws Exception {
        var factory = new RuoYiAccountConfiguration().ruoyiAccountSqlSessionFactory(database);
        assertTrue(factory.getConfiguration().getInterceptors().stream().anyMatch(PageInterceptor.class::isInstance),
                "账户查询需要原PageHelper组件");
    }

    @Test void nativePageReadsActualAccountAndClearsItsThreadState() throws Exception {
        var factory = new RuoYiAccountConfiguration().ruoyiAccountSqlSessionFactory(database);
        assertTrue(factory.getConfiguration().hasStatement(QUERY), "账户身份扩展需要实际MyBatis查询");
        try (var session = factory.openSession()) {
            var page = PageHelper.startPage(1, 2, false).<LocalAccountProtocol.AccountItem>doSelectPageInfo(() ->
                    session.selectList(QUERY, java.util.Collections.singletonMap("cursor", null)));
            assertEquals(1, page.getList().size());
            var item = page.getList().get(0);
            assertEquals(owner.get("user_id"), item.user_id()); assertEquals(owner.get("username"), item.username());
            assertEquals(owner.get("display_name"), item.display_name()); assertEquals(owner.get("status"), item.status());
            assertEquals(owner.get("platform_role"), item.platform_role());
            assertEquals(owner.get("revision").toString(), item.revision());
            assertEquals(owner.get("credential_revision").toString(), item.credential_revision());
            assertNull(PageHelper.getLocalPage());
        }
    }

    @Test void nativePageHonoursActualCursorAndKeepsOriginalSystemQueries() throws Exception {
        var factory = new RuoYiAccountConfiguration().ruoyiAccountSqlSessionFactory(database);
        assertTrue(factory.getConfiguration().hasStatement(QUERY), "游标继续使用真实账户UUID");
        assertTrue(factory.getConfiguration().hasStatement("com.ruoyi.system.mapper.SysUserMapper.selectUserList"));
        var mapped = factory.getConfiguration().getMappedStatement(QUERY);
        assertFalse(mapped.isUseCache()); assertTrue(mapped.isFlushCacheRequired());
        try (var session = factory.openSession()) {
            var page = PageHelper.startPage(1, 2, false).<LocalAccountProtocol.AccountItem>doSelectPageInfo(() ->
                    session.selectList(QUERY, Map.of("cursor", owner.get("user_id"))));
            assertTrue(page.getList().isEmpty()); assertNull(PageHelper.getLocalPage());
        }
    }
}
