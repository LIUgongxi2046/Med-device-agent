package com.ruoyi.medical.persistence;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.StrictComparisonPolicyDefinition.*;

/** Pure contract tests only: these do not create approvals, source evidence, or runtime data. */
class StrictComparisonLineageTest {
    private final StrictComparisonCodec codec=new StrictComparisonCodec(100000);
    private static String id(int n){return "10000000-0000-4000-8000-"+String.format("%012d",n);}
    private static Version version(int n){return new Version(id(n),"a".repeat(64));}
    private static StrictComparisonProtocol.Scope scope(){return new StrictComparisonProtocol.Scope(id(1),id(2),id(3),id(4),id(5));}
    @Test void policyParentsRetainRealScopeBasisAndRuleButNotImplementationOrTaskResource()throws Exception {
        var global=new Context(ContextMode.GLOBAL,List.of());
        var definition=new Definition("1.0.0",Operator.TEXT_EXACT,new Precision(PrecisionMode.NOT_APPLICABLE,null),new Unit(UnitMode.EXACT_DECLARED,null),
            new ApplicabilityScope(Applicability.APPLICABLE,version(6),version(5)),CoverageMode.EXACT_DECLARED_COMBINATIONS,
            List.of(new Combination("field",global,global)),version(9),List.of(version(7),version(4)));
        var request=codec.tree(new StrictComparisonProtocol.Author("1.0.0",scope(),definition,"explicit basis"));
        assertEquals(Set.of(id(2),id(3),id(4),id(5),id(6),id(7)),StrictComparisonStore.parents(codec,StrictComparisonStore.Kind.POLICY,null,request,request));
        assertThrows(TenantAccessException.class,()->StrictComparisonStore.parents(codec,StrictComparisonStore.Kind.POLICY,id(8),request,request));
    }
    @Test void decisionsLinkPolicyAndExactPreviousDecisionWithoutInventingApproval()throws Exception {
        var request=codec.tree(Map.of("policy_revision",id(1)));
        var state=codec.tree(Map.of("revision","1","status","DRAFT"));((com.fasterxml.jackson.databind.node.ObjectNode)state).putNull("decision_id");
        var body=codec.tree(Map.of("policy_revision",id(1),"baseline",Map.of("state",state)));
        assertEquals(Set.of(id(1)),StrictComparisonStore.parents(codec,StrictComparisonStore.Kind.DECISION,id(1),request,body));
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.required("baseline").required("state")).put("decision_id",id(2));
        assertEquals(Set.of(id(1),id(2)),StrictComparisonStore.parents(codec,StrictComparisonStore.Kind.DECISION,id(1),request,body));
        assertThrows(TenantAccessException.class,()->StrictComparisonStore.parents(codec,StrictComparisonStore.Kind.DECISION,id(3),request,body));
    }
    @Test void decimalParentsRetainSourceParseApprovalRightsAndScopeNotParserIdentity()throws Exception {
        var request=codec.read("""
            {"schema_version":"1.0.0","scope":%s,
             "evidence":{"schema_version":"1.0.0","tenant_id":"%s","project_id":"%s","source_id":"%s","source_version":"%s","parse_version":"%s","chunk_id":"chunk","locator_kind":"TXT","locator":{"kind":"TXT","encoding":"UTF-8","newline_policy":"PRESERVE","char_start":0,"char_end":1,"line_start":1,"line_end":1},"quote_hash":"%s","approval_revision":"%s"},
             "rights_revision":"%s","purpose":"STRICT_FIELD_COMPARISON","numeric_span":{"start_codepoint":0,"end_codepoint":1},"unit":{"basis":"UNITLESS","span":null},"parser":{"revision":"%s","sha256":"%s"},"reason":"source parsing"}
            """.formatted(codec.canonical(scope()),id(20),id(21),id(22),id(6),id(7),"a".repeat(64),id(8),id(9),id(10),"b".repeat(64)));
        var body=codec.tree(Map.of("request",request));
        assertEquals(Set.of(id(2),id(3),id(4),id(5),id(6),id(7),id(8),id(9)),StrictComparisonStore.parents(codec,StrictComparisonStore.Kind.DECIMAL,null,request,body));
    }
    @Test void typedOwnerMappingsReadActualTablesAndNeverAliasSource()throws Exception {
        var configuration=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new org.springframework.jdbc.datasource.DriverManagerDataSource()).getConfiguration();
        var statement=configuration.getMappedStatement("com.ruoyi.medical.persistence.mapper.VersionResourceMapper.owners");
        var tables=Map.of("STRICT_COMPARISON_POLICY","strict_comparison_policy_revision","STRICT_COMPARISON_DECISION","strict_comparison_policy_decision",
            "STRICT_DECIMAL_EVIDENCE","strict_decimal_evidence_revision","STRICT_FIELD_CONTEXT","task_strict_field_context_revision",
            "STRICT_FIELD_CONTEXT_DECISION","task_strict_field_context_decision","STRICT_FIELD_TYPED_TARGET","task_strict_field_typed_revision",
            "STRICT_FIELD_TYPED_TARGET_DECISION","task_strict_field_typed_decision","STRICT_FIELD_RULE_SET","task_strict_field_rule_set");
        for(var entry:tables.entrySet()) {
            var sql=statement.getBoundSql(Map.of("id",id(1),"domain",entry.getKey())).getSql().replaceAll("\\s+"," ");
            assertTrue(sql.contains("FROM "+entry.getValue()+" v"));assertTrue(sql.contains("'PROJECT' AS resource_kind"));
            assertTrue(sql.contains("v.project_id AS resource_id"));assertTrue(sql.contains("NULL AS source_version"));
            assertFalse(sql.contains("'SOURCE' AS resource_kind"));assertTrue(sql.contains("v.content_sha256"));
        }
    }
    @Test void typedOwnersRequireExactCustomReadActionsRatherThanProjectRead()throws Exception {
        var method=VersionResourceAuthority.class.getDeclaredMethod("action",VersionResourceProtocol.Domain.class,String.class);method.setAccessible(true);
        for(String domain:List.of("STRICT_COMPARISON_POLICY","STRICT_COMPARISON_DECISION"))assertEquals("strict_comparison_policy.read",method.invoke(null,VersionResourceProtocol.Domain.valueOf(domain),"PROJECT"));
        assertEquals("task.strict_field_rule_set.read",method.invoke(null,VersionResourceProtocol.Domain.STRICT_FIELD_RULE_SET,"PROJECT"));
        assertEquals("strict_decimal_evidence.read",method.invoke(null,VersionResourceProtocol.Domain.STRICT_DECIMAL_EVIDENCE,"PROJECT"));
        for(String domain:List.of("STRICT_FIELD_CONTEXT","STRICT_FIELD_CONTEXT_DECISION"))assertEquals("task.strict_field_context.read",method.invoke(null,VersionResourceProtocol.Domain.valueOf(domain),"PROJECT"));
        for(String domain:List.of("STRICT_FIELD_TYPED_TARGET","STRICT_FIELD_TYPED_TARGET_DECISION"))assertEquals("task.strict_field_typed.read",method.invoke(null,VersionResourceProtocol.Domain.valueOf(domain),"PROJECT"));
    }
}
