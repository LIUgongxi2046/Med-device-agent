package com.ruoyi.medical.persistence;

import com.baomidou.mybatisplus.extension.parser.cache.JdkSerialCaffeineJsqlParseCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

final class FailFastSqlParseCache extends JdkSerialCaffeineJsqlParseCache {
    FailFastSqlParseCache() {
        this(Caffeine.newBuilder().maximumWeight(32L * 1024 * 1024)
            .weigher((String sql, byte[] bytes) -> Math.addExact(Math.multiplyExact(sql.length(), Character.BYTES), bytes.length))
            .expireAfterWrite(Duration.ofSeconds(5)).build());
    }

    FailFastSqlParseCache(Cache<String, byte[]> storage) { super(storage); }

    // 两个原生解析入口分别保存完整SQL，保持各自的AST类型。
    @Override public Statement getStatement(String sql) { return super.getStatement("statement:" + sql); }
    @Override public void putStatement(String sql, Statement statement) { super.putStatement("statement:" + sql, statement); }
    @Override public Statements getStatements(String sql) { return super.getStatements("statements:" + sql); }
    @Override public void putStatements(String sql, Statements statements) { super.putStatements("statements:" + sql, statements); }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T get(String sql) {
        byte[] bytes = cache.getIfPresent(sql);
        // 缓存损坏必须在原生反序列化位置报错，企业检查始终使用当前上下文。
        return bytes == null ? null : (T) deserialize(sql, bytes);
    }
}
