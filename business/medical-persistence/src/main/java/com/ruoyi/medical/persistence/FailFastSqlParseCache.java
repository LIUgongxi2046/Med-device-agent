package com.ruoyi.medical.persistence;

import com.baomidou.mybatisplus.extension.parser.cache.FurySerialCaffeineJsqlParseCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import io.github.classgraph.ClassGraph;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;

final class FailFastSqlParseCache extends FurySerialCaffeineJsqlParseCache {
    private static final ThreadSafeFury SERIALIZER = serializer();

    private static ThreadSafeFury serializer() {
        // 固定依赖的反射序列化保持独立副本，首次业务读取无需等待动态编译。
        var serializer = Fury.builder().requireClassRegistration(true).withCodegen(false).withAsyncCompilation(false).buildThreadSafeFury();
        // 只登记固定依赖中JSQLParser的类型，输入字节不能指定额外类型。
        try (var classes = new ClassGraph().acceptPackages("net.sf.jsqlparser").enableClassInfo().ignoreClassVisibility().scan()) {
            classes.getAllClasses().stream().filter(type -> !type.isInterface() && !type.isAnnotation())
                .sorted(java.util.Comparator.comparing(io.github.classgraph.ClassInfo::getName))
                .forEach(type -> serializer.register(type.loadClass()));
        }
        return serializer;
    }

    @Override public byte[] serialize(Object value) { return SERIALIZER.serialize(value); }
    @Override public Object deserialize(String sql, byte[] bytes) { return SERIALIZER.deserialize(bytes); }

    FailFastSqlParseCache() {
        this(Caffeine.newBuilder().maximumWeight(32L * 1024 * 1024)
            .weigher((String sql, byte[] bytes) -> Math.addExact(Math.multiplyExact(sql.length(), Character.BYTES), bytes.length))
            .expireAfterWrite(Duration.ofMinutes(5)).build());
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
