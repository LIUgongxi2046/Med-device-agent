package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual new authority + deterministic expansion + shared TaskPlanPolicy.
 * Original079/083/088, candidate/template/profile readers are explicit current-authority doubles. */
class FormalEvaluationPlanPreparationAuthorityTest extends PlanPreparationFixture {
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final FormalEvaluationExecutionInputs inputs=mock(FormalEvaluationExecutionInputs.class);
    final FormalEvaluationExecutionInputs.Prepared source=mock(FormalEvaluationExecutionInputs.Prepared.class);
    final FormalEvaluationExecutionAdmission.Prepared root=mock(FormalEvaluationExecutionAdmission.Prepared.class);
    final FormalEvaluationBoundExecutions bounds=mock(FormalEvaluationBoundExecutions.class);
    final FormalEvaluationBoundExecutions.Bound bound=mock(FormalEvaluationBoundExecutions.Bound.class);
    final FormalEvaluationContextStore.Approved context=mock(FormalEvaluationContextStore.Approved.class);
    final FormalEvaluationExecutionScopes.Prepared snapshot=mock(FormalEvaluationExecutionScopes.Prepared.class);
    final FormalAgentCandidateAuthority candidates=mock(FormalAgentCandidateAuthority.class);
    final FormalAgentCandidateAuthority.VerifiedCandidateIdentity candidate=mock(FormalAgentCandidateAuthority.VerifiedCandidateIdentity.class);
    final TemplateVersionStore templates=mock(TemplateVersionStore.class);
    final TemplateApplicabilityService applicability=mock(TemplateApplicabilityService.class);
    final ProjectService projects=mock(ProjectService.class);
    final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final FormalEvaluationExactInputObjectAuthorization writes=mock(FormalEvaluationExactInputObjectAuthorization.class);
    final FormalEvaluationPlanPreparationDependencies dependencies=mock(FormalEvaluationPlanPreparationDependencies.class);
    final AgentExecutionPolicyProtocol.Definition policy=mock(AgentExecutionPolicyProtocol.Definition.class);
    final AgentExecutionPolicyProtocol.NodeBinding binding=mock(AgentExecutionPolicyProtocol.NodeBinding.class);
    final com.fasterxml.jackson.databind.node.ObjectNode manifest=json.object();
    final FormalEvaluationPlanPreparationAuthority authority;
    final ProjectProtocol.VerifiedProfile profile;
    final EvaluationProtocol.Definition definition;
    FormalEvaluationPlanPreparationAuthorityTest()throws Exception {
        when(access.execute(eq(subject),any())).thenAnswer(i->tx(()->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(admission)));
        when(inputs.prepare(subject,"1",checked.original().request().start())).thenReturn(source);when(source.requireCurrent(admission)).thenReturn(root);
        when(root.request()).thenReturn(checked.original().request().start());when(root.projectId()).thenReturn(project);when(root.classification()).thenReturn("RESTRICTED");when(root.canonical()).thenReturn("{}");
        when(root.policy()).thenReturn(policy);when(binding.nodeKind()).thenReturn("N01");when(policy.bindings()).thenReturn(List.of(binding));when(policy.businessPurpose()).thenReturn("EVALUATION_RESULTS");
        var n=checked.node();var c=n.cell();var repeated=new FormalEvaluationExecutionProtocol.Cell(c.evaluationId(),c.definitionSha256(),c.candidate(),c.policy(),c.sampleId(),c.sourceFamily(),c.split(),1,c.input(),c.expected(),c.runtimeManifestSha256());
        when(root.nodes()).thenReturn(List.of(n,new FormalEvaluationExecutionProtocol.Node(repeated,n.nodeKind(),n.role(),n.model(),n.tokenizer(),n.parametersSha256())));
        manifest.putArray("template_versions").add(json.tree(basis(30)));
        var sample=new EvaluationProtocol.Sample(id(6),id(7),"ACCEPTANCE","REGISTRATION_DIRECTORY","REAL",basis(20),basis(21),List.of());
        var create=new EvaluationProtocol.Create("1.0.0",project,basis(70),basis(71),List.of(sample),List.of(basis(10)),basis(72),List.of("accuracy"),new EvaluationProtocol.Variance(2,"fixture","fixture"),basis(73),"PRODUCTION","Explicit fixture");
        definition=new EvaluationProtocol.Definition("1.0.0",tenant,evaluation,create,List.of(new EvaluationProtocol.Candidate(id(10),hash('a'),id(74),actor,manifest,json.object(),"1.0.0",hash('b'))),List.of(),id(75),"1",actor,"2026-10-18T00:00:00Z");
        when(root.definition()).thenReturn(definition);
        when(bounds.require(admission,execution,source)).thenReturn(bound);when(bound.context()).thenReturn(context);when(context.binding()).thenReturn(basis(40));when(bound.scope()).thenReturn(snapshot);when(snapshot.profileRevision()).thenReturn(id(31));when(snapshot.snapshot()).thenReturn(basis(32));
        when(candidates.require(admission,basis(10),project,"EVALUATION_RESULTS")).thenReturn(candidate);when(candidate.executionPolicy()).thenReturn(basis(11));when(candidate.status()).thenReturn("DRAFT");when(candidate.manifestDefinition()).thenAnswer(i->manifest.deepCopy());when(candidate.authorizations()).thenReturn(json.object());
        var row=new ProjectProtocol.Row(tenant,project,"ACTIVE",BigInteger.ONE,"Fixture project",actor,id(31),id(70),hash('a'),"Fixture",id(76),BigInteger.ONE,"1.0.0",hash('b'),actor,now,now,BigInteger.ONE);
        profile=new ProjectProtocol.VerifiedProfile(row,hash('a'),List.of(),List.of());when(projects.fixedProfile(admission,project,id(31),"EVALUATION_RESULTS")).thenReturn(profile);
        when(source.document(admission,id(6))).thenReturn(json.object().put("document_type","REGISTRATION_DIRECTORY"));when(source.receipt(admission,id(6))).thenReturn(json.object().put("original_receipt_fixture",true));
        var grant=new PermissionProtocol.GrantRow(id(80),actor,project,"EVALUATION",evaluation,"evaluation.read","{}","ACTIVE",now,now.plusDays(1),id(81),"1.0.0",hash('b'),BigInteger.ONE);
        when(resources.require(any(),any(),anyString(),anyString())).thenReturn(grant);
        when(writes.require(admission,root,n)).thenReturn(json.object().put("explicit_write_authority_fixture",true));
        when(dependencies.require(eq(admission),eq(root),eq(n),any(),any())).thenReturn(checked.dependencies());
        choice(30,"UNKNOWN");
        authority=new FormalEvaluationPlanPreparationAuthority(access,inputs,bounds,candidates,templates,applicability,projects,resources,writes,
                new FormalEvaluationPlanExpansion(new TaskPlanPolicy(),json),dependencies,schema,json);
    }
    void choice(int id,String state)throws Exception {
        var t=FormalEvaluationPlanExpansionTest.template(id);when(templates.applicationTarget(admission,t.template_version(),"EVALUATION_RESULTS")).thenReturn(t);
        var condition=new TemplateApplicabilityProtocol.ConditionResult("NODE",PlanPreparationFixture.id(60),null,null,List.of(),state.equals("UNKNOWN")?"UNKNOWN":"APPLICABLE",state.equals("UNKNOWN")?List.of("fixture"):List.of());
        var view=new TemplateApplicabilityProtocol.TemplateView(t,state,List.of(condition),List.of(),hash('b'));
        var target=new TemplateApplicabilityService.Target(admission,"EVALUATION_RESULTS",profile,view,null,"{}");
        when(applicability.inspectTarget(admission,project,PlanPreparationFixture.id(31),"REGISTRATION_DIRECTORY",t.template_version(),"EVALUATION_RESULTS")).thenReturn(target);
        when(applicability.requireTarget(admission,project,PlanPreparationFixture.id(31),"REGISTRATION_DIRECTORY",t.template_version(),"EVALUATION_RESULTS")).thenReturn(target);
    }
    FormalEvaluationPlanPreparationSchema.Original request(PermissionProtocol.Basis template)throws Exception {var r=checked.original().request();return schema.request(schema.canonical(new FormalEvaluationPlanPreparationSchema.Request("1.0.0",r.executionId(),r.start(),r.sampleId(),r.repeatIndex(),r.nodeKind(),template)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    JsonNode preview(PermissionProtocol.Basis template)throws Exception{var p=authority.prepare(subject,"1",request(template),false);return tx(()->p.current(admission).preview());}
    @Test void genuineOriginalCellExpandsAllNodesAndDisclosesCompleteMatrix()throws Exception{
        var p=preview(null);assertEquals("SELECTED",p.required("selection_state").asText());assertEquals(2,p.required("plan_candidate").required("sections").size());
        assertEquals(2,p.required("coverage").required("expected_cells").intValue());assertEquals(2,p.required("coverage").required("expected_nodes").intValue());assertEquals(1,p.required("coverage").required("selected_cells").intValue());assertFalse(p.required("coverage").required("complete_evaluation").booleanValue());
        assertEquals(id(31),p.required("profile_revision").asText());assertFalse(p.required("runnable").booleanValue());assertFalse(p.required("professional_completion").booleanValue());
        verify(bounds,atLeastOnce()).require(admission,execution,source);verify(applicability,atLeastOnce()).requireTarget(admission,project,id(31),"REGISTRATION_DIRECTORY",id(30),"EVALUATION_RESULTS");
    }
    @Test void ambiguousCompatibleTemplatesRequireNormalSelectionAndNeverFirstMatch()throws Exception{
        manifest.withArray("template_versions").add(json.tree(basis(33)));choice(33,"APPLICABLE");var p=preview(null);
        assertEquals("CHOICE_REQUIRED",p.required("selection_state").asText());assertTrue(p.required("plan_candidate").isNull());
        assertEquals(ErrorCode.PLAN_UNCONFIRMED,assertThrows(TenantAccessException.class,()->authority.prepare(subject,"1",request(null),true)).code());
        assertEquals(id(33),preview(basis(33)).required("selected_template").required("version_id").asText());
    }
    @Test void unauthorizedTemplateReadIsNotSilentlyMarkedInapplicableOrLeaked()throws Exception{
        when(templates.applicationTarget(admission,id(30),"EVALUATION_RESULTS")).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->preview(null)).code());verify(applicability,never()).inspectTarget(any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void nonManifestChoiceWrongHashAndProfileDriftFailClosed()throws Exception{
        assertThrows(TenantAccessException.class,()->preview(basis(999)));assertThrows(TenantAccessException.class,()->preview(new PermissionProtocol.Basis(id(30),hash('f'))));
        when(projects.fixedProfile(admission,project,id(31),"EVALUATION_RESULTS")).thenThrow(new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH));
        assertEquals(ErrorCode.RESULT_VERSION_MISMATCH,assertThrows(TenantAccessException.class,()->preview(null)).code());
    }
    @Test void unsupportedPolicyNodeSetStopsBeforeAnyTemplateOrWriteWork()throws Exception{
        when(binding.nodeKind()).thenReturn("N02");assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,()->preview(null)).code());
        verifyNoInteractions(templates,applicability,writes,dependencies);
    }
    @Test void missingRegistrationGrantStillAllowsReadPreviewButNeverPreparation()throws Exception{
        when(writes.require(admission,root,checked.node())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertFalse(preview(null).required("action_summary").get(0).required("allowed").booleanValue());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->authority.prepare(subject,"1",request(null),true)).code());
    }
    @Test void notApplicableChoiceRetainsRealResultAndCannotBeSelected()throws Exception{
        choice(30,"NOT_APPLICABLE");var p=preview(null);assertEquals("NO_COMPATIBLE_TEMPLATE",p.required("selection_state").asText());
        assertEquals("NOT_APPLICABLE",p.required("templates").get(0).required("applicability").asText());assertThrows(TenantAccessException.class,()->preview(basis(30)));
    }
}
