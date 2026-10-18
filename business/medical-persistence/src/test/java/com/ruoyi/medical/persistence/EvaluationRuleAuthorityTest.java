package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Contract and actual SQL-boundary regressions; these do not create a real approval or user. */
class EvaluationRuleAuthorityTest {
    private final PermissionSchema json=new PermissionSchema();
    private final RuleVersionSchema schema=new RuleVersionSchema(json,new TemplateApplicabilitySchema(json),new RuleExpression());
    private final EvaluationRuleAuthority authority=new EvaluationRuleAuthority(null,null,schema,json,new IdentityDirectoryPolicy("TEST"),null);
    EvaluationRuleAuthorityTest()throws Exception{}
    private RuleVersionProtocol.StorageRow row(String status)throws Exception {
        var basis=EvaluationFixture.ref(100);
        var expression=new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,"true",List.of(),List.of());
        var content=new TemplateApplicabilityProtocol.RuleContent("1.0.0",RuleExpression.ENGINE,expression,List.of(basis));
        var validated=schema.version(new RuleVersionProtocol.VersionInput("1.0.0",null,RuleVersionSchema.CONTENT_REF,content,"Synthetic rule boundary fixture"));
        return new RuleVersionProtocol.StorageRow(EvaluationFixture.id(1),EvaluationFixture.id(2),RuleExpression.ENGINE,json.canonicalObject(expression),json.canonicalObject(List.of(basis)),
            status,validated.content_sha256(),validated.content_sha256(),"1.0.0",schema.contentManifestSha256(),EvaluationFixture.id(3),LocalDateTime.parse("2026-10-16T00:00:00"),"TEST",
            validated.canonical_content(),null,null,"Synthetic rule boundary fixture",EvaluationFixture.id(4),BigInteger.ONE,BigInteger.TWO);
    }
    private PermissionProtocol.Basis ref(RuleVersionProtocol.StorageRow row){return new PermissionProtocol.Basis(row.ruleVersion(),row.contentHash());}
    @Test void rejectsAnyReferenceWithoutActualRuleDefinitionEvenIfItIsReadableInAnotherDomain()throws Exception {
        for(String wrongDomain:List.of("ARTIFACT","SOURCE_VERSION","AGENT_VERSION"))
            assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->authority.validate(EvaluationFixture.id(1),EvaluationFixture.ref(2),List.of(),true,List.of())).code(),wrongDomain);
        var config=new MedicalPersistenceConfiguration().medicalSqlSessionFactory(new DriverManagerDataSource()).getConfiguration();
        String sql=config.getMappedStatement("com.ruoyi.medical.persistence.mapper.RuleVersionMapper.definition").getBoundSql(java.util.Map.of()).getSql();
        assertTrue(sql.contains("FROM rule_version r JOIN rule_definition"));assertTrue(sql.contains("v.entity_kind='RULE_VERSION'"));assertTrue(sql.contains("FOR UPDATE"));
    }
    @Test void draftReturnedOrDisabledRulesCannotAuthorizeNewEvaluationWrites()throws Exception {
        for(String status:List.of("DRAFT","RETURNED","DISABLED")){var row=row(status);
            assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->authority.validate(row.tenantId(),ref(row),List.of(row),true,List.of(row.ruleVersion()))).code(),status);}
    }
    @Test void approvedLabelWithoutCurrentApprovalLedgerCannotAuthorizeWrites()throws Exception {
        var row=row("APPROVED");assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,()->authority.validate(row.tenantId(),ref(row),List.of(row),true,List.of())).code());
        assertDoesNotThrow(()->authority.validate(row.tenantId(),ref(row),List.of(row),true,List.of(row.ruleVersion())));
    }
    @Test void historicalReadPreservesOriginalDefinitionAfterApprovalIsReturnedOrDisabled()throws Exception {
        for(String status:List.of("APPROVED","RETURNED","DISABLED")){var row=row(status);assertDoesNotThrow(()->authority.validate(row.tenantId(),ref(row),List.of(row),false,List.of()));}
    }
    @Test void schemaHashAndTenantMismatchesCannotBeReinterpretedAsRules()throws Exception {
        var row=row("APPROVED");assertThrows(TenantAccessException.class,()->authority.validate(EvaluationFixture.id(9),ref(row),List.of(row),true,List.of(row.ruleVersion())));
        assertThrows(TenantAccessException.class,()->authority.validate(row.tenantId(),EvaluationFixture.ref(2),List.of(row),true,List.of(row.ruleVersion())));
        var bad=new RuleVersionProtocol.StorageRow(row.tenantId(),row.ruleVersion(),row.engineRevision(),row.expression(),row.basisManifest(),row.status(),row.contentHash(),row.registryHash(),row.schemaVersion(),"f".repeat(64),row.createdBy(),row.createdAt(),row.classification(),row.contentJson(),row.baseVersion(),row.baseHash(),row.reason(),row.authorityGrant(),row.authorityRevision(),row.revision());
        assertThrows(TenantAccessException.class,()->authority.validate(row.tenantId(),ref(row),List.of(bad),true,List.of(row.ruleVersion())));
    }
}
