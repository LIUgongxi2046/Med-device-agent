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
    private static final java.util.Set<String> IMMUTABLE_TABLES = java.util.Set.of(
        "annotation_decision", "simulation_fixture_input_artifact", "simulation_input_artifact_operation",
        "simulation_rule_binding", "simulation_rule_approval_binding", "simulation_evaluation_preparation",
        "gru_conversation_context", "gru_message_record",
        "document_revision_return", "document_revision_return_assignment",
        "document_technical_check_manifest", "document_technical_check",
        "document_comparison_intent", "document_comparison_registration", "document_comparison_attempt",
        "gru_record_export_intent", "gru_record_export_attempt", "gru_record_export",
        "document_technical_check_intent", "document_technical_check_attempt",
        "agent_publication_record", "evaluation_acceptance_policy_decision",
        "runtime_retention_policy", "runtime_retention_retirement", "runtime_retention_cancellation",
        "agent_execution_policy_version", "agent_execution_policy_component", "agent_execution_policy_decision",
        "formal_evaluation_input_registration", "model_verification_input_preparation", "model_enablement_policy_preparation", "formal_capability_cancellation",
        "run_runtime_intent", "step_runtime_attempt", "run_runtime_receipt", "runtime_object_intent", "runtime_object_attempt", "runtime_node_definition", "runtime_governance_manifest", "runtime_governance_member", "runtime_governance_edge", "runtime_registered_object", "formal_model_manifest", "formal_model_verification_job", "formal_invocation_binding", "formal_invocation_attempt", "formal_invocation_object_intent", "formal_invocation_object_attempt", "formal_invocation_registered_object", "formal_invocation_accounting", "formal_invocation_cancellation", "formal_invocation_uncertainty", "runtime_connection_intent", "runtime_connection_start", "runtime_connection_completed", "model_eligibility", "expert_qualification_record", "expert_qualification", "expert_score_binding", "expert_score_authority_witness", "formal_agent_candidate_binding", "formal_evaluation_policy_input", "formal_evaluation_execution", "formal_evaluation_cell", "formal_evaluation_node", "professional_runtime_observation", "formal_evaluation_node_result", "formal_evaluation_context_version", "formal_evaluation_context_decision", "formal_evaluation_execution_context", "formal_agent_acceptance", "formal_agent_publication_intent", "formal_agent_publication_attempt", "formal_agent_publication_registration", "formal_agent_publication_disable");
    private static boolean immutableTable(String name) {
        return IMMUTABLE_TABLES.contains(name.replace("`", "").toLowerCase(java.util.Locale.ROOT));
    }

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
        if (!(insert.getSelect() instanceof net.sf.jsqlparser.statement.select.Values)) {
            if (!(insert.getSelect() instanceof PlainSelect select) || select.getFromItem() == null) throw denied();
            if (select.getSelectItems().stream().anyMatch(item -> item.getExpression() instanceof AllColumns
                    || item.getExpression() instanceof AllTableColumns)) throw denied();
        }
        super.processInsert(insert, index, sql, context);
    }

    @Override protected void processInsertSelect(Select select, String context) {
        if (!(select instanceof PlainSelect plain) || plain.getFromItem() == null) throw denied();
        // The generic INSERT traversal misses derived UNION branches and appends an
        // ambiguous source tenant column. Reuse the full SELECT tenant traversal,
        // then bind the inserted tenant exclusively to verified server identity.
        processSelectBody(plain, context);
        plain.addSelectItems(new net.sf.jsqlparser.statement.select.SelectItem<>(getTenantLineHandler().getTenantId()));
    }

    @Override protected void processUpdate(Update update, int index, String sql, Object context) {
        if (immutableTable(update.getTable().getName())) throw denied();
        if (update.getUpdateSets().stream().flatMap(set -> set.getColumns().stream()).anyMatch(ProtectedTenantInterceptor::tenantColumn)) throw denied();
        if (update.getFromItem() != null || update.getSelect() != null || nonempty(update.getJoins())
                || nonempty(update.getStartJoins()) || nonempty(update.getWithItemsList()) || update.isModifierIgnore()) throw denied();
        super.processUpdate(update, index, sql, context);
    }

    @Override protected void processDelete(Delete delete, int index, String sql, Object context) {
        if (immutableTable(delete.getTable().getName())) throw denied();
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
