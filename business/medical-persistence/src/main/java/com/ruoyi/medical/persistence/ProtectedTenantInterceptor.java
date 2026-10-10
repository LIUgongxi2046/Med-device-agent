package com.ruoyi.medical.persistence;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ruoyi.medical.contracts.ErrorCode;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public final class ProtectedTenantInterceptor extends TenantLineInnerInterceptor {
    public ProtectedTenantInterceptor() { super(new ServerTenantHandler()); }

    private static TenantAccessException denied() { return new TenantAccessException(ErrorCode.ACCESS_DENIED); }

    private static void requireStatement(MappedStatement statement, Object parameter) {
        TenantScope.requireSqlAccess(statement.getId());
        TenantScope.requireSubjectParameter(statement.getId(), parameter);
        if (InterceptorIgnoreHelper.willIgnoreTenantLine(statement.getId())) throw denied();
    }

    @Override public boolean willDoUpdate(Executor executor, MappedStatement statement, Object parameter) {
        requireStatement(statement, parameter);
        return true;
    }

    @Override public void beforeQuery(Executor executor, MappedStatement statement, Object parameter,
                                      RowBounds bounds, ResultHandler handler, BoundSql sql) {
        requireStatement(statement, parameter);
        PluginUtils.mpBoundSql(sql).sql(parserSingle(sql.getSql(), null));
    }

    @Override public void beforePrepare(StatementHandler handler, Connection connection, Integer timeout) {
        var statement = PluginUtils.mpStatementHandler(handler);
        requireStatement(statement.mappedStatement(), handler.getBoundSql().getParameterObject());
        var sql = statement.mPBoundSql();
        // 每次准备都重新核查，附加参数不能跳过企业条件处理。
        sql.sql(parserMulti(sql.sql(), null));
    }

    @Override public String parserSingle(String sql, Object context) {
        TenantScope.requireCurrent();
        return super.parserSingle(sql, context);
    }

    @Override public String parserMulti(String sql, Object context) {
        TenantScope.requireCurrent();
        return super.parserMulti(sql, context);
    }

    @Override protected String processParser(Statement statement, int index, String sql, Object context) {
        if (!(statement instanceof Select || statement instanceof Insert
                || statement instanceof Update || statement instanceof Delete)) throw denied();
        return super.processParser(statement, index, sql, context);
    }

    @Override protected void processInsert(Insert insert, int index, String sql, Object context) {
        if (insert.getColumns() == null || insert.getColumns().isEmpty()
                || insert.isModifierIgnore() || insert.isUseDuplicate()
                || insert.getWithItemsList() != null && !insert.getWithItemsList().isEmpty()) throw denied();
        if (insert.getValues() == null) {
            if (!(insert.getSelect() instanceof PlainSelect select) || select.getFromItem() == null) throw denied();
            if (select.getSelectItems().stream().anyMatch(item -> item.getExpression() instanceof AllColumns
                    || item.getExpression() instanceof AllTableColumns)) throw denied();
        }
        super.processInsert(insert, index, sql, context);
    }

    @Override protected void processUpdate(Update update, int index, String sql, Object context) {
        if (update.getUpdateSets().stream().flatMap(set -> set.getColumns().stream()).anyMatch(ProtectedTenantInterceptor::tenantColumn)) throw denied();
        if (update.getFromItem() != null || update.getSelect() != null || nonempty(update.getJoins())
                || nonempty(update.getStartJoins()) || nonempty(update.getWithItemsList()) || update.isModifierIgnore()) throw denied();
        super.processUpdate(update, index, sql, context);
    }

    @Override protected void processDelete(Delete delete, int index, String sql, Object context) {
        if (nonempty(delete.getTables()) || nonempty(delete.getUsingList())
                || nonempty(delete.getJoins()) || nonempty(delete.getWithItemsList()) || delete.isModifierIgnore()) throw denied();
        super.processDelete(delete, index, sql, context);
    }

    private static boolean nonempty(List<?> values) { return values != null && !values.isEmpty(); }
    private static boolean tenantColumn(Column column) { return "tenant_id".equalsIgnoreCase(column.getUnquotedColumnName()); }

    @Override public void setTenantLineHandler(TenantLineHandler handler) { throw denied(); }
    @Override public void setProperties(Properties properties) { if (!properties.isEmpty()) throw denied(); }

    private static final class ServerTenantHandler implements TenantLineHandler {
        @Override public Expression getTenantId() { return new StringValue(TenantScope.requireCurrent().tenantId()); }
        @Override public boolean ignoreTable(String table) { return false; }
        @Override public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
            if (columns.stream().anyMatch(ProtectedTenantInterceptor::tenantColumn)) throw denied();
            return false;
        }
    }
}
