package com.ruoyi.medical.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import liquibase.Liquibase;
import liquibase.change.core.SQLFileChange;
import liquibase.database.core.MySQLDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MigrationBundleTest {
    @Test
    void actualMigrationResourcesHaveValidChangesAndChecksums() throws Exception {
        // 使用 Liquibase 解析器核查制品，不连接或替代目标数据库。
        try (var resources = new ClassLoaderResourceAccessor();
             var migration = new Liquibase("db/changelog/master.xml", resources, new MySQLDatabase())) {
            var changes = migration.getDatabaseChangeLog().getChangeSets();
            assertFalse(changes.isEmpty());
            var identities = new HashSet<String>();
            for (var change : changes) {
                assertTrue(identities.add(change.getFilePath() + ":" + change.getAuthor() + ":" + change.getId()));
                assertFalse(change.isRunInTransaction());
                assertEquals(Boolean.TRUE, change.getFailOnError());
                assertEquals(java.util.Set.of("mysql"), change.getDbmsSet());
                assertNotNull(change.generateCheckSum(liquibase.ChecksumVersion.latest()));
                for (var operation : change.getChanges()) {
                    assertFalse(operation.validate(migration.getDatabase()).hasErrors());
                    assertTrue(operation.generateStatements(migration.getDatabase()).length > 0);
                }
            }
            assertInstanceOf(SQLFileChange.class, changes.get(0).getChanges().get(0));
            assertEquals("data-design/core.sql", ((SQLFileChange) changes.get(0).getChanges().get(0)).getPath());
            var root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
            try (var input = getClass().getClassLoader().getResourceAsStream("data-design/core.sql")) {
                assertNotNull(input);
                assertArrayEquals(Files.readAllBytes(root.resolve("data-design/core.sql")), input.readAllBytes());
            }
            System.out.println("MIGRATION_RESOURCE_CHECK changesets=" + changes.size()
                + " core_sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(root.resolve("data-design/core.sql"))))
                + " scope=LOCAL_PARSER_CHECK");
        }
    }
}
