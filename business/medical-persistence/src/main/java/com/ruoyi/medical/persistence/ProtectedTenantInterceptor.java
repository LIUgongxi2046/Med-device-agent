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
        "framework_form_schema", "framework_form_approval", "framework_form_operation",
        "knowledge_feedback_event", "knowledge_feedback_origin", "rule_business_definition", "customer_connector_business_profile",
        "platform_framework_job_revision",
        "framework_binding_manifest", "framework_binding_approval", "framework_binding_operation",
        "source_copy_authority",
        "operations_feedback_event",
        "customer_connector_decision",
        "approved_web_query_decision",
        "gru_execution_presentation_context",
        "task_run_assembly_success_reuse",
        "gru_execution_message",
        "task_run_success_reuse",
        "dense_index_configuration", "dense_operation_source",
        "export_layout_profile", "export_layout_verification",
        "formal_evaluation_deterministic_execution", "formal_evaluation_deterministic_object_attempt", "formal_evaluation_deterministic_object_intent", "formal_evaluation_deterministic_object_registered", "formal_evaluation_deterministic_outcome",
        "change_impact_application", "change_impact_decision", "change_impact_operation", "change_impact_resolution", "change_proposal_scope", "change_revision_entry", "document_review_confirmation_receipt", "document_review_resubmission", "document_review_revision_assignment", "document_signed_content", "document_signing_callback", "document_signing_challenge_binding", "document_signing_operation", "document_signing_policy", "document_signing_policy_approval", "evaluation_result", "evaluation_score", "evaluation_score_observation", "formal_evaluation_call_exact_attempt", "formal_evaluation_call_exact_intent", "formal_evaluation_call_exact_registration", "formal_evaluation_cancel_intent", "formal_evaluation_case_approval", "formal_evaluation_case_original", "formal_evaluation_cell_outcome", "formal_evaluation_context_read", "formal_evaluation_host_launch_attempt", "formal_evaluation_invocation_context", "formal_evaluation_job", "formal_evaluation_launch_attempt", "formal_evaluation_launch_intent", "formal_evaluation_launch_receipt", "formal_evaluation_node_dispatch", "formal_evaluation_node_outcome", "formal_evaluation_node_waiting", "formal_evaluation_professional_completion", "formal_evaluation_runtime_object_attempt", "formal_evaluation_runtime_object_intent", "formal_evaluation_runtime_object_registered", "formal_evaluation_runtime_prepare", "formal_evaluation_stage_input_attempt", "formal_evaluation_stage_input_intent", "formal_evaluation_stage_input_registered", "formal_evaluation_tool_question", "formal_evaluation_tool_receipt", "impact_manifest", "review_batch", "review_batch_item", "review_decision", "scenario_record", "signature_record", "task_input_continuation", "task_run_continuation", "task_run_preruntime_termination",
        "candidate_comparison_intent", "candidate_comparison_completion",
        "strict_field_human_decision", "strict_field_evidence_question",
        "human_evidence_batch", "human_evidence_decision",
        "document_adoption_preview", "document_adoption_intent", "document_adoption_completion",
        "template_application_decision", "professional_issue_decision", "professional_issue_occurrence",
        "document_review_intent", "document_review_object_intent", "document_review_object_attempt", "document_review_object", "document_review_result", "document_review_execution", "document_review_launch_intent", "document_review_launch_attempt", "document_review_launch_receipt", "document_review_completion", "document_review_runtime_prepare", "document_review_exact_intent", "document_review_exact_attempt", "document_review_exact_registration", "document_review_context_read", "document_review_independent_read",
        "task_plan_draft_job", "task_plan_draft_object_intent", "task_plan_draft_object_attempt", "task_plan_draft_candidate", "task_plan_draft_check", "task_plan_draft_tool_receipt", "task_plan_draft_origin", "task_plan_draft_runtime_prepare", "task_plan_draft_launch_intent", "task_plan_draft_launch_attempt", "task_plan_draft_launch_receipt", "task_plan_draft_completion", "task_plan_draft_exact_intent", "task_plan_draft_exact_attempt", "task_plan_draft_exact_registration",
        "task_strict_field_input", "task_strict_field_source_read", "task_strict_field_result", "task_strict_field_context_revision", "task_strict_field_context_decision", "task_strict_field_typed_revision", "task_strict_field_typed_decision", "task_strict_field_typed_intent", "task_strict_field_typed_registration",
        "task_section_writer_producer_rule", "task_section_writer_producer_decision",
        "task_strict_field_rule_set", "task_strict_field_output_intent", "task_strict_field_output_registration", "task_strict_field_method_read",
        "task_section_writer_object_intent", "task_section_writer_object_attempt", "task_section_writer_object", "task_section_writer_input", "task_section_writer_submission", "task_section_writer_exact_intent", "task_section_writer_exact_attempt", "task_section_writer_exact_registration", "task_section_writer_runtime_prepare", "document_initial_intent", "document_initial_object", "document_initial_registration",
        "task_candidate_assembly_object_intent", "task_candidate_assembly_object_attempt", "task_candidate_assembly_object", "task_candidate_assembly_producer_rule", "task_candidate_assembly_producer_decision", "task_candidate_assembly_rule_set",
        "task_source_verifier_object_intent", "task_source_verifier_object_attempt", "task_source_verifier_object", "task_source_verifier_input", "task_source_verifier_producer_rule", "task_source_verifier_producer_decision", "task_source_verifier_rule_set", "task_source_verifier_submission", "task_source_verifier_issue_proposal", "task_source_verifier_runtime_prepare", "task_source_verifier_context_read", "task_source_verifier_independent_read", "task_source_verifier_exact_intent", "task_source_verifier_exact_attempt", "task_source_verifier_exact_registration",
        "strict_comparison_policy_revision", "strict_comparison_policy_decision", "strict_decimal_evidence_revision",
        "task_package_authoring_intent", "task_package_authoring_registration", "task_package_authoring_rejection",
        "task_field_mapping_revision", "task_field_source_read_receipt", "task_field_target_intent", "task_field_target_entry", "task_field_target_attempt", "task_field_target_registration", "task_field_target_input_capability", "task_field_target_input_capability_decision",
        "formal_evaluation_node_input_intent", "formal_evaluation_node_input_attempt", "formal_evaluation_node_input_registered",
        "formal_evaluation_exact_input_intent", "formal_evaluation_exact_input_attempt", "formal_evaluation_exact_input_registered",
        "formal_evaluation_plan_preparation_intent", "formal_evaluation_plan_preparation_attempt", "formal_evaluation_plan_preparation_registered",
        "annotation_decision", "simulation_fixture_input_artifact", "simulation_input_artifact_operation",
        "simulation_rule_binding", "simulation_rule_approval_binding", "simulation_evaluation_preparation",
        "gru_conversation_context", "gru_message_record",
        "document_revision_return", "document_revision_return_assignment",
        "document_technical_check_manifest", "document_technical_check",
        "document_comparison_intent", "document_comparison_registration", "document_comparison_attempt",
        "gru_record_export_intent", "gru_record_export_attempt", "gru_record_export",
        "document_technical_check_intent", "document_technical_check_attempt",
        "agent_publication_record",
        "task_run_replay_intent", "task_run_replay_attempt", "task_run_replay_registration", "task_run_workflow_launch", "task_run_workflow_attempt", "task_run_workflow_acceptance", "task_delegation_rule", "task_delegation_decision", "task_run_replay_governance_manifest", "task_run_replay_governance_member", "task_run_replay_governance_edge", "evaluation_acceptance_policy_decision",
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
