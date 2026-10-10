package com.ruoyi.medical.persistence;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ruoyi.medical.persistence.mapper.TenantWatermarkMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class SqlParseCacheTest {
    private String currentMapperSql() throws Exception {
        var factory = new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource());
        return factory.getConfiguration().getMappedStatement(TenantWatermarkMapper.class.getName() + ".lockCurrentTenant")
            .getBoundSql(null).getSql();
    }

    @Test
    void changingOneParsedMapperDoesNotChangeTheNextRead() throws Exception {
        String sql = currentMapperSql();
        var cache = new FailFastSqlParseCache();
        var original = CCJSqlParserUtil.parse(sql);
        cache.putStatement(sql, original);
        var first = assertInstanceOf(PlainSelect.class, cache.getStatement(sql));
        assertNotSame(original, first);
        first.getSelectItems().clear();
        var next = assertInstanceOf(PlainSelect.class, cache.getStatement(sql));
        assertNotSame(first, next);
        assertEquals(original.toString(), next.toString());
        assertEquals(2, next.getSelectItems().size());
    }

    @Test
    void changingOneParsedTransactionDoesNotChangeItsSavedStatements() throws Exception {
        Path root = Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var blocks = new ArrayList<String>();
        Parser.builder().build().parse(Files.readString(root.resolve("LLD-后端.md"))).accept(new AbstractVisitor() {
            @Override public void visit(FencedCodeBlock block) {
                if (block.getInfo().equals("sql")) blocks.add(block.getLiteral());
            }
        });
        assertEquals(1, blocks.size());
        String sql = blocks.get(0);
        var original = CCJSqlParserUtil.parseStatements(sql);
        assertEquals(4, original.size());
        var cache = new FailFastSqlParseCache();
        cache.putStatements(sql, original);
        var first = cache.getStatements(sql);
        assertInstanceOf(PlainSelect.class, first.get(0)).setWhere(null);
        var next = cache.getStatements(sql);
        assertNotSame(first, next);
        assertEquals(original.toString(), next.toString());
    }

    @Test
    void damagedSerializedMapperFailsAtRead() throws Exception {
        String sql = currentMapperSql();
        var storage = Caffeine.newBuilder().<String, byte[]>build();
        var cache = new FailFastSqlParseCache(storage);
        cache.putStatement(sql, CCJSqlParserUtil.parse(sql));
        assertEquals(1, storage.estimatedSize());
        // 截断实际序列化结果，核查损坏读取必须立即报告错误。
        storage.asMap().replaceAll((key, saved) -> Arrays.copyOf(saved, saved.length / 2));
        assertThrows(RuntimeException.class, () -> cache.getStatement(sql));
        assertEquals(1, storage.estimatedSize());
    }

    @Test
    void singleAndMultipleParsingOfTheSameMapperKeepTheirTypes() throws Exception {
        String sql = currentMapperSql();
        var single = CCJSqlParserUtil.parse(sql);
        var multiple = CCJSqlParserUtil.parseStatements(sql);
        var cache = new FailFastSqlParseCache();
        cache.putStatement(sql, single);
        cache.putStatements(sql, multiple);
        assertEquals(single.toString(), cache.getStatement(sql).toString());
        assertEquals(multiple.toString(), cache.getStatements(sql).toString());
    }
}
